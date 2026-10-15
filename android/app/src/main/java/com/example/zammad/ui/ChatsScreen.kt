package com.example.zammad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zammad.core.L10n
import com.example.zammad.core.relativeDate
import com.example.zammad.model.ChatSession
import com.example.zammad.session.SessionStore
import com.example.zammad.vm.ChatsModel

/** The "Chats" tab: live web-chat sessions (visitors and agent answers). */
@Composable
fun ChatsScreen(
    session: SessionStore,
    model: ChatsModel,
    onOpenSession: (ChatSession) -> Unit
) {
    LaunchedEffect(Unit) {
        model.connect(session.api)
    }
    DisposableEffect(Unit) {
        // The long-polling loop only runs while this tab is composed.
        onDispose { model.disconnect() }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            model.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            model.chatDisabled || model.noPermission -> Text(
                L10n.t(
                    if (model.chatDisabled) "chat.disabled" else "chat.noPermission"
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )

            !model.connected -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    L10n.t("error.network"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    model.disconnect()
                    model.connect(session.api)
                }) {
                    Text(L10n.t("common.retry"))
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                L10n.t("chat.agents"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${model.activeAgentCount}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { model.refresh() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = L10n.t("tickets.refresh"),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item { SectionLabel(L10n.t("chat.my")) }
                if (model.mySessions.isEmpty()) {
                    item { EmptyLine(L10n.t("chat.empty")) }
                }
                items(model.mySessions.size) { index ->
                    val chat = model.mySessions[index]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(chat) }
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                chat.url?.trim()?.takeIf { it.isNotEmpty() && it != "-" }
                                    ?: L10n.t("chat.guest"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            val last = chat.messages.lastOrNull()
                            val preview = last?.content?.trim().orEmpty()
                            Text(
                                if (preview.isEmpty()) relativeDate(chat.createdAt) else preview,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                item { SectionLabel(L10n.t("chat.waiting")) }
                if (model.waiting.isEmpty()) {
                    item { EmptyLine(L10n.t("chat.empty")) }
                }
                items(model.waiting.size) { index ->
                    val chat = model.waiting[index]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                chat.url?.trim()?.takeIf { it.isNotEmpty() && it != "-" }
                                    ?: L10n.t("chat.guest"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                relativeDate(chat.createdAt),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { model.answerNext() },
                        enabled = model.waiting.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(L10n.t("chat.answer"))
                    }
                }
            }
        }
    }
}

/** One live chat session: message bubbles and an input field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    model: ChatsModel,
    sessionId: String,
    onClose: () -> Unit
) {
    val chat = model.mySessions.firstOrNull { it.sessionId == sessionId }
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }

    LaunchedEffect(chat?.messages?.size) {
        val count = chat?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        L10n.t("chat.title"),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = L10n.t("common.close")
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        model.close(sessionId)
                        onClose()
                    }) {
                        Text(L10n.t("chat.close"))
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val messages = chat?.messages ?: emptyList()
                if (messages.isEmpty()) {
                    item {
                        Text(
                            L10n.t("ticket.noMessages"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                items(messages.size) { index ->
                    val message = messages[index]
                    val mine = message.createdByUserId != null &&
                        message.createdByUserId == chat?.userId
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            shape = if (mine) {
                                RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)
                            } else {
                                RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
                            },
                            color = if (mine) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                message.content.trim().ifEmpty { "…" },
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                color = if (mine) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 8.dp
                                )
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            relativeDate(message.createdAt),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(L10n.t("chat.placeholder")) },
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val value = text.trim()
                        if (value.isNotEmpty()) {
                            model.send(sessionId, value)
                            text = ""
                        }
                    },
                    enabled = text.trim().isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = L10n.t("reply.send"),
                        tint = if (text.trim().isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
