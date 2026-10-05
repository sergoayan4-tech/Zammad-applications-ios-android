import Foundation

/// Shared URL session (no disk cache, short timeouts).
enum APIClientSession {
    static let shared: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 120
        configuration.waitsForConnectivity = false
        return URLSession(configuration: configuration)
    }()
}

/// Formats / parses the date format used by Zammad: `2025-01-22T10:46:58.251Z`.
enum ZammadDate {
    private static let fractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    static func parse(_ raw: String) -> Date? {
        let value = raw.trimmed
        guard !value.isEmpty else { return nil }
        return fractional.date(from: value) ?? plain.date(from: value)
    }

    static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let raw = (try? container.decode(String.self)) ?? ""
            return ZammadDate.parse(raw) ?? .distantPast
        }
        return decoder
    }
}

/// A thin, typed wrapper around the Zammad REST API.
struct APIClient: Sendable {
    let baseURL: URL
    let credentials: Credentials

    private static let decoder = ZammadDate.makeDecoder()

    // MARK: - Public surface

    func get<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        try await send(path, query: query, method: "GET", body: nil)
    }

    func post<T: Decodable>(_ path: String, body: [String: Any]) async throws -> T {
        try await send(path, query: [], method: "POST", body: body)
    }

    func put<T: Decodable>(_ path: String, body: [String: Any]) async throws -> T {
        try await send(path, query: [], method: "PUT", body: body)
    }

    /// Downloads raw bytes (used for ticket attachments).
    func rawData(_ path: String, query: [URLQueryItem] = []) async throws -> Data {
        let request = try makeRequest(path, query: query, method: "GET", body: nil)
        let (data, response) = try await perform(request)
        try validate(data: data, response: response)
        return data
    }

    /// Tries `POST /api/v1/signin` to exchange a login/password for an API token.
    /// Returns `nil` when the server does not allow it (HTTP Basic is used then).
    func signInForToken(username: String, password: String) async -> String? {
        guard let url = buildURL("/api/v1/signin", query: []) else { return nil }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try? JSONSerialization.data(
            withJSONObject: ["username": username, "password": password]
        )

        guard let (data, response) = try? await APIClientSession.shared.data(for: request),
              let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            return nil
        }
        return Self.tokenValue(from: data)
    }

    // MARK: - Internals

    private func send<T: Decodable>(
        _ path: String,
        query: [URLQueryItem],
        method: String,
        body: [String: Any]?
    ) async throws -> T {
        let request = try makeRequest(path, query: query, method: method, body: body)
        let (data, response) = try await perform(request)
        try validate(data: data, response: response)

        do {
            return try Self.decoder.decode(T.self, from: data)
        } catch {
            throw APIError(kind: .decoding, status: Self.status(of: response), serverMessage: "\(error)")
        }
    }

    private func makeRequest(
        _ path: String,
        query: [URLQueryItem],
        method: String,
        body: [String: Any]?
    ) throws -> URLRequest {
        guard let url = buildURL(path, query: query) else {
            throw APIError(kind: .invalidURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 30
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if let authorization = credentials.authorizationHeaderValue {
            request.setValue(authorization, forHTTPHeaderField: "Authorization")
        }

        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        return request
    }

    private func buildURL(_ path: String, query: [URLQueryItem]) -> URL? {
        var base = baseURL.absoluteString
        while base.hasSuffix("/") {
            base.removeLast()
        }
        var cleanPath = path
        while cleanPath.hasPrefix("/") {
            cleanPath.removeFirst()
        }
        guard let url = URL(string: base + "/" + cleanPath) else { return nil }
        guard !query.isEmpty,
              var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return url
        }
        components.queryItems = (components.queryItems ?? []) + query
        return components.url
    }

    private func perform(_ request: URLRequest) async throws -> (Data, URLResponse) {
        do {
            return try await APIClientSession.shared.data(for: request)
        } catch let error as URLError {
            throw APIError(kind: .network, serverMessage: error.localizedDescription)
        } catch {
            throw APIError(kind: .network, serverMessage: error.localizedDescription)
        }
    }

    private func validate(data: Data, response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else {
            throw APIError(kind: .network)
        }
        guard !(200..<300).contains(http.statusCode) else { return }

        let kind: APIError.Kind
        switch http.statusCode {
        case 401: kind = .unauthorized
        case 403: kind = .forbidden
        case 404: kind = .notFound
        case 500...: kind = .server
        default: kind = .client
        }
        throw APIError(kind: kind, status: http.statusCode, serverMessage: Self.errorMessage(from: data))
    }

    private static func status(of response: URLResponse) -> Int? {
        (response as? HTTPURLResponse)?.statusCode
    }

    /// Lenient extraction of an error message from a Zammad error payload.
    private static func errorMessage(from data: Data) -> String? {
        guard !data.isEmpty else { return nil }

        if let object = try? JSONSerialization.jsonObject(with: data) {
            if let dict = object as? [String: Any] {
                for key in ["error_description", "error", "message", "detail"] {
                    if let value = dict[key] as? String, !value.isEmpty {
                        return value
                    }
                }
                if let errors = dict["errors"] {
                    if let text = errors as? String, !text.isEmpty {
                        return text
                    }
                    if let list = errors as? [String] {
                        return list.joined(separator: ", ")
                    }
                    if let map = errors as? [String: Any] {
                        return map.map { "\($0.key): \($0.value)" }.joined(separator: ", ")
                    }
                }
            } else if let text = object as? String, !text.isEmpty {
                return text
            }
        }

        if let text = String(data: data, encoding: .utf8)?.trimmed,
           !text.isEmpty, !text.hasPrefix("<") {
            return text.count > 300 ? String(text.prefix(300)) + "…" : text
        }
        return nil
    }

    /// Lenient extraction of a token from a `/api/v1/signin` response.
    private static func tokenValue(from data: Data) -> String? {
        guard let object = try? JSONSerialization.jsonObject(with: data) else { return nil }
        if let dict = object as? [String: Any] {
            for key in ["token", "access_token", "api_token", "auth_token"] {
                if let value = dict[key] as? String, !value.isEmpty {
                    return value
                }
            }
            return nil
        }
        if let text = object as? String, text.count > 8 {
            return text
        }
        return nil
    }
}
