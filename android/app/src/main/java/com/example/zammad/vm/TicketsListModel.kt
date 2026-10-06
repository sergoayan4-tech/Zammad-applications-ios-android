package com.example.zammad.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zammad.api.ApiClient
import com.example.zammad.api.ApiErrorKind
import com.example.zammad.api.ApiException
import com.example.zammad.core.isClosed
import com.example.zammad.model.Ticket
import com.example.zammad.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Filter tabs, in the order they appear in the UI. */
enum class TicketFilter(val key: String) {
    OPEN("tickets.filter.open"),
    CLOSED("tickets.filter.closed"),
    ALL("tickets.filter.all"),
    MINE("tickets.filter.mine")
}

private enum class ListSource { SEARCH_ALL, SEARCH_EMPTY, INDEX }

/** Backs the ticket list: paging, filters, search and pull-to-refresh. */
class TicketsListModel : ViewModel() {

    var tickets by mutableStateOf<List<Ticket>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<Ticket>?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var searching by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var filter by mutableStateOf(TicketFilter.OPEN)
    var error by mutableStateOf<ApiException?>(null)
        private set

    val isSearching: Boolean get() = searchResults != null

    private var page = 1
    private var loadedOnce = false
    private var session: SessionStore? = null
    private var searchJob: Job? = null
    private var listSource = ListSource.SEARCH_ALL

    fun configure(session: SessionStore) {
        this.session = session
    }

    suspend fun loadIfNeeded() {
        if (loadedOnce) return
        reload()
    }

    suspend fun reload() {
        val s = session ?: return
        loading = true
        error = null
        try {
            listSource = ListSource.SEARCH_ALL
            var fresh = try {
                fetchSearch(s.api, 1, "*")
            } catch (_: ApiException) {
                emptyList()
            }
            if (fresh.isEmpty()) {
                listSource = ListSource.SEARCH_EMPTY
                fresh = try {
                    fetchSearch(s.api, 1, "")
                } catch (_: ApiException) {
                    emptyList()
                }
            }
            if (fresh.isEmpty()) {
                listSource = ListSource.INDEX
                fresh = fetchIndex(s.api, 1)
            }
            tickets = fresh
            hasMore = fresh.size >= PAGE_SIZE
            page = 2
            loadedOnce = true
        } catch (e: ApiException) {
            error = e
        } catch (e: Exception) {
            error = ApiException(ApiErrorKind.UNKNOWN, e.message ?: "error")
        } finally {
            loading = false
        }
    }

    suspend fun loadMore() {
        val s = session ?: return
        if (!hasMore || loadingMore || loading) return
        loadingMore = true
        error = null
        try {
            val fresh = when (listSource) {
                ListSource.SEARCH_ALL -> fetchSearch(s.api, page, "*")
                ListSource.SEARCH_EMPTY -> fetchSearch(s.api, page, "")
                ListSource.INDEX -> fetchIndex(s.api, page)
            }
            val known = tickets.map { it.id }.toSet()
            val added = fresh.filter { it.id !in known }
            tickets = tickets + added
            hasMore = added.isNotEmpty()
            page += 1
        } catch (e: ApiException) {
            error = e
        } catch (e: Exception) {
            error = ApiException(ApiErrorKind.UNKNOWN, e.message ?: "error")
        } finally {
            loadingMore = false
        }
    }

    /** Debounced server-side search (mirrors iOS `setSearchQuery`). */
    fun setQuery(value: String) {
        query = value
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.length < 2) {
            searchResults = null
            searching = false
            return
        }
        searching = true
        searchJob = viewModelScope.launch {
            delay(350)
            performSearch(trimmed)
        }
    }

    private suspend fun performSearch(text: String) {
        val s = session
        if (s == null) {
            searching = false
            return
        }
        searchResults = try {
            withContext(Dispatchers.IO) {
                s.api.getArray(
                    "/api/v1/tickets/search",
                    listOf("query" to text, "expand" to "true", "per_page" to "$PAGE_SIZE")
                ).map { Ticket.fromJson(it) }
            }
        } catch (_: Exception) {
            null
        }
        searching = false
    }

    /** Applies the active filter to the loaded (or searched) tickets. */
    fun visibleTickets(myId: Int?): List<Ticket> {
        val base = searchResults ?: tickets
        val filtered = when (filter) {
            TicketFilter.ALL -> base
            TicketFilter.MINE ->
                if (myId != null) base.filter { it.ownerId == myId && !isClosed(it) } else emptyList()
            TicketFilter.OPEN -> base.filter { !isClosed(it) }
            TicketFilter.CLOSED -> base.filter { isClosed(it) }
        }
        return filtered.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
    }

    private suspend fun fetchSearch(api: ApiClient, page: Int, q: String): List<Ticket> =
        withContext(Dispatchers.IO) {
            api.getArray(
                "/api/v1/tickets/search",
                listOf(
                    "query" to q,
                    "sort_by" to "updated_at",
                    "order_by" to "desc",
                    "expand" to "true",
                    "per_page" to "$PAGE_SIZE",
                    "page" to "$page"
                )
            ).map { Ticket.fromJson(it) }
        }

    private suspend fun fetchIndex(api: ApiClient, page: Int): List<Ticket> =
        withContext(Dispatchers.IO) {
            api.getArray(
                "/api/v1/tickets",
                listOf("expand" to "true", "per_page" to "$PAGE_SIZE", "page" to "$page")
            ).map { Ticket.fromJson(it) }
        }

    companion object {
        private const val PAGE_SIZE = 100
    }
}
