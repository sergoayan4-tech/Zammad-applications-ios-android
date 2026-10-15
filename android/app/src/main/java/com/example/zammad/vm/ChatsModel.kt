package com.example.zammad.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zammad.api.ApiClient
import com.example.zammad.api.jsonObjects
import com.example.zammad.model.ChatMessage
import com.example.zammad.model.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs the "Chats" tab: Zammad web-chat sessions over the long-polling
 * protocol (`POST /api/v1/message_send` + `/api/v1/message_receive`).
 *
 * The flow mirrors the Zammad web client: a `login` handshake yields a
 * `client_id`, then events are pushed through `message_send` and received
 * from the `message_receive` long poll.
 */
class ChatsModel : ViewModel() {

    var waiting by mutableStateOf<List<ChatSession>>(emptyList())
        private set
    var mySessions by mutableStateOf<List<ChatSession>>(emptyList())
        private set
    var activeAgentCount by mutableStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var connected by mutableStateOf(false)
        private set
    var chatDisabled by mutableStateOf(false)
        private set
    var noPermission by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var api: ApiClient? = null
    private var clientId: String? = null
    private var loopJob: Job? = null

    // ------------------------------------------------------------- lifecycle

    fun connect(api: ApiClient) {
        if (clientId != null || loopJob?.isActive == true) return
        this.api = api
        loading = true
        errorMessage = null
        viewModelScope.launch(Dispatchers.IO) { handshake() }
    }

    fun disconnect() {
        loopJob?.cancel()
        loopJob = null
        clientId = null
        connected = false
        loading = true
        waiting = emptyList()
        mySessions = emptyList()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { sendEvent("chat_status_agent", JSONObject()) }
    }

    /** Takes the first waiting session (same as the Zammad web client). */
    fun answerNext() {
        viewModelScope.launch(Dispatchers.IO) { sendEvent("chat_session_start", JSONObject()) }
    }

    fun send(sessionId: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = JSONObject()
                .put("session_id", sessionId)
                .put("content", content)
            sendEvent("chat_session_message", data)
        }
    }

    fun close(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sendEvent("chat_session_close", JSONObject().put("session_id", sessionId))
        }
    }

    // -------------------------------------------------------------- transport

    private suspend fun handshake() {
        val api = api ?: return
        try {
            val resp = api.postRaw(
                "/api/v1/message_send",
                """{"data":{"event":"login"}}""",
                readTimeoutMs = 20_000
            )
            val cid = JSONObject(resp).optString("client_id").ifEmpty { null }
            if (cid == null) {
                withContext(Dispatchers.Main) { loading = false }
                return
            }
            clientId = cid
            withContext(Dispatchers.Main) { connected = true; loading = false }
            loopJob = viewModelScope.launch(Dispatchers.IO) { receiveLoop() }
            sendEvent("chat_status_agent", JSONObject())
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                loading = false
                errorMessage = e.message
            }
        }
    }

    private suspend fun receiveLoop() {
        val api = api ?: return
        val cid = clientId ?: return
        while (true) {
            try {
                val resp = api.postRaw(
                    "/api/v1/message_receive",
                    JSONObject().put("client_id", cid).toString(),
                    readTimeoutMs = 60_000
                )
                handleReceive(resp)
            } catch (e: Exception) {
                // Network hiccup or poll timeout: pause briefly, keep going.
                delay(2_000)
            }
        }
    }

    private suspend fun sendEvent(event: String, data: JSONObject) {
        val api = api ?: return
        val cid = clientId ?: return
        val body = JSONObject()
            .put("client_id", cid)
            .put("data", JSONObject().put("event", event).put("data", data))
        try {
            api.postRaw("/api/v1/message_send", body.toString(), readTimeoutMs = 20_000)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { errorMessage = e.message }
        }
    }

    // ----------------------------------------------------------------- events

    private suspend fun handleReceive(resp: String) {
        val events = mutableListOf<JSONObject>()
        when (resp.trimStart().firstOrNull()) {
            '[' -> JSONArray(resp).jsonObjects().forEach { events.add(it) }
            '{' -> try {
                events.add(JSONObject(resp))
            } catch (_: Exception) {
            }
        }
        for (event in events) handleEvent(event)
    }

    private suspend fun handleEvent(event: JSONObject) {
        val name = event.optString("event")
        val data = event.optJSONObject("data") ?: JSONObject()
        when (name) {
            "chat_status_agent" -> applyStatus(data)

            "chat_session_start" -> {
                val session = data.optJSONObject("session") ?: return
                withContext(Dispatchers.Main) { upsertMySession(ChatSession.fromJson(session)) }
            }

            "chat_session_message" -> appendMessage(data)

            "chat_session_closed", "chat_status_close" -> {
                val sid = data.optString("session_id")
                if (sid.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        mySessions = mySessions.filterNot { it.sessionId == sid }
                    }
                    sendEvent("chat_status_agent", JSONObject())
                }
            }

            "chat_error" -> {
                val state = data.optString("state")
                withContext(Dispatchers.Main) {
                    if (state == "chat_disabled") chatDisabled = true
                    if (state == "no_permission") noPermission = true
                    loading = false
                }
            }
        }
    }

    private suspend fun applyStatus(data: JSONObject) {
        if (data.optString("state") == "chat_disabled") {
            withContext(Dispatchers.Main) {
                chatDisabled = true
                loading = false
            }
            return
        }
        val waitingList = data.optJSONArray("waiting_chat_session_list")
            ?.jsonObjects()
            ?.map { ChatSession.fromJson(it) }
            ?: emptyList()
        val mine = data.optJSONArray("active_sessions")
            ?.jsonObjects()
            ?.map { ChatSession.fromJson(it) }
            ?: emptyList()
        val agents = data.optInt("active_agent_count", 0)
        withContext(Dispatchers.Main) {
            chatDisabled = false
            waiting = waitingList
            mySessions = mine
            activeAgentCount = agents
            loading = false
        }
    }

    private fun upsertMySession(session: ChatSession) {
        val index = mySessions.indexOfFirst { it.sessionId == session.sessionId }
        mySessions = if (index >= 0) {
            mySessions.toMutableList().apply { this[index] = session }
        } else {
            mySessions + session
        }
    }

    private suspend fun appendMessage(data: JSONObject) {
        val sid = data.optString("session_id")
        val raw = data.optJSONObject("message") ?: return
        val message = ChatMessage.fromJson(raw)
        withContext(Dispatchers.Main) {
            val index = mySessions.indexOfFirst { it.sessionId == sid }
            if (index < 0) return@withContext
            val session = mySessions[index]
            if (message.id != null && session.messages.any { it.id == message.id }) {
                return@withContext
            }
            val updated = session.copy(messages = session.messages + message)
            mySessions = mySessions.toMutableList().apply { this[index] = updated }
        }
    }
}
