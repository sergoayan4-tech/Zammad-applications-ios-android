import SwiftUI

/// Three tabs: tickets, new ticket, settings.
struct MainTabView: View {
    @Environment(AppPreferences.self) private var prefs

    @State private var selection = 0
    @State private var path = NavigationPath()

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack(path: $path) {
                TicketsListView()
                    .navigationDestination(for: Ticket.self) { ticket in
                        TicketDetailView(ticket: ticket)
                    }
            }
            .tabItem {
                Label(prefs.t("tab.tickets"), systemImage: "tray.full")
            }
            .tag(0)

            NavigationStack {
                NewTicketView { ticket in
                    selection = 0
                    path.append(ticket)
                }
            }
            .tabItem {
                Label(prefs.t("tab.new"), systemImage: "square.and.pencil")
            }
            .tag(1)

            NavigationStack {
                SettingsView()
            }
            .tabItem {
                Label(prefs.t("tab.settings"), systemImage: "gearshape")
            }
            .tag(2)
        }
    }
}
