import Foundation
import Observation

/// Quick filters above the ticket list.
enum TicketFilter: String, CaseIterable, Identifiable {
    case all
    case open
    case mine
    case closed

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
    var filter: TicketFilter = .all
    var error: Error?

    private var page = 1
    private var loadedOnce = false
    private var api: APIClient?
    private var searchTask: Task<Void, Never>?

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
        guard let api else { return }
        loading = true
        error = nil

        do {
            let fresh: [Ticket] = try await api.get("/api/v1/tickets", query: Self.pageQuery(1))
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
        guard let api, hasMore, !loadingMore, !loading else { return }
        loadingMore = true
        error = nil

        do {
            let fresh: [Ticket] = try await api.get("/api/v1/tickets", query: Self.pageQuery(page))
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
                filtered = base.filter { $0.ownerId == myID }
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
