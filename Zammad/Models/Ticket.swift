import Foundation

/// A Zammad ticket. Field names match the camelCased JSON produced by
/// the `.convertFromSnakeCase` decoding strategy.
struct Ticket: Identifiable, Decodable, Hashable {
    let id: Int
    let number: FlexibleString?
    let title: String?
    let groupId: Int?
    let priorityId: Int?
    let stateId: Int?
    let ownerId: Int?
    let customerId: Int?
    let organizationId: Int?
    let articleCount: Int?
    let note: String?
    let createdAt: Date?
    let updatedAt: Date?
    let lastContactAt: Date?
    let escalationAt: Date?

    // Populated with `expand=true`
    let group: String?
    let state: String?
    let priority: String?
    let owner: String?
    let customer: String?
    let organization: String?
    let createdBy: String?
    let updatedBy: String?

    var displayNumber: String {
        if let number, !number.value.isEmpty {
            return "#" + number.value
        }
        return "#\(id)"
    }

    var displayTitle: String {
        let value = title?.trimmed ?? ""
        return value.isEmpty ? "#" + String(id) : value
    }
}
