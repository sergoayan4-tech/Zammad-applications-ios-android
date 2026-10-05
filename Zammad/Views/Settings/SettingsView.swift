import SwiftUI

/// Language, appearance, connection and app info.
struct SettingsView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppPreferences.self) private var prefs

    @State private var confirmDisconnect = false

    var body: some View {
        Form {
            Section(prefs.t("settings.language")) {
                Picker(prefs.t("settings.language"), selection: languageBinding) {
                    ForEach(AppLanguage.allCases) { language in
                        Text(prefs.label(for: language)).tag(language)
                    }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }

            Section(prefs.t("settings.appearance")) {
                Picker(prefs.t("settings.appearance"), selection: appearanceBinding) {
                    Text(prefs.t("settings.appearance.system")).tag(AppearanceMode.system)
                    Text(prefs.t("settings.appearance.light")).tag(AppearanceMode.light)
                    Text(prefs.t("settings.appearance.dark")).tag(AppearanceMode.dark)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

            Section(prefs.t("settings.connection")) {
                LabeledContent(prefs.t("settings.server"), value: session.serverDisplay)
                LabeledContent(prefs.t("settings.account"), value: accountName)
                LabeledContent(prefs.t("settings.auth"), value: authName)

                Button(prefs.t("settings.disconnect"), role: .destructive) {
                    confirmDisconnect = true
                }
            }

            Section(prefs.t("settings.about")) {
                LabeledContent(prefs.t("settings.version"), value: appVersion)

                if let url = URL(string: "https://docs.zammad.org/en/latest/api/introduction.html") {
                    Link(prefs.t("settings.api"), destination: url)
                }
            }
        }
        .navigationTitle(prefs.t("settings.title"))
        .confirmationDialog(
            prefs.t("settings.disconnect.question"),
            isPresented: $confirmDisconnect,
            titleVisibility: .visible
        ) {
            Button(prefs.t("settings.disconnect"), role: .destructive) {
                session.disconnect()
            }
            Button(prefs.t("common.cancel"), role: .cancel) {}
        }
    }

    // MARK: - Values

    private var accountName: String {
        session.currentUser?.displayName ?? "—"
    }

    private var authName: String {
        session.authMode == .password
            ? prefs.t("connect.auth.password")
            : prefs.t("connect.auth.token")
    }

    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return "\(version) (\(build))"
    }

    private var languageBinding: Binding<AppLanguage> {
        Binding(
            get: { prefs.language },
            set: { prefs.setLanguage($0) }
        )
    }

    private var appearanceBinding: Binding<AppearanceMode> {
        Binding(
            get: { prefs.appearance },
            set: { prefs.setAppearance($0) }
        )
    }
}
