package com.example.zammad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zammad.core.L10n
import com.example.zammad.model.Ticket
import com.example.zammad.session.SessionStore
import com.example.zammad.vm.TicketFilter
import com.example.zammad.vm.TicketsListModel
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Manual pull-to-refresh state (no experimental Material APIs). */
private class PullRefreshState {
    var progress by mutableFloatStateOf(0f)
        private set
    var refreshing by mutableStateOf(false)
        private set

    fun drag(amount: Float) {
        if (refreshing) return
        progress = (progress + abs(amount) / 260f).coerceIn(0f, 1.3f)
    }

    fun release() {
        if (progress >= 1f && !refreshing) {
            refreshing = true
        } else {
            progress = 0f
        }
    }

    fun finish() {
        refreshing = false
        progress = 0f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(
    session: SessionStore,
    model: TicketsListModel,
    onOpen: (Ticket) -> Unit
) {
    val scope = rememberCoroutineScope()
    val myId = session.currentUser?.id
    val listState = rememberLazyListState()
    val pull = remember { PullRefreshState() }

    LaunchedEffect(Unit) {
        model.configure(session)
        model.loadIfNeeded()
    }

    LaunchedEffect(pull.refreshing) {
        if (pull.refreshing) {
            model.reload()
            pull.finish()
        }
    }

    val connection = remember(pull, listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val atTop =
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (source == NestedScrollSource.Drag && available.y != 0f && atTop) {
                    pull.drag(available.y)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pull.release()
                return Velocity.Zero
            }
        }
    }

    val visible = model.visibleTickets(myId)
    val showIndicator = pull.refreshing || pull.progress > 0f

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(L10n.t("tickets.title"), fontWeight = FontWeight.SemiBold) },
            actions = {
                IconButton(onClick = { scope.launch { model.reload() } }) {
                    Icon(Icons.Default.Refresh, contentDescription = L10n.t("tickets.refresh"))
                }
            }
        )

        OutlinedTextField(
            value = model.query,
            onValueChange = { model.setQuery(it) },
            placeholder = { Text(L10n.t("tickets.search")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (model.query.isNotEmpty()) {
                    IconButton(onClick = { model.setQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = L10n.t("common.close"))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(10.dp))
        SegmentedControl(
            options = TicketFilter.entries.map { it.key to L10n.t(it.key) },
            selected = model.filter.key,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) { value ->
            model.filter = TicketFilter.entries.first { it.key == value }
        }
        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                model.loading && visible.isEmpty() -> CenteredLoading()

                model.error != null && visible.isEmpty() -> ErrorBlock(
                    message = errorText(model.error) ?: L10n.t("error.unknown"),
                    onRetry = { scope.launch { model.reload() } }
                )

                visible.isEmpty() && model.isSearching -> EmptyBlock(
                    title = L10n.t("tickets.empty.search"),
                    hint = L10n.t("tickets.empty.search.hint")
                )

                visible.isEmpty() -> EmptyBlock(
                    title = L10n.t("tickets.empty"),
                    hint = L10n.t("tickets.empty.hint")
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .nestedScroll(connection),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { ticket ->
                        TicketRow(ticket = ticket, ref = session.reference) { onOpen(ticket) }
                    }
                    if (model.hasMore && !model.isSearching) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (model.loadingMore) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    androidx.compose.material3.TextButton(
                                        onClick = { scope.launch { model.loadMore() } }
                                    ) {
                                        Text(L10n.t("tickets.loadMore"))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showIndicator) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(24.dp)
                            .alpha(if (pull.refreshing) 1f else pull.progress.coerceIn(0.25f, 1f))
                    )
                }
            }

            val bannerError = if (visible.isNotEmpty()) errorText(model.error) else null
            if (bannerError != null) {
                Text(
                    text = bannerError,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
