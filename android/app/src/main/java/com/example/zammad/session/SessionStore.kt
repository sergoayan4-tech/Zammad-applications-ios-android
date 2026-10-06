package com.example.zammad.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.zammad.api.ApiClient
import com.example.zammad.api.ApiErrorKind
import com.example.zammad.api.ApiException
import com.example.zammad.api.flexString
import com.example.zammad.api.jsonBody
import com.example.zammad.core.L10n
import com.example.zammad.core.Prefs
import com.example.zammad.model.ReferenceData
import com.example.zammad.model.ZammadGroup
import com.example.zammad.model.ZammadPriority
import com.example.zammad.model.ZammadState
import com.example.zammad.model.ZammadUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

enum class AuthMode { TOKEN, PASSWORD }
enum class ConnectPhase { IDLE, CONNECTING }

/**
 * Owns the API client, the signed-in user and the loaded lookups.
 * Mirrors the iOS SessionStore + ReferenceData.
 */
class SessionStore(val prefs: Prefs) {
    val api = ApiClient()

    /** Survives screen disposal (connect must finish even if the form leaves). */
    private val opScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var phase by mutableStateOf(ConnectPhase.IDLE)
        private set
    var connected by mutableStateOf(false)
        private set
    var connectError by mutableStateOf<ApiException?>(null)
        private set
    var currentUser by mutableStateOf<ZammadUser?>(null)
        private set
    var reference by mutableStateOf(ReferenceData())
        private set
    var authMode by mutableStateOf(AuthMode.TOKEN)
        private set

    val serverDisplay: String get() = prefs.serverUrl.ifEmpty { "—" }

    fun applyLanguage() {
        L10n.code = prefs.resolveLanguage()
    }

    // ------------------------------------------------------------------ connect

    /** Fire-and-forget connect; safe to call from click handlers. */
    fun connectAsync(
        server: String,
        mode: AuthMode,
        token: String,
        username: String,
        password: String
    ) {
        opScope.launch {
            connect(server, mode, token, username, password)
        }
    }

    suspend fun connect(
        server: String,
        mode: AuthMode,
        token: String,
        username: String,
        password: String
    ) {
        val sanitized = sanitizeServer(server)
        if (sanitized == null) {
            connectError = ApiException(ApiErrorKind.INVALID_URL, "bad server")
            return
        }

        phase = ConnectPhase.CONNECTING
        connectError = null
        api.baseUrl = sanitized

        try {
            when (mode) {
                AuthMode.TOKEN -> {
                    api.authHeader = ApiClient.tokenAuth(token.trim())
                    prefs.token = token.trim()
                    prefs.login = ""
                    prefs.password = ""
                }

                AuthMode.PASSWORD -> {
                    val signInToken = trySignIn(sanitized, username, password)
                    if (signInToken != null) {
                        api.authHeader = ApiClient.tokenAuth(signInToken)
                        prefs.token = signInToken
                    } else {
                        api.authHeader = ApiClient.basicAuth(username, password)
                        prefs.token = ""
                    }
                    prefs.login = username
                    prefs.password = password
                }
            }

            val me = withContext(Dispatchers.IO) { api.getObject("/api/v1/users/me") }
            currentUser = ZammadUser.fromJson(me)
            prefs.serverUrl = sanitized
            authMode = mode
            connected = true
            loadReference()
        } catch (e: ApiException) {
            failConnect(e)
        } catch (e: Exception) {
            failConnect(ApiException(ApiErrorKind.UNKNOWN, e.message ?: "error"))
        }
        phase = ConnectPhase.IDLE
    }

    /** Reuse the stored credentials on launch. */
    suspend fun restore() {
        if (prefs.serverUrl.isEmpty()) return
        if (prefs.token.isEmpty() && (prefs.login.isEmpty() || prefs.password.isEmpty())) return

        phase = ConnectPhase.CONNECTING
        api.baseUrl = prefs.serverUrl
        try {
            if (prefs.token.isNotEmpty()) {
                api.authHeader = ApiClient.tokenAuth(prefs.token)
                authMode = AuthMode.TOKEN
            } else {
                val signInToken = trySignIn(prefs.serverUrl, prefs.login, prefs.password)
                if (signInToken != null) {
                    api.authHeader = ApiClient.tokenAuth(signInToken)
                    prefs.token = signInToken
                } else {
                    api.authHeader = ApiClient.basicAuth(prefs.login, prefs.password)
                }
                authMode = AuthMode.PASSWORD
            }
            val me = withContext(Dispatchers.IO) { api.getObject("/api/v1/users/me") }
            currentUser = ZammadUser.fromJson(me)
            connected = true
            loadReference()
        } catch (_: Exception) {
            api.authHeader = null
            connected = false
        }
        phase = ConnectPhase.IDLE
    }

    fun disconnect() {
        prefs.clearCredentials()
        api.baseUrl = ""
        api.authHeader = null
        currentUser = null
        reference = ReferenceData()
        connected = false
        connectError = null
        authMode = AuthMode.TOKEN
    }

    private fun failConnect(e: ApiException) {
        connectError = e
        api.authHeader = null
        connected = false
    }

    private suspend fun trySignIn(server: String, username: String, password: String): String? {
        return try {
            api.baseUrl = server
            api.authHeader = null
            val body = jsonBody {
                put("username", username)
                put("password", password)
            }
            val resp = withContext(Dispatchers.IO) { api.post("/api/v1/signin", body) }
            JSONObject(resp).flexString("token")
        } catch (_: Exception) {
            null
        }
    }

    // --------------------------------------------------------------- reference

    suspend fun loadReference() {
        try {
            val states = withContext(Dispatchers.IO) {
                api.getArray("/api/v1/ticket_states").map { ZammadState.fromJson(it) }
            }
            val priorities = withContext(Dispatchers.IO) {
                api.getArray("/api/v1/ticket_priorities").map { ZammadPriority.fromJson(it) }
            }
            val groups = withContext(Dispatchers.IO) {
                api.getArray("/api/v1/groups").map { ZammadGroup.fromJson(it) }
            }
            // GET /users may be forbidden for non-admins — the app still works.
            val users = try {
                withContext(Dispatchers.IO) {
                    api.getArray("/api/v1/users").map { ZammadUser.fromJson(it) }
                }
            } catch (_: ApiException) {
                emptyList()
            }
            reference = ReferenceData(states, priorities, groups, users)
        } catch (_: ApiException) {
            // Keep what we already have; the ticket list still loads.
        }
    }

    suspend fun searchUsers(query: String): List<ZammadUser> = withContext(Dispatchers.IO) {
        try {
            api.getArray("/api/v1/users/search", listOf("query" to query))
                .map { ZammadUser.fromJson(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        /** Adds a scheme when missing, validates the URL and trims the slash. */
        fun sanitizeServer(raw: String): String? {
            var s = raw.trim()
            if (s.isEmpty()) return null
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            val url = try {
                URL(s)
            } catch (_: Exception) {
                return null
            }
            if (url.host.isEmpty()) return null
            return s.trimEnd('/')
        }
    }
}
