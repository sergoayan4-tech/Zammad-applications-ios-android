import Foundation
import Observation

/// Backs the "Chats" tab: Zammad web-chat sessions over the long-polling
/// protocol (`POST /api/v1/message_send` + `/api/v1/message_receive`).
///
/// The flow mirrors the Zammad web client: a `login` handshake yields a
/// `client_id`, then events are pushed through `message_send` and received
/// from the `message_receive` long poll.
@MainActor
@Observable
final class ChatsModel {
    private(set) var waiting: [ChatSession] = []
    private(set) var mySessions: [ChatSession] = []
    private(set) var activeAgentCount = 0
    private(set) var loading = true
    private(set) var connected = false

    var chatDisabled = false
    var noPermission = false
    var errorMessage: String?

    private var api: APIClient?
    private var clientId: String?
    private var receiveTask: Task<Void, Never>?

    // MARK: - Lifecycle

    func connect(api: APIClient?) {
        guard let api else { return }
        guard clientId == nil, receiveTask == nil else { return }
        self.api = api
        loading = true
        errorMessage = nil
        Task { await handshake() }
    }

    func disconnect() {
        receiveTask?.cancel()
        receiveTask = nil
        clientId = nil
        connected = false
        loading = true
        waiting = []
        mySessions = []
    }

    func refresh() {
        Task { await send(event: "chat_status_agent", data: [:]) }
    }

    /// Takes the first waiting session (same as the Zammad web client).
    func answerNext() {
        Task { await send(event: "chat_session_start", data: [:]) }
    }

    func send(sessionId: String, content: String) {
        Task {
            await send(
                event: "chat_session_message",
                data: ["session_id": sessionId, "content": content]
            )
        }
    }

    func close(sessionId: String) {
        Task { await send(event: "chat_session_close", data: ["session_id": sessionId]) }
    }

    // MARK: - Transport

    private func handshake() async {
        guard let api else { return }
        do {
            let raw = try await api.postRaw(
                "/api/v1/message_send",
                body: ["data": ["event": "login"]]
            )
            guard let response = raw as? [String: Any],
                  let cid = response["client_id"] as? String,
                  !cid.isEmpty else {
                loading = false
                return
            }
            clientId = cid
            connected = true
            loading = false
            receiveTask = Task { await receiveLoop() }
            await send(event: "chat_status_agent", data: [:])
        } catch {
            loading = false
            errorMessage = (error as? APIError)?.errorDescription ?? "\(error)"
        }
    }

    private func receiveLoop() async {
        while !Task.isCancelled {
            guard let api, let clientId else { return }
            do {
                let raw = try await api.postRaw(
                    "/api/v1/message_receive",
                    body: ["client_id": clientId],
                    timeout: 60
                )
                handleReceive(raw)
            } catch {
                // Network hiccup or poll timeout: pause briefly, keep going.
                try? await Task.sleep(nanoseconds: 2_000_000_000)
            }
        }
    }

    private func send(event: String, data: [String: Any]) async {
        guard let api, let clientId else { return }
        do {
            _ = try await api.postRaw(
                "/api/v1/message_send",
                body: [
                    "client_id": clientId,
                    "data": ["event": event, "data": data]
                ]
            )
        } catch {
            errorMessage = (error as? APIError)?.errorDescription ?? "\(error)"
        }
    }

    // MARK: - Events

    private func handleReceive(_ raw: Any) {
        if let list = raw as? [[String: Any]] {
            for event in list { handle(event) }
        } else if let dict = raw as? [String: Any] {
            handle(dict)
        }
    }

    private func handle(_ event: [String: Any]) {
        guard let name = event["event"] as? String else { return }
        let data = event["data"] as? [String: Any] ?? [:]

        switch name {
        case "chat_status_agent":
            applyStatus(data)
        case "chat_session_start":
            if let session = data["session"] as? [String: Any] {
                upsertMySession(ChatSession(json: session))
            }
        case "chat_session_message":
            appendMessage(data)
        case "chat_session_closed", "chat_status_close":
            if let sid = data["session_id"] as? String {
                removeMySession(sid)
            }
        case "chat_error":
            handleChatError(data)
        default:
            break // typing indicators, queue positions, pong — not needed
        }
    }

    private func applyStatus(_ data: [String: Any]) {
        if let state = data["state"] as? String, state == "chat_disabled" {
            chatDisabled = true
            loading = false
            return
        }
        chatDisabled = false
        activeAgentCount = ChatJSON.int(data["active_agent_count"]) ?? 0
        waiting = (data["waiting_chat_session_list"] as? [[String: Any]] ?? [])
            .map { ChatSession(json: $0) }
        mySessions = (data["active_sessions"] as? [[String: Any]] ?? [])
            .map { ChatSession(json: $0) }
        loading = false
    }

    private func upsertMySession(_ session: ChatSession) {
        if let index = mySessions.firstIndex(where: { $0.sessionId == session.sessionId }) {
            mySessions[index] = session
        } else {
            mySessions.append(session)
        }
    }

    private func appendMessage(_ data: [String: Any]) {
        guard let sid = data["session_id"] as? String,
              let raw = data["message"] as? [String: Any],
              let index = mySessions.firstIndex(where: { $0.sessionId == sid }) else { return }

        let message = ChatMessage(json: raw)
        guard !mySessions[index].messages.contains(where: { $0.id == message.id }) else { return }
        mySessions[index].messages.append(message)
    }

    private func removeMySession(_ sessionId: String) {
        mySessions.removeAll { $0.sessionId == sessionId }
        Task { await send(event: "chat_status_agent", data: [:]) }
    }

    private func handleChatError(_ data: [String: Any]) {
        let state = data["state"] as? String ?? ""
        if state == "chat_disabled" { chatDisabled = true }
        if state == "no_permission" { noPermission = true }
        loading = false
    }
}
