import Foundation
import Observation

/// Backs the ticket detail screen: articles, replies and ticket actions.
@MainActor
@Observable
final class TicketDetailModel {
    private(set) var ticket: Ticket
    private(set) var articles: [TicketArticle] = []
    private(set) var loading = true
    private(set) var sending = false
    private(set) var updating = false
    private(set) var owners: [ZammadUser] = []
    private(set) var ownersLoading = false

    var errorMessage: Error?
    var replyText = ""
    var isInternal = false

    private var ownerSearchTask: Task<Void, Never>?

    init(ticket: Ticket) {
        self.ticket = ticket
    }

    var replyDraft: String {
        replyText.trimmed
    }

    var canSend: Bool {
        !replyDraft.isEmpty && !sending
    }

    // MARK: - Loading

    func load(api: APIClient?) async {
        guard let api else {
            loading = false
            return
        }

        loading = true
        errorMessage = nil
        var firstError: Error?

        do {
            let fresh: Ticket = try await api.get(
                "/api/v1/tickets/\(ticket.id)",
                query: [URLQueryItem(name: "expand", value: "true")]
            )
            ticket = fresh
        } catch let loadError {
            firstError = loadError
        }

        do {
            let list: [TicketArticle] = try await api.get("/api/v1/ticket_articles/by_ticket/\(ticket.id)")
            articles = list.sorted { lhs, rhs in
                (lhs.createdAt ?? .distantPast) < (rhs.createdAt ?? .distantPast)
            }
        } catch let loadError {
            if firstError == nil {
                firstError = loadError
            }
        }

        errorMessage = firstError
        loading = false
    }

    // MARK: - Reply

    func send(api: APIClient?) async {
        let text = replyDraft
        guard let api, canSend else { return }

        sending = true
        errorMessage = nil

        let payload: [String: Any] = [
            "ticket_id": ticket.id,
            "body": text,
            "content_type": "text/plain",
            "type": isInternal ? "note" : "email",
            "internal": isInternal,
            "sender": "Agent"
        ]

        do {
            let article: TicketArticle = try await api.post("/api/v1/ticket_articles", body: payload)
            articles.append(article)
            replyText = ""
        } catch let sendError {
            errorMessage = sendError
        }
        sending = false
    }

    // MARK: - Ticket actions

    func setState(id: Int, api: APIClient?) async {
        await update(["state_id": id], api: api)
    }

    func setPriority(id: Int, api: APIClient?) async {
        await update(["priority_id": id], api: api)
    }

    func setGroup(id: Int, api: APIClient?) async {
        await update(["group_id": id], api: api)
    }

    func setOwner(id: Int, api: APIClient?) async {
        await update(["owner_id": id], api: api)
    }

    // MARK: - Owners (assignment)

    func loadOwners(api: APIClient?) async {
        guard let api else { return }
        ownersLoading = true

        let users: [ZammadUser]? = try? await api.get(
            "/api/v1/users",
            query: [
                URLQueryItem(name: "expand", value: "true"),
                URLQueryItem(name: "per_page", value: "100")
            ]
        )
        if let users {
            owners = Self.agentsOnly(users)
        }
        ownersLoading = false
    }

    /// Debounced owner lookup (`/api/v1/users/search`).
    func searchOwners(_ text: String, api: APIClient?) {
        ownerSearchTask?.cancel()

        let trimmed = text.trimmed
        guard !trimmed.isEmpty else {
            ownerSearchTask = Task { [weak self] in
                await self?.loadOwners(api: api)
            }
            return
        }

        ownerSearchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            await self?.performOwnerSearch(trimmed, api: api)
        }
    }

    // MARK: - Attachments

    func downloadAttachment(
        _ attachment: ArticleAttachment,
        article: TicketArticle,
        api: APIClient?
    ) async throws -> URL {
        guard let api, let attachmentID = attachment.id else {
            throw APIError(kind: .notFound)
        }

        let data = try await api.rawData(
            "/api/v1/ticket_attachment/\(ticket.id)/\(article.id)/\(attachmentID)"
        )

        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("attachments", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)

        let url = directory.appendingPathComponent(attachment.filename.sanitizedFileName)
        try data.write(to: url, options: .atomic)
        return url
    }

    // MARK: - Private

    private func update(_ payload: [String: Any], api: APIClient?) async {
        guard let api, !updating else { return }
        updating = true
        errorMessage = nil

        do {
            let updated: Ticket = try await api.put("/api/v1/tickets/\(ticket.id)", body: payload)
            ticket = updated
        } catch let updateError {
            errorMessage = updateError
        }
        updating = false
    }

    private func performOwnerSearch(_ text: String, api: APIClient?) async {
        guard let api else { return }
        ownersLoading = true

        let users: [ZammadUser]? = try? await api.get(
            "/api/v1/users/search",
            query: [
                URLQueryItem(name: "query", value: text),
                URLQueryItem(name: "expand", value: "true"),
                URLQueryItem(name: "per_page", value: "20")
            ]
        )
        if let users {
            owners = Self.agentsOnly(users)
        }
        ownersLoading = false
    }

    /// Keeps active agents (drops the system "-" user and customers).
    private static func agentsOnly(_ users: [ZammadUser]) -> [ZammadUser] {
        let usable = users.filter { user in
            let login = user.login?.trimmed ?? ""
            let first = user.firstname?.trimmed ?? ""
            return login != "-" && first != "-"
        }

        let knownRoles = usable.filter { ($0.roles?.isEmpty == false) }
        if knownRoles.isEmpty {
            return usable.filter { $0.active ?? true }
        }
        return usable.filter { ($0.active ?? true) && $0.isAgent }
    }
}
