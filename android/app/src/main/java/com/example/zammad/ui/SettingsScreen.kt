package com.example.zammad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zammad.core.L10n
import com.example.zammad.session.SessionStore

/** Language, appearance, connection and app info (iOS SettingsView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(session: SessionStore) {
    val prefs = session.prefs
    var confirmDisconnect by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("settings.title"), fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionTitle(L10n.t("settings.language"))
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

            Spacer(Modifier.height(14.dp))
            SectionTitle(L10n.t("settings.appearance"))
            SegmentedControl(
                options = listOf(
                    "system" to L10n.t("settings.appearance.system"),
                    "light" to L10n.t("settings.appearance.light"),
                    "dark" to L10n.t("settings.appearance.dark")
                ),
                selected = prefs.theme
            ) {
                prefs.theme = it
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle(L10n.t("settings.connection"))
            DetailCellRow(L10n.t("settings.server"), session.serverDisplay)
            DetailCellRow(
                L10n.t("settings.account"),
                session.currentUser?.displayName ?: "—"
            )
            DetailCellRow(
                L10n.t("settings.auth"),
                when (session.authMode) {
                    com.example.zammad.session.AuthMode.TOKEN -> L10n.t("connect.auth.token")
                    com.example.zammad.session.AuthMode.PASSWORD -> L10n.t("connect.auth.password")
                }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmDisconnect = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    " " + L10n.t("settings.disconnect"),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            SectionTitle(L10n.t("settings.about"))
            DetailCellRow(L10n.t("settings.version"), "1.0 (Android)")
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(L10n.t("settings.disconnect")) },
            text = { Text(L10n.t("settings.disconnect.question")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    session.disconnect()
                }) {
                    Text(
                        L10n.t("settings.disconnect"),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text(L10n.t("common.cancel"))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DetailCellRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
