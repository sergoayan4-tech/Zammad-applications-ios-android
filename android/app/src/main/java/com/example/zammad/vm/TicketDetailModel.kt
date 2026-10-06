package com.example.zammad.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zammad.api.ApiErrorKind
import com.example.zammad.api.ApiException
import com.example.zammad.api.jsonBody
import com.example.zammad.model.Ticket
import com.example.zammad.model.TicketArticle
import com.example.zammad.model.ZammadUser
import com.example.zammad.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Backs the ticket detail screen: articles, replies and ticket actions. */
class TicketDetailModel(initial: Ticket) : ViewModel() {

    var ticket by mutableStateOf(initial)
        private set
    var articles by mutableStateOf<List<TicketArticle>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var sending by mutableStateOf(false)
        private set
    var updating by mutableStateOf(false)
        private set
    var owners by mutableStateOf<List<ZammadUser>>(emptyList())
        private set
    var ownersLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<ApiException?>(null)
    var replyText by mutableStateOf("")
    var isInternal by mutableStateOf(false)

    private var session: SessionStore? = null
    private var ownerSearchJob: Job? = null

    val replyDraft: String get() = replyText.trim()
    val canSend: Boolean get() = replyDraft.isNotEmpty() && !sending

    fun configure(session: SessionStore) {
        this.session = session
        if (owners.isEmpty()) {
            owners = session.reference.users
        }
    }

    // ------------------------------------------------------------------ load

    suspend fun load() {
        val s = session
        if (s == null) {
            loading = false
            return
        }
        loading = true
        errorMessage = null
        try {
            val pair = withContext(Dispatchers.IO) {
                val t = s.api.getObject("/api/v1/tickets/${ticket.id}?expand=true")
                val a = s.api.getArray("/api/v1/ticket_articles/by_ticket/${ticket.id}")
                Pair(t, a)
            }
            ticket = Ticket.fromJson(pair.first)
            articles = pair.second.map { TicketArticle.fromJson(it) }
        } catch (e: ApiException) {
            errorMessage = e
        } catch (e: Exception) {
            errorMessage = ApiException(ApiErrorKind.UNKNOWN, e.message ?: "error")
        }
        loading = false
    }

    // ------------------------------------------------------------------ reply

    suspend fun send() {
        val s = session ?: return
        if (!canSend) return
        sending = true
        errorMessage = null
        try {
            val text = replyDraft
            val internal = isInternal
            val body = jsonBody {
                put("ticket_id", ticket.id)
                put("body", text)
                put("type", "note")
                put("sender", "agent")
                put("internal", internal)
            }
            withContext(Dispatchers.IO) { s.api.post("/api/v1/ticket_articles", body) }
            replyText = ""
            load()
        } catch (e: ApiException) {
            errorMessage = e
        } catch (e: Exception) {
            errorMessage = ApiException(ApiErrorKind.UNKNOWN, e.message ?: "error")
        }
        sending = false
    }

    // ---------------------------------------------------------------- actions

    suspend fun setState(stateId: Int) = updateTicket(mapOf("state_id" to stateId))

    suspend fun setPriority(priorityId: Int) = updateTicket(mapOf("priority_id" to priorityId))

    suspend fun setGroup(groupId: Int) = updateTicket(mapOf("group_id" to groupId))

    suspend fun setOwner(userId: Int) = updateTicket(mapOf("owner_id" to userId))

    private suspend fun updateTicket(fields: Map<String, Any>) {
        val s = session ?: return
        updating = true
        errorMessage = null
        try {
            val body = JSONObject().apply { fields.forEach { (k, v) -> put(k, v) } }.toString()
            val resp = withContext(Dispatchers.IO) {
                s.api.put("/api/v1/tickets/${ticket.id}", body)
            }
            val obj = JSONObject(resp)
            val inner = if (obj.has("ticket") && obj.optJSONObject("ticket") != null) {
                obj.getJSONObject("ticket")
            } else {
                obj
            }
            ticket = Ticket.fromJson(inner)
            // Refresh names (state/group/owner strings come from expand).
            load()
        } catch (e: ApiException) {
            errorMessage = e
        } catch (e: Exception) {
            errorMessage = ApiException(ApiErrorKind.UNKNOWN, e.message ?: "error")
        }
        updating = false
    }

    // ------------------------------------------------------------------ owner

    /** Debounced agent search for the "assign to" sheet. */
    fun searchOwners(query: String) {
        val s = session ?: return
        ownerSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            owners = s.reference.users
            ownersLoading = false
            return
        }
        ownersLoading = true
        ownerSearchJob = viewModelScope.launch {
            delay(300)
            val res = s.searchUsers(trimmed)
            owners = res
            ownersLoading = false
        }
    }

    override fun onCleared() {
        ownerSearchJob?.cancel()
        super.onCleared()
    }
}
