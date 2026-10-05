import SwiftUI

/// Searchable list of tickets with quick filters and pagination.
struct TicketsListView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @State private var model = TicketsListModel()

    var body: some View {
        content
            .navigationTitle(prefs.t("tickets.title"))
            .searchable(text: queryBinding, prompt: prefs.t("tickets.search"))
            .refreshable { await model.reload() }
            .task {
                model.configure(api: session.api)
                await model.loadIfNeeded()
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await model.reload() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .disabled(model.loading)
                    .accessibilityLabel(prefs.t("tickets.refresh"))
                }
            }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        if model.loading && model.tickets.isEmpty {
            LoadingView(title: prefs.t("common.loading"))
        } else if let error = model.error, model.tickets.isEmpty, !model.loading {
            ErrorRetryView(
                message: prefs.message(for: error),
                retryTitle: prefs.t("common.retry")
            ) {
                Task { await model.reload() }
            }
        } else if visibleTickets.isEmpty {
            emptyState
        } else {
            list
        }
    }

    private var list: some View {
        List {
            Section {
                filterPicker
            }

            Section {
                ForEach(visibleTickets) { ticket in
                    NavigationLink(value: ticket) {
                        TicketRow(ticket: ticket, reference: session.reference, prefs: prefs)
                    }
                }
            }

            if model.hasMore && !model.isSearching {
                Section {
                    Button {
                        Task { await model.loadMore() }
                    } label: {
                        if model.loadingMore {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Text(prefs.t("tickets.loadMore"))
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(model.loadingMore)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var filterPicker: some View {
        Picker("", selection: filterBinding) {
            ForEach(TicketFilter.allCases) { filter in
                Text(prefs.t(filter.titleKey)).tag(filter)
            }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        .textCase(nil)
    }

    @ViewBuilder
    private var emptyState: some View {
        if model.isSearching || !model.query.trimmed.isEmpty {
            ContentUnavailableView(
                prefs.t("tickets.empty.search"),
                systemImage: "magnifyingglass",
                description: Text(prefs.t("tickets.empty.search.hint"))
            )
        } else {
            ContentUnavailableView(
                prefs.t("tickets.empty"),
                systemImage: "tray",
                description: Text(prefs.t("tickets.empty.hint"))
            )
        }
    }

    // MARK: - Bindings

    private var visibleTickets: [Ticket] {
        model.visibleTickets(myID: session.currentUser?.id)
    }

    private var filterBinding: Binding<TicketFilter> {
        Binding(
            get: { model.filter },
            set: { model.filter = $0 }
        )
    }

    private var queryBinding: Binding<String> {
        Binding(
            get: { model.query },
            set: { model.setQuery($0) }
        )
    }
}
