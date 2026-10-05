import Foundation

/// Ticket state lookup entry (`GET /api/v1/ticket_states`).
struct TicketState: Identifiable, Decodable, Hashable {
    let id: Int
    let name: String
    let active: Bool?
}

/// Ticket priority lookup entry (`GET /api/v1/ticket_priorities`).
struct TicketPriority: Identifiable, Decodable, Hashable {
    let id: Int
    let name: String
    let active: Bool?
}

/// Group lookup entry (`GET /api/v1/groups`).
struct Group: Identifiable, Decodable, Hashable {
    let id: Int
    let name: String
    let active: Bool?
}

/// A Zammad user (`GET /api/v1/users`, `/users/me`, `/users/search`).
struct ZammadUser: Identifiable, Decodable, Hashable {
    let id: Int
    let firstname: String?
    let lastname: String?
    let email: String?
    let login: String?
    let active: Bool?
    let department: String?
    let organization: String?
    let roles: [String]?

    var displayName: String {
        let names = [firstname, lastname]
            .compactMap { $0?.trimmed }
            .filter { !$0.isEmpty && $0 != "-" }
        if !names.isEmpty {
            return names.joined(separator: " ")
        }
        if let login, !login.trimmed.isEmpty, login != "-" {
            return login.trimmed
        }
        if let email, !email.trimmed.isEmpty {
            return email
        }
        return "#\(id)"
    }

    var subtitle: String {
        if let email, !email.trimmed.isEmpty, email != displayName {
            return email.trimmed
        }
        if let login, !login.trimmed.isEmpty, login != displayName {
            return login.trimmed
        }
        return department?.trimmed ?? ""
    }

    var isAgent: Bool {
        guard let roles, !roles.isEmpty else { return true }
        return roles.contains("Agent") || roles.contains("Admin")
    }
}
