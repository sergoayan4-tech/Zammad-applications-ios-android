import Foundation

/// A single message (article) inside a ticket.
struct TicketArticle: Identifiable, Decodable, Hashable {
    let id: Int
    let ticketId: Int?
    let senderId: Int?
    let sender: String?
    let typeId: Int?
    let type: FlexibleString?
    let from: String?
    let to: String?
    let cc: String?
    let subject: String?
    let body: String?
    let contentType: String?
    let isInternal: Bool?
    let createdAt: Date?
    let updatedAt: Date?
    let createdBy: String?
    let updatedBy: String?
    let timeUnit: FlexibleString?
    let attachments: [ArticleAttachment]?

    enum CodingKeys: String, CodingKey {
        case id
        case ticketId
        case senderId
        case sender
        case typeId
        case type
        case from
        case to
        case cc
        case subject
        case body
        case contentType
        case isInternal = "internal"
        case createdAt
        case updatedAt
        case createdBy
        case updatedBy
        case timeUnit
        case attachments
    }

    var isFromCustomer: Bool {
        (sender ?? "").lowercased() == "customer"
    }

    var isFromSystem: Bool {
        (sender ?? "").lowercased() == "system"
    }
}

/// A file attached to an article.
struct ArticleAttachment: Identifiable, Decodable, Hashable {
    let id: Int?
    let filename: String
    let size: FlexibleString?

    var fileExtension: String {
        (filename as NSString).pathExtension.lowercased()
    }

    var sizeText: String? {
        guard let size, let bytes = Int(size.value) else { return nil }
        return ByteCountFormatter.string(fromByteCount: Int64(bytes), countStyle: .file)
    }
}
