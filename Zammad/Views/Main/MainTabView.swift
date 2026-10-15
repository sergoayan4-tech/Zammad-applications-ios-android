import SwiftUI

/// Four tabs: tickets, chats, new ticket, settings.
struct MainTabView: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(SessionStore.self) private var session

    @State private var selection = 0
    @State private var path = NavigationPath()
    @State private var chatsModel = ChatsModel()

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
                ChatsListView(model: chatsModel)
                    .navigationDestination(for: ChatSession.self) { chat in
                        ChatSessionView(model: chatsModel, sessionId: chat.sessionId ?? "")
                    }
            }
            .tabItem {
                Label(prefs.t("tab.chats"), systemImage: "bubble.left.and.bubble.right")
            }
            .tag(1)

            NavigationStack {
                NewTicketView { ticket in
                    selection = 0
                    path.append(ticket)
                }
            }
            .tabItem {
                Label(prefs.t("tab.new"), systemImage: "square.and.pencil")
            }
            .tag(2)

            NavigationStack {
                SettingsView()
            }
            .tabItem {
                Label(prefs.t("tab.settings"), systemImage: "gearshape")
            }
            .tag(3)
        }
        .onChange(of: selection) { _, newValue in
            // The long-polling loop only runs while the Chats tab is open.
            if newValue == 1 {
                chatsModel.connect(api: session.api)
            } else {
                chatsModel.disconnect()
            }
        }
    }
}
