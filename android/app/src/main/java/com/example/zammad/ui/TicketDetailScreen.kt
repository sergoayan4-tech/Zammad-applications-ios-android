package com.example.zammad.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zammad.core.L10n
import com.example.zammad.core.dateTimeOf
import com.example.zammad.core.displayName
import com.example.zammad.core.groupNameOf
import com.example.zammad.core.isClosed
import com.example.zammad.core.ownerNameOf
import com.example.zammad.core.plainText
import com.example.zammad.core.priorityNameOf
import com.example.zammad.core.relativeDate
import com.example.zammad.core.stateNameOf
import com.example.zammad.model.ArticleAttachment
import com.example.zammad.model.TicketArticle
import com.example.zammad.session.SessionStore
import com.example.zammad.vm.TicketDetailModel
import kotlinx.coroutines.launch

/** Full-screen ticket detail: header, articles, composer, actions sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    session: SessionStore,
    model: TicketDetailModel,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        model.configure(session)
        model.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        model.ticket.displayNumber,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = L10n.t("common.close"))
                    }
                },
                actions = {
                    IconButton(onClick = { showActions = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = L10n.t("ticket.actions"))
                    }
                }
            )
        },
        bottomBar = {
            ReplyComposer(model = model, onSend = { scope.launch { model.send() } })
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            if (model.updating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val err = errorText(model.errorMessage)
            if (err != null && !model.updating) {
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            val ticket = model.ticket
            val ref = session.reference
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    ticket.displayNumber,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val state = stateNameOf(ticket, ref)
                                if (state.isNotEmpty()) StateChip(state)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                ticket.displayTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailCell(
                                    L10n.t("ticket.priority"),
                                    displayName(priorityNameOf(ticket, ref)),
                                    Modifier.weight(1f)
                                )
                                DetailCell(
                                    L10n.t("ticket.group"),
                                    displayName(groupNameOf(ticket, ref)),
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailCell(
                                    L10n.t("ticket.owner"),
                                    ownerNameOf(ticket),
                                    Modifier.weight(1f)
                                )
                                DetailCell(
                                    L10n.t("ticket.customer"),
                                    displayName(ticket.customer),
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailCell(
                                    L10n.t("ticket.organization"),
                                    displayName(ticket.organization),
                                    Modifier.weight(1f)
                                )
                                DetailCell(
                                    L10n.t("ticket.updated"),
                                    dateTimeOf(ticket.updatedAt ?: ticket.createdAt),
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailCell(
                                    L10n.t("ticket.created"),
                                    dateTimeOf(ticket.createdAt),
                                    Modifier.weight(1f)
                                )
                                Spacer(Modifier.weight(1f))
                            }
                            val ticketNote = ticket.note?.trim().orEmpty()
                            if (ticketNote.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Divider()
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    ticketNote,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            L10n.t("ticket.messages"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (model.loading) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                if (model.articles.isEmpty() && !model.loading) {
                    item {
                        Text(
                            L10n.t("ticket.noMessages"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                items(model.articles.size) { index ->
                    ArticleBubble(
                        article = model.articles[index],
                        session = session
                    )
                }
            }
        }
    }

    if (showActions) {
        TicketActionsSheet(
            session = session,
            model = model,
            onDismiss = { showActions = false }
        )
    }
}

/** One message bubble (customer left, agent right, internal highlighted). */
@Composable
private fun ArticleBubble(article: TicketArticle, session: SessionStore) {
    val fromAgent = !article.isFromCustomer && !article.isFromSystem
    val internal = article.isInternal == true
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromAgent) Alignment.End else Alignment.Start
    ) {
        val sender = displayName(article.createdBy ?: article.from)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                sender,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                " · " + relativeDate(article.createdAt),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(3.dp))
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (fromAgent) 14.dp else 4.dp,
                bottomEnd = if (fromAgent) 4.dp else 14.dp
            ),
            color = when {
                internal -> MaterialTheme.colorScheme.tertiaryContainer
                fromAgent -> MaterialTheme.colorScheme.primaryContainer
                article.isFromSystem -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                if (internal) {
                    Text(
                        L10n.t("article.internal"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                }
                val body = plainText(article.body)
                if (body.isNotEmpty()) {
                    Text(body, fontSize = 14.sp, lineHeight = 19.sp)
                }
                if (article.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    article.attachments.forEach { att ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(5.dp))
                            TextButton(
                                onClick = {
                                    downloadAttachment(context, session, article, att)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    att.filename,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun downloadAttachment(
    context: Context,
    session: SessionStore,
    article: TicketArticle,
    att: ArticleAttachment
) {
    try {
        val ticketId = article.ticketId
        if (ticketId == null) return
        val url = session.api.baseUrl.trimEnd('/') +
            "/api/v1/ticket_attachment/$ticketId/${article.id}/${att.id}"
        val request = DownloadManager.Request(Uri.parse(url))
        session.api.authHeader?.let { request.addRequestHeader("Authorization", it) }
        request.addRequestHeader("Accept", "*/*")
        request.setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        request.setMimeType(att.contentType ?: "application/octet-stream")
        var name = att.filename
        if (name.isBlank()) name = "attachment_${att.id}"
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(
            context,
            L10n.t("attachment.download") + " " + name,
            Toast.LENGTH_SHORT
        ).show()
    } catch (_: Exception) {
        Toast.makeText(context, L10n.t("error.unknown"), Toast.LENGTH_SHORT).show()
    }
}

/** Composer: text field + public/internal toggle + send. */
@Composable
private fun ReplyComposer(model: TicketDetailModel, onSend: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = model.replyText,
            onValueChange = { model.replyText = it },
            placeholder = { Text(L10n.t("reply.placeholder")) },
            minLines = 2,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SegmentedControl(
                options = listOf(
                    "pub" to L10n.t("reply.public"),
                    "int" to L10n.t("reply.internal")
                ),
                selected = if (model.isInternal) "int" else "pub",
                modifier = Modifier.weight(1f)
            ) {
                model.isInternal = it == "int"
            }
            IconButton(
                onClick = onSend,
                enabled = model.canSend
            ) {
                if (model.sending) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = L10n.t("reply.send"),
                        tint = if (model.canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (model.isInternal) {
            Spacer(Modifier.height(4.dp))
            Text(
                L10n.t("reply.internalHint"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
