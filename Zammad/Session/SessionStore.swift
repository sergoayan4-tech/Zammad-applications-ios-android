import Foundation
import Observation

/// Holds the connection state of the app: server, credentials, current user.
@MainActor
@Observable
final class SessionStore {
    enum Phase: Equatable {
        case restoring
        case signedOut
        case connecting
        case connected
    }

    private enum Keys {
        static let server = "session.server"
        static let mode = "session.authMode"
        static let username = "session.username"
        static let secret = "session.secret"
    }

    private(set) var phase: Phase = .restoring
    private(set) var api: APIClient?
    private(set) var currentUser: ZammadUser?
    private(set) var serverURL: URL?
    private(set) var authMode: AuthMode?
    var connectError: Error?

    let reference = ReferenceData()

    private var restored = false

    var serverDisplay: String {
        serverURL?.absoluteString ?? "—"
    }

    var isAuthenticatedAsToken: Bool {
        authMode == .token
    }

    // MARK: - Values used to prefill the connect screen

    var savedServerString: String {
        UserDefaults.standard.string(forKey: Keys.server) ?? ""
    }

    var savedMode: AuthMode {
        AuthMode(rawValue: UserDefaults.standard.string(forKey: Keys.mode) ?? "") ?? .token
    }

    var savedUsername: String {
        UserDefaults.standard.string(forKey: Keys.username) ?? ""
    }

    // MARK: - Lifecycle

    /// Silently restores the previously saved connection at launch.
    func restore() async {
        guard !restored else { return }
        restored = true

        guard let serverString = UserDefaults.standard.string(forKey: Keys.server),
              let modeValue = UserDefaults.standard.string(forKey: Keys.mode),
              let mode = AuthMode(rawValue: modeValue),
              let url = sanitizedServerURL(serverString) else {
            phase = .signedOut
            return
        }

        let credentials: Credentials
        switch mode {
        case .token:
            guard let token = KeychainStore.read(account: Keys.secret), !token.isEmpty else {
                phase = .signedOut
                return
            }
            credentials = .token(token)
        case .password:
            guard let username = UserDefaults.standard.string(forKey: Keys.username),
                  let password = KeychainStore.read(account: Keys.secret),
                  !password.isEmpty else {
                phase = .signedOut
                return
            }
            credentials = .login(username: username, password: password)
        }

        phase = .connecting
        let connected = await establish(url: url, mode: mode, credentials: credentials)
        if !connected {
            phase = .signedOut
        }
    }

    /// Connects using the values entered on the connect screen.
    func connect(url: URL, mode: AuthMode, token: String, username: String, password: String) async {
        connectError = nil
        phase = .connecting

        let credentials: Credentials = mode == .token
            ? .token(token)
            : .login(username: username, password: password)

        let connected = await establish(url: url, mode: mode, credentials: credentials)
        if connected {
            save(url: url, mode: mode, username: username, secret: mode == .token ? token : password)
        } else {
            phase = .signedOut
        }
    }

    /// Clears everything and returns to the connect screen.
    func disconnect() {
        api = nil
        currentUser = nil
        serverURL = nil
        authMode = nil
        connectError = nil
        reference.reset()

        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: Keys.server)
        defaults.removeObject(forKey: Keys.mode)
        defaults.removeObject(forKey: Keys.username)
        KeychainStore.delete(account: Keys.secret)

        phase = .signedOut
    }

    // MARK: - Private

    private func establish(url: URL, mode: AuthMode, credentials: Credentials) async -> Bool {
        var client = APIClient(baseURL: url, credentials: credentials)

        // Prefer an API token when the server supports password sign in.
        if mode == .password,
           case .login(let username, let password) = credentials,
           let exchanged = await client.signInForToken(username: username, password: password) {
            client = APIClient(baseURL: url, credentials: .token(exchanged))
        }

        do {
            let me: ZammadUser = try await client.get("/api/v1/users/me")
            api = client
            currentUser = me
            serverURL = url
            authMode = mode
            connectError = nil
            phase = .connected
            await reference.load(api: client)
            return true
        } catch {
            api = nil
            currentUser = nil
            serverURL = nil
            authMode = nil
            connectError = error
            return false
        }
    }

    private func save(url: URL, mode: AuthMode, username: String, secret: String) {
        let defaults = UserDefaults.standard
        defaults.set(url.absoluteString, forKey: Keys.server)
        defaults.set(mode.rawValue, forKey: Keys.mode)
        defaults.set(username, forKey: Keys.username)
        KeychainStore.save(secret, account: Keys.secret)
    }
}
