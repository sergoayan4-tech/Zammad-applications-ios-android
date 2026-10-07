import Foundation
import Observation

/// Reference data shared by all screens: states, priorities and groups.
@Observable
final class ReferenceData {
    var states: [TicketState] = []
    var priorities: [TicketPriority] = []
    var groups: [ZammadGroup] = []
    var loaded = false

    var activeStates: [TicketState] {
        states.filter { $0.active ?? true }
    }

    var activePriorities: [TicketPriority] {
        priorities.filter { $0.active ?? true }
    }

    var activeGroups: [ZammadGroup] {
        groups.filter { $0.active ?? true }
    }

    func load(api: APIClient?) async {
        guard let api else { return }

        let fetchedStates: [TicketState]? = try? await api.get("/api/v1/ticket_states")
        let fetchedPriorities: [TicketPriority]? = try? await api.get("/api/v1/ticket_priorities")
        var fetchedGroups: [ZammadGroup]? = try? await api.get("/api/v1/groups")

        // GET /api/v1/groups requires the `admin.group` permission: agents get
        // 403 and the group picker turns out empty. Recover the visible groups
        // from the tickets themselves (group_id + group name via expand).
        if fetchedGroups?.isEmpty ?? true {
            fetchedGroups = await groupsFromTickets(api: api)
        }

        if let fetchedStates { states = fetchedStates }
        if let fetchedPriorities { priorities = fetchedPriorities }
        if let fetchedGroups { groups = fetchedGroups }
        loaded = true
    }

    /// Groups taken from recent tickets — a fallback for non-admin accounts.
    private func groupsFromTickets(api: APIClient) async -> [ZammadGroup]? {
        let tickets: [Ticket]? = try? await api.get(
            "/api/v1/tickets/search",
            query: [
                URLQueryItem(name: "query", value: "*"),
                URLQueryItem(name: "sort_by", value: "updated_at"),
                URLQueryItem(name: "order_by", value: "desc"),
                URLQueryItem(name: "expand", value: "true"),
                URLQueryItem(name: "per_page", value: "100")
            ]
        )
        guard let tickets else { return nil }

        var seen = Set<Int>()
        var result: [ZammadGroup] = []
        for ticket in tickets {
            guard let id = ticket.groupId,
                  let name = ticket.group?.trimmed,
                  !name.isEmpty, name != "-" else { continue }
            guard seen.insert(id).inserted else { continue }
            result.append(ZammadGroup(id: id, name: name, active: true))
        }
        return result.isEmpty ? nil : result
    }

    func reset() {
        states = []
        priorities = []
        groups = []
        loaded = false
    }

    // MARK: - Lookups

    func lookupName(stateID: Int?) -> String? {
        guard let stateID, let match = states.first(where: { $0.id == stateID }) else { return nil }
        return match.name
    }

    func lookupName(priorityID: Int?) -> String? {
        guard let priorityID, let match = priorities.first(where: { $0.id == priorityID }) else { return nil }
        return match.name
    }

    func lookupName(groupID: Int?) -> String? {
        guard let groupID, let match = groups.first(where: { $0.id == groupID }) else { return nil }
        return match.name
    }

    // MARK: - Defaults for the "new ticket" form

    func defaultStateID() -> Int? {
        activeStates.first(where: { $0.name.lowercased() == "new" })?.id ?? activeStates.first?.id
    }

    func defaultPriorityID() -> Int? {
        activePriorities.first(where: { $0.name.lowercased().contains("normal") })?.id
            ?? activePriorities.first?.id
    }

    func defaultGroupID() -> Int? {
        activeGroups.first?.id
    }
}
