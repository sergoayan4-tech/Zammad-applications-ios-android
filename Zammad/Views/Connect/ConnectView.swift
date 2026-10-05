import SwiftUI

/// First screen: server address + credentials.
struct ConnectView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppPreferences.self) private var prefs

    @State private var server = ""
    @State private var mode: AuthMode = .token
    @State private var token = ""
    @State private var username = ""
    @State private var password = ""
    @State private var validationKey: String?

    var body: some View {
        NavigationStack {
            Form {
                headerSection
                serverSection
                authSection
                actionSection
            }
            .navigationTitle(prefs.t("connect.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                languageToolbar
            }
        }
        .onAppear(perform: prefill)
    }

    // MARK: - Sections

    private var headerSection: some View {
        Section {
            VStack(spacing: 10) {
                Image(systemName: "bubble.left.and.bubble.right.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(Color.accentColor)
                Text(prefs.t("connect.title"))
                    .font(.title3.bold())
                    .multilineTextAlignment(.center)
                Text(prefs.t("connect.subtitle"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .listRowBackground(Color.clear)
        }
    }

    private var serverSection: some View {
        Section {
            TextField(prefs.t("connect.server.placeholder"), text: $server)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.URL)
        } header: {
            Text(prefs.t("connect.server"))
        }
    }

    private var authSection: some View {
        Section {
            Picker(prefs.t("connect.auth"), selection: $mode) {
                Text(prefs.t("connect.auth.token")).tag(AuthMode.token)
                Text(prefs.t("connect.auth.password")).tag(AuthMode.password)
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            if mode == .token {
                SecureField(prefs.t("connect.token"), text: $token)
                    .textContentType(.password)
                Text(prefs.t("connect.token.hint"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                TextField(prefs.t("connect.username"), text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.emailAddress)
                    .textContentType(.username)
                SecureField(prefs.t("connect.password"), text: $password)
                    .textContentType(.password)
                Text(prefs.t("connect.password.hint"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text(prefs.t("connect.auth"))
        }
    }

    private var actionSection: some View {
        Section {
            Button {
                Task { await submit() }
            } label: {
                Group {
                    if session.phase == .connecting {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text(prefs.t("connect.connecting"))
                        }
                    } else {
                        Text(prefs.t("connect.submit"))
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 2)
            }
            .disabled(session.phase == .connecting)

            if let validationKey {
                Text(prefs.t(validationKey))
                    .font(.footnote)
                    .foregroundStyle(.red)
            }

            if let error = session.connectError {
                Text(prefs.message(for: error))
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var languageToolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                ForEach(AppLanguage.allCases) { language in
                    Button {
                        prefs.setLanguage(language)
                    } label: {
                        if language == prefs.language {
                            Label(prefs.label(for: language), systemImage: "checkmark")
                        } else {
                            Text(prefs.label(for: language))
                        }
                    }
                }
            } label: {
                Label(prefs.t("settings.language"), systemImage: "globe")
            }
        }
    }

    // MARK: - Actions

    private func prefill() {
        let saved = session.savedServerString
        guard !saved.isEmpty else { return }
        if server.isEmpty {
            server = saved
        }
        mode = session.savedMode
        if username.isEmpty {
            username = session.savedUsername
        }
    }

    private func submit() async {
        validationKey = nil

        guard let url = sanitizedServerURL(server) else {
            validationKey = "connect.error.url"
            return
        }
        switch mode {
        case .token where token.trimmed.isEmpty:
            validationKey = "connect.error.token"
            return
        case .password where username.trimmed.isEmpty || password.isEmpty:
            validationKey = "connect.error.credentials"
            return
        default:
            break
        }

        await session.connect(url: url, mode: mode, token: token, username: username, password: password)
    }
}
