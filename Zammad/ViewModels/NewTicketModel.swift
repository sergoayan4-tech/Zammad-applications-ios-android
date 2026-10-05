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

    private(set) var creating = false
    var errorMessage: Error?

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
        errorMessage = nil
    }
}
