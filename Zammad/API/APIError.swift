import Foundation

/// Errors produced by `APIClient`.
struct APIError: Error, LocalizedError {
    enum Kind {
        case network
        case unauthorized
        case forbidden
        case notFound
        case client
        case server
        case decoding
        case invalidURL
    }

    let kind: Kind
    let status: Int?
    let serverMessage: String?

    init(kind: Kind, status: Int? = nil, serverMessage: String? = nil) {
        self.kind = kind
        self.status = status
        self.serverMessage = serverMessage
    }

    /// English description, used by `LocalizedError`.
    var errorDescription: String? {
        localizedMessage(code: "en")
    }

    /// Localized description. Known error kinds get a translated message,
    /// everything else falls back to what the server returned.
    func localizedMessage(code: String) -> String {
        let key: String
        switch kind {
        case .network: key = "error.network"
        case .unauthorized: key = "error.unauthorized"
        case .forbidden: key = "error.forbidden"
        case .notFound: key = "error.notFound"
        case .server: key = "error.server"
        case .decoding: key = "error.decode"
        case .invalidURL: key = "error.invalidURL"
        case .client: key = "error.request"
        }

        let localized = L10n.translate(key, code: code)

        // Client errors usually carry a useful message from Zammad itself.
        if kind == .client, let serverMessage, !serverMessage.isEmpty {
            return serverMessage
        }
        if let serverMessage, !serverMessage.isEmpty, kind == .server {
            return "\(localized) (\(serverMessage))"
        }
        return localized
    }
}
