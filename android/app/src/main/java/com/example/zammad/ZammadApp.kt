package com.example.zammad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.zammad.core.L10n
import com.example.zammad.model.Ticket
import com.example.zammad.session.ConnectPhase
import com.example.zammad.session.SessionStore
import com.example.zammad.ui.ConnectScreen
import com.example.zammad.ui.NewTicketScreen
import com.example.zammad.ui.SettingsScreen
import com.example.zammad.ui.TicketActionsSheet
import com.example.zammad.ui.TicketDetailScreen
import com.example.zammad.ui.TicketsScreen
import com.example.zammad.vm.TicketDetailModel
import com.example.zammad.vm.TicketsListModel
import kotlinx.coroutines.launch

/** App root: splash → connect screen → main tabs (iOS RootView + MainTabView). */
@Composable
fun ZammadApp(session: SessionStore) {
    val prefs = session.prefs
    val darkTheme = when (prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    var booted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session.restore()
        booted = true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) {
            androidx.compose.material3.darkColorScheme()
        } else {
            androidx.compose.material3.lightColorScheme()
        }
    ) {
        when {
            !booted && session.phase == ConnectPhase.CONNECTING -> SplashScreen()
            !booted && session.prefs.serverUrl.isNotEmpty() &&
                (session.prefs.token.isNotEmpty() || session.prefs.login.isNotEmpty()) -> SplashScreen()
            !session.connected -> ConnectScreen(session)
            else -> MainContent(session)
        }
    }
}

@Composable
private fun SplashScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Email,
            contentDescription = null,
            modifier = Modifier.size(46.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(14.dp))
        Text("Zammad", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        CircularProgressIndicator(Modifier.size(30.dp))
    }
}

@Composable
private fun MainContent(session: SessionStore) {
    val scope = rememberCoroutineScope()
    val listModel: TicketsListModel = viewModel()
    var tab by remember { mutableStateOf(0) }
    var openTicket by remember { mutableStateOf<Ticket?>(null) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Email, contentDescription = null) },
                        label = { Text(L10n.t("tab.tickets")) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Create, contentDescription = null) },
                        label = { Text(L10n.t("tab.new")) }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(L10n.t("tab.settings")) }
                    )
                }
            }
        ) { pad ->
            Box(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
            ) {
                when (tab) {
                    0 -> TicketsScreen(session, listModel) { openTicket = it }
                    1 -> NewTicketScreen(session) { created ->
                        openTicket = created
                        tab = 0
                        scope.launch { listModel.reload() }
                    }
                    2 -> SettingsScreen(session)
                }
            }
        }

        openTicket?.let { ticket ->
            val model = remember(ticket.id) { TicketDetailModel(ticket) }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TicketDetailScreen(
                    session = session,
                    model = model,
                    onClose = { openTicket = null }
                )
            }
        }
    }
}
