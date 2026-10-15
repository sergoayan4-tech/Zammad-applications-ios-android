import Foundation

/// Lenient JSON helpers for the chat long-polling payloads
/// (they arrive as untyped dictionaries, not decoded models).
enum ChatJSON {
    static func int(_ value: Any?) -> Int? {
        switch value {
        case let v as Int: return v
        case let v as Double: return Int(v)
        case let v as String: return Int(v)
        default: return nil
        }
    }

    static func date(_ value: Any?) -> Date? {
        guard let raw = value as? String else { return nil }
        return ZammadDate.parse(raw)
    }
}

/// One message inside a chat session (`Chat::Message` attributes).
struct ChatMessage: Identifiable, Hashable {
    let id: Int
    let content: String
    let createdByUserId: Int?
    let createdAt: Date?

    /// Parses the long-polling payload (raw dictionary).
    init(json: [String: Any]) {
        id = ChatJSON.int(json["id"]) ?? 0
        content = (json["content"] as? String) ?? ""
        createdByUserId = ChatJSON.int(json["created_by_id"])
        createdAt = ChatJSON.date(json["created_at"])
    }
}

/// One chat session — waiting or running (`Chat::Session` attributes).
struct ChatSession: Identifiable, Hashable {
    let id: Int
    let chatId: Int?
    let sessionId: String?
    let state: String?
    /// The agent assigned to a running session (= me).
    let userId: Int?
    let createdAt: Date?
    /// The page URL a visitor came from (empty for agent sessions).
    let url: String?
    var messages: [ChatMessage]

    /// Parses the long-polling payload (raw dictionary).
    init(json: [String: Any]) {
        id = ChatJSON.int(json["id"]) ?? 0
        chatId = ChatJSON.int(json["chat_id"])
        sessionId = json["session_id"] as? String
        state = json["state"] as? String
        userId = ChatJSON.int(json["user_id"])
        createdAt = ChatJSON.date(json["created_at"])
        let prefs = json["preferences"] as? [String: Any]
        url = prefs?["url"] as? String
        messages = (json["messages"] as? [[String: Any]] ?? []).map {
            ChatMessage(json: $0)
        }
    }
}
