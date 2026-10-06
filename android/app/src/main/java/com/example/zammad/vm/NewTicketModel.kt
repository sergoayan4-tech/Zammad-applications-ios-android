package com.example.zammad.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zammad.api.ApiErrorKind
import com.example.zammad.api.ApiException
import com.example.zammad.api.jsonBody
import com.example.zammad.model.ReferenceData
import com.example.zammad.model.Ticket
import com.example.zammad.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Backs the "new ticket" form. */
class NewTicketModel : ViewModel() {

    var title by mutableStateOf("")
    var customerEmail by mutableStateOf("")
    var body by mutableStateOf("")
    var groupId by mutableStateOf<Int?>(null)
    var priorityId by mutableStateOf<Int?>(null)
    var stateId by mutableStateOf<Int?>(null)

    var creating by mutableStateOf(false)
        private set
    var validationKey by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<ApiException?>(null)

    fun applyDefaults(reference: ReferenceData) {
        if (groupId == null) groupId = reference.activeGroups.firstOrNull()?.id
        if (priorityId == null) priorityId = reference.activePriorities.firstOrNull()?.id
        if (stateId == null) stateId = reference.activeStates.firstOrNull()?.id
    }

    /** Returns the created ticket, or null on validation/network failure. */
    suspend fun create(session: SessionStore): Ticket? {
        if (creating) return null
        validationKey = null
        errorMessage = null
        if (title.trim().isEmpty()) {
            validationKey = "new.error.title"
            return null
        }
        if (groupId == null && session.reference.activeGroups.isNotEmpty()) {
            validationKey = "new.error.group"
            return null
        }
        if (body.trim().isEmpty()) {
            validationKey = "new.error.message"
            return null
        }

        creating = true
        return try {
            val article = jsonBody {
                put("body", body.trim())
                put("subject", title.trim())
                put("type", "email")
                put("sender", "agent")
                put("internal", false)
            }
            val payload = jsonBody {
                put("title", title.trim())
                groupId?.let { put("group_id", it) }
                priorityId?.let { put("priority_id", it) }
                stateId?.let { put("state_id", it) }
                val email = customerEmail.trim()
                if (email.isNotEmpty()) put("customer", email)
                put("article", JSONObject(article))
            }
            val resp = withContext(Dispatchers.IO) {
                session.api.post("/api/v1/tickets", payload)
            }
            val obj = JSONObject(resp)
            val inner = if (obj.has("ticket") && obj.optJSONObject("ticket") != null) {
                obj.getJSONObject("ticket")
            } else {
                obj
            }
            Ticket.fromJson(inner)
        } catch (e: ApiException) {
            errorMessage = e
            null
        } catch (e: Exception) {
            errorMessage = ApiException(ApiErrorKind.UNKNOWN, e.message ?: "error")
            null
        } finally {
            creating = false
        }
    }
}
