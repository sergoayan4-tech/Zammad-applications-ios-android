import SwiftUI

/// Decides which top level screen to show depending on the session state.
struct RootView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppPreferences.self) private var prefs

    var body: some View {
        content
            .animation(.easeInOut(duration: 0.2), value: session.phase)
            .task {
                await session.restore()
            }
    }

    @ViewBuilder
    private var content: some View {
        switch session.phase {
        case .restoring:
            splash
        case .signedOut, .connecting:
            ConnectView()
        case .connected:
            MainTabView()
        }
    }

    private var splash: some View {
        VStack(spacing: 14) {
            Image(systemName: "bubble.left.and.bubble.right.fill")
                .font(.system(size: 46))
                .foregroundStyle(Color.accentColor)
            Text("Zammad")
                .font(.title2.bold())
            ProgressView()
            Text(prefs.t("common.loading"))
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}
