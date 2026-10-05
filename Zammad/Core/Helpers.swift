import Foundation

extension String {
    /// Whitespace trimmed copy of the string.
    var trimmed: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// First path component safe version of a file name.
    var sanitizedFileName: String {
        let invalid = CharacterSet(charactersIn: "/\\:*?\"<>|")
        let cleaned = components(separatedBy: invalid).joined(separator: "_")
        return cleaned.isEmpty ? "file" : cleaned
    }
}

/// Normalizes whatever the user typed into a server URL.
func sanitizedServerURL(_ input: String) -> URL? {
    var value = input.trimmed
    guard !value.isEmpty else { return nil }
    if !value.contains("://") {
        value = "https://" + value
    }
    while value.hasSuffix("/") {
        value.removeLast()
    }
    guard let url = URL(string: value), let host = url.host, !host.isEmpty else {
        return nil
    }
    return url
}

/// State names that mean "ticket is not open anymore".
private let closedStateNames: Set<String> = ["closed", "removed", "merged"]

func isClosedStateName(_ name: String) -> Bool {
    closedStateNames.contains(name.lowercased())
}

// MARK: - Display helpers (ticket → localized/expanded name)

func ticketStateName(_ ticket: Ticket, _ reference: ReferenceData) -> String {
    if let name = ticket.state, !name.isEmpty, name != "-" {
        return name
    }
    return reference.lookupName(stateID: ticket.stateId) ?? ""
}

func ticketPriorityName(_ ticket: Ticket, _ reference: ReferenceData) -> String {
    if let name = ticket.priority, !name.isEmpty, name != "-" {
        return name
    }
    return reference.lookupName(priorityID: ticket.priorityId) ?? ""
}

func ticketGroupName(_ ticket: Ticket, _ reference: ReferenceData) -> String {
    if let name = ticket.group, !name.isEmpty, name != "-" {
        return name
    }
    return reference.lookupName(groupID: ticket.groupId) ?? ""
}

func ticketOwnerName(_ ticket: Ticket) -> String {
    let owner = ticket.owner?.trimmed ?? ""
    if owner.isEmpty || owner == "-" {
        return "—"
    }
    return owner
}
