package com.example.zammad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zammad.core.L10n
import com.example.zammad.session.AuthMode
import com.example.zammad.session.ConnectPhase
import com.example.zammad.session.SessionStore
import kotlinx.coroutines.launch

/** First screen: server address + credentials (mirrors iOS ConnectView). */
@Composable
fun ConnectScreen(session: SessionStore) {
    val prefs = session.prefs
    var server by remember { mutableStateOf(prefs.serverUrl) }
    var mode by remember {
        mutableStateOf(
            if (prefs.token.isNotEmpty()) AuthMode.TOKEN
            else if (prefs.login.isNotEmpty()) AuthMode.PASSWORD
            else AuthMode.TOKEN
        )
    }
    var token by remember { mutableStateOf(prefs.token) }
    var username by remember { mutableStateOf(prefs.login) }
    var password by remember { mutableStateOf(prefs.password) }
    var validationKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val connecting = session.phase == ConnectPhase.CONNECTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(14.dp))
        Text(L10n.t("connect.title"), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            L10n.t("connect.subtitle"),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = server,
            onValueChange = { server = it; validationKey = null },
            label = { Text(L10n.t("connect.server")) },
            placeholder = { Text(L10n.t("connect.server.placeholder")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Text(
            L10n.t("connect.auth"),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        SegmentedControl(
            options = listOf(
                AuthMode.TOKEN.name to L10n.t("connect.auth.token"),
                AuthMode.PASSWORD.name to L10n.t("connect.auth.password")
            ),
            selected = mode.name
        ) {
            mode = if (it == AuthMode.TOKEN.name) AuthMode.TOKEN else AuthMode.PASSWORD
            validationKey = null
        }
        Spacer(Modifier.height(16.dp))

        if (mode == AuthMode.TOKEN) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; validationKey = null },
                label = { Text(L10n.t("connect.token")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                L10n.t("connect.token.hint"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; validationKey = null },
                label = { Text(L10n.t("connect.username")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; validationKey = null },
                label = { Text(L10n.t("connect.password")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                L10n.t("connect.password.hint"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val error = validationKey?.let { L10n.t(it) } ?: errorText(session.connectError)
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val sanitized = SessionStore.sanitizeServer(server)
                when {
                    sanitized == null -> validationKey = "connect.error.url"
                    mode == AuthMode.TOKEN && token.isBlank() ->
                        validationKey = "connect.error.token"
                    mode == AuthMode.PASSWORD && (username.isBlank() || password.isBlank()) ->
                        validationKey = "connect.error.credentials"
                    else -> {
                        validationKey = null
                        session.connectAsync(sanitized, mode, token, username, password)
                    }
                }
            },
            enabled = !connecting,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Text(
                    " " + L10n.t("connect.connecting"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    L10n.t("connect.submit"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(34.dp))
        SegmentedControl(
            options = listOf(
                "system" to "System",
                "en" to "English",
                "ru" to "Русский"
            ),
            selected = prefs.language
        ) {
            prefs.language = it
            session.applyLanguage()
        }
        Spacer(Modifier.height(24.dp))
    }
}
