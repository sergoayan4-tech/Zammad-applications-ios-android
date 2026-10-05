import Foundation
import Observation

/// Reference data shared by all screens: states, priorities and groups.
@Observable
final class ReferenceData {
    var states: [TicketState] = []
    var priorities: [TicketPriority] = []
    var groups: [Group] = []
    var loaded = false

    var activeStates: [TicketState] {
        states.filter { $0.active ?? true }
    }

    var activePriorities: [TicketPriority] {
        priorities.filter { $0.active ?? true }
    }

    var activeGroups: [Group] {
        groups.filter { $0.active ?? true }
    }

    func load(api: APIClient?) async {
        guard let api else { return }

        let fetchedStates: [TicketState]? = try? await api.get("/api/v1/ticket_states")
        let fetchedPriorities: [TicketPriority]? = try? await api.get("/api/v1/ticket_priorities")
        let fetchedGroups: [Group]? = try? await api.get("/api/v1/groups")

        if let fetchedStates { states = fetchedStates }
        if let fetchedPriorities { priorities = fetchedPriorities }
        if let fetchedGroups { groups = fetchedGroups }
        loaded = true
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
