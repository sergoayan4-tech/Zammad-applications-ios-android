import Foundation
import Observation

/// Backs the "new ticket" form.
@MainActor
@Observable
final class NewTicketModel {
    var title = ""
    var customerEmail = ""
    var body = ""
    var groupId: Int?
    var priorityId: Int?
    var stateId: Int?

    /// `nil` = the creator (Zammad default), `1` = unassigned.
    var ownerId: Int?
    var ownerName: String?
    var ownerQuery = ""

    private(set) var owners: [ZammadUser] = []
    private(set) var ownersLoading = false

    private(set) var creating = false
    var errorMessage: Error?

    private var ownerSearchTask: Task<Void, Never>?

    func applyDefaults(from reference: ReferenceData) {
        if groupId == nil {
            groupId = reference.defaultGroupID()
        }
        if priorityId == nil {
            priorityId = reference.defaultPriorityID()
        }
        if stateId == nil {
            stateId = reference.defaultStateID()
        }
    }

    func create(api: APIClient?) async -> Ticket? {
        guard let api, !creating else { return nil }
        creating = true
        errorMessage = nil
        defer { creating = false }

        let article: [String: Any] = [
            "body": body.trimmed,
            "subject": title.trimmed,
            "type": "email",
            "sender": "Agent",
            "internal": false,
            "content_type": "text/plain"
        ]

        var payload: [String: Any] = [
            "title": title.trimmed,
            "article": article
        ]
        if let groupId {
            payload["group_id"] = groupId
        }
        if let priorityId {
            payload["priority_id"] = priorityId
        }
        if let stateId {
            payload["state_id"] = stateId
        }
        if let ownerId {
            payload["owner_id"] = ownerId
        }
        let email = customerEmail.trimmed
        if !email.isEmpty {
            payload["customer"] = email
        }

        do {
            let ticket: Ticket = try await api.post("/api/v1/tickets", body: payload)
            resetTextFields()
            return ticket
        } catch let createError {
            errorMessage = createError
            return nil
        }
    }

    func resetTextFields() {
        title = ""
        customerEmail = ""
        body = ""
        ownerId = nil
        ownerName = nil
        ownerQuery = ""
        owners = []
        errorMessage = nil
    }

    // MARK: - Owner search (assignment while creating)

    /// Picks an agent from the search results.
    func selectOwner(_ user: ZammadUser) {
        ownerId = user.id
        ownerName = user.displayName
        ownerQuery = ""
        owners = []
    }

    /// Debounced owner lookup (`/api/v1/users/search`).
    func searchOwners(_ text: String, api: APIClient?) {
        ownerSearchTask?.cancel()

        let trimmed = text.trimmed
        guard !trimmed.isEmpty else {
            owners = []
            ownersLoading = false
            return
        }

        ownerSearchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            await self?.performOwnerSearch(trimmed, api: api)
        }
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
