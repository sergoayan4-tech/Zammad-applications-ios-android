import Foundation

/// How the user authenticates against the Zammad server.
enum AuthMode: String, CaseIterable, Identifiable {
    case token
    case password

    var id: String { rawValue }
}

/// Credentials used for every API call.
enum Credentials: Sendable, Equatable {
    /// Personal access token → `Authorization: Token token=...`
    case token(String)
    /// Login/password → `Authorization: Basic ...`
    case login(username: String, password: String)

    var authorizationHeaderValue: String? {
        switch self {
        case .token(let token):
            let trimmed = token.trimmed
            return trimmed.isEmpty ? nil : "Token token=\(trimmed)"
        case .login(let username, let password):
            let raw = "\(username):\(password)"
            return "Basic " + Data(raw.utf8).base64EncodedString()
        }
    }
}
