import SwiftUI

@main
struct ZammadApp: App {
    @State private var prefs = AppPreferences()
    @State private var session = SessionStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(prefs)
                .environment(session)
                .preferredColorScheme(prefs.appearance.colorScheme)
        }
    }
}
