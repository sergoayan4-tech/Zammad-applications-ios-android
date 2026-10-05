import Foundation
import Observation

/// Quick filters above the ticket list.
enum TicketFilter: String, CaseIterable, Identifiable {
    case open
    case closed
    case all
    case mine

    var id: String { rawValue }

    var titleKey: String {
        switch self {
        case .all: return "tickets.filter.all"
        case .open: return "tickets.filter.open"
        case .mine: return "tickets.filter.mine"
        case .closed: return "tickets.filter.closed"
        }
    }
}

/// Endpoint that currently serves the ticket list.
///
/// The plain `GET /api/v1/tickets` index has **no server side sorting** — it
/// returns tickets oldest first (confirmed by the Zammad maintainers), so on a
/// busy instance page 1 is a batch of long-closed tickets and newer open ones
/// never make it onto the screen.
///
/// `GET /api/v1/tickets/search` does support `sort_by`/`order_by`, so the list
/// prefers it with `query=*` + `sort_by=updated_at&order_by=desc` (newest
/// first, like the web UI). Backends differ: Elasticsearch installs need
/// `query=*`, plain SQL installs return everything for an empty query — hence
/// the fallback chain in `reload()`.
private enum ListSource {
    case searchAll
    case searchEmpty
    case index
}

/// Backs the ticket list: loading, pagination, search and filters.
@MainActor
@Observable
final class TicketsListModel {
    private(set) var tickets: [Ticket] = []
    private(set) var searchResults: [Ticket]?
    private(set) var loading = false
    private(set) var loadingMore = false
    private(set) var hasMore = false
    private(set) var searching = false
    private(set) var query = ""
    var filter: TicketFilter = .open
    var error: Error?

    private var page = 1
    private var loadedOnce = false
    private var api: APIClient?
    private var searchTask: Task<Void, Never>?
    private var listSource: ListSource = .searchAll

    private static let pageSize = 100

    func configure(api: APIClient?) {
        self.api = api
    }

    /// True when the list is showing server side search results.
    var isSearching: Bool {
        searchResults != nil
    }

    func loadIfNeeded() async {
        guard !loadedOnce else { return }
        await reload()
    }

    func reload() async {
        guard api != nil else { return }
        loading = true
        error = nil

        do {
            // Newest first: the plain index cannot be sorted, so go through
            // /tickets/search. Empty results advance the fallback chain; only
            // the last step (plain index) surfaces its errors to the UI.
            listSource = .searchAll
            var fresh = (try? await fetchSearchPage(1, query: "*")) ?? []
            if fresh.isEmpty {
                listSource = .searchEmpty
                fresh = (try? await fetchSearchPage(1, query: "")) ?? []
            }
            if fresh.isEmpty {
                listSource = .index
                fresh = try await fetchIndexPage(1)
            }

            tickets = fresh
            hasMore = fresh.count >= Self.pageSize
            page = 2
            loadedOnce = true
        } catch let loadError {
            error = loadError
        }
        loading = false
    }

    func loadMore() async {
        guard api != nil, hasMore, !loadingMore, !loading else { return }
        loadingMore = true
        error = nil

        do {
            let fresh: [Ticket]
            switch listSource {
            case .searchAll:
                fresh = try await fetchSearchPage(page, query: "*")
            case .searchEmpty:
                fresh = try await fetchSearchPage(page, query: "")
            case .index:
                fresh = try await fetchIndexPage(page)
            }

            let known = Set(tickets.map(\.id))
            let newItems = fresh.filter { !known.contains($0.id) }
            tickets.append(contentsOf: newItems)
            // Stops when the server ignores pagination and returns the same page.
            hasMore = !newItems.isEmpty
            page += 1
        } catch let loadError {
            error = loadError
        }
        loadingMore = false
    }

    /// Debounced server side search (`/api/v1/tickets/search`).
    func setQuery(_ value: String) {
        query = value
        searchTask?.cancel()

        let trimmed = value.trimmed
        guard trimmed.count >= 2 else {
            searchResults = nil
            searching = false
            return
        }

        searching = true
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            await self?.performSearch(trimmed)
        }
    }

    /// Tickets currently visible for the selected filter.
    func visibleTickets(myID: Int?) -> [Ticket] {
        let base = searchResults ?? tickets
        let filtered: [Ticket]

        switch filter {
        case .all:
            filtered = base
        case .mine:
            if let myID {
                // "Mine" means open tickets assigned to me (as requested);
                // my closed ones are still visible under the "Closed" tab.
                filtered = base.filter { $0.ownerId == myID && !Self.isClosed($0) }
            } else {
                filtered = []
            }
        case .open:
            filtered = base.filter { !Self.isClosed($0) }
        case .closed:
            filtered = base.filter { Self.isClosed($0) }
        }

        return filtered.sorted { lhs, rhs in
            let left = lhs.updatedAt ?? lhs.createdAt ?? .distantPast
            let right = rhs.updatedAt ?? rhs.createdAt ?? .distantPast
            return left > right
        }
    }

    // MARK: - Private

    private func performSearch(_ text: String) async {
        guard let api else {
            searching = false
            return
        }

        do {
            let results: [Ticket] = try await api.get(
                "/api/v1/tickets/search",
                query: [
                    URLQueryItem(name: "query", value: text),
                    URLQueryItem(name: "expand", value: "true"),
                    URLQueryItem(name: "per_page", value: String(Self.pageSize))
                ]
            )
            searchResults = results
            error = nil
        } catch {
            // Fall back to filtering the tickets we already loaded.
            searchResults = nil
        }
        searching = false
    }

    /// One page of tickets sorted newest first via the search endpoint.
    private func fetchSearchPage(_ page: Int, query: String) async throws -> [Ticket] {
        guard let api else { return [] }
        return try await api.get("/api/v1/tickets/search", query: [
            URLQueryItem(name: "query", value: query),
            URLQueryItem(name: "sort_by", value: "updated_at"),
            URLQueryItem(name: "order_by", value: "desc"),
            URLQueryItem(name: "expand", value: "true"),
            URLQueryItem(name: "per_page", value: String(Self.pageSize)),
            URLQueryItem(name: "page", value: String(page))
        ])
    }

    /// Last resort: the plain index, which cannot be sorted server side.
    private func fetchIndexPage(_ page: Int) async throws -> [Ticket] {
        guard let api else { return [] }
        return try await api.get("/api/v1/tickets", query: Self.pageQuery(page))
    }

    private static func pageQuery(_ page: Int) -> [URLQueryItem] {
        [
            URLQueryItem(name: "expand", value: "true"),
            URLQueryItem(name: "per_page", value: String(pageSize)),
            URLQueryItem(name: "page", value: String(page))
        ]
    }

    private static func isClosed(_ ticket: Ticket) -> Bool {
        let name = ticket.state?.lowercased() ?? ""
        guard !name.isEmpty else { return false }
        return isClosedStateName(name)
    }
}
