import SwiftUI

/// The "Chats" tab: live web-chat sessions (visitors and agent answers),
/// mirroring the Chats section of the Zammad web UI.
struct ChatsListView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppPreferences.self) private var prefs

    let model: ChatsModel

    var body: some View {
        List {
            if model.chatDisabled {
                Text(prefs.t("chat.disabled"))
                    .foregroundStyle(.secondary)
            } else if model.noPermission {
                Text(prefs.t("chat.noPermission"))
                    .foregroundStyle(.secondary)
            } else {
                Section {
                    HStack {
                        Label {
                            Text(prefs.t("chat.agents"))
                        } icon: {
                            Image(systemName: "person.2")
                        }
                        Spacer()
                        Text("\(model.activeAgentCount)")
                            .foregroundStyle(.secondary)
                    }
                }

                Section(prefs.t("chat.my")) {
                    if model.mySessions.isEmpty {
                        Text(prefs.t("chat.empty"))
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(model.mySessions) { chat in
                            NavigationLink(value: chat) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(sessionLabel(chat))
                                        .font(.subheadline.weight(.medium))
                                    Text(rowSubtitle(chat))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                            }
                        }
                    }
                }

                Section(prefs.t("chat.waiting")) {
                    if model.waiting.isEmpty {
                        Text(prefs.t("chat.empty"))
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(model.waiting) { chat in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(guestLabel(chat))
                                    .font(.subheadline.weight(.medium))
                                Text(prefs.relativeDate(chat.createdAt))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }

                    Button(prefs.t("chat.answer")) {
                        model.answerNext()
                    }
                    .disabled(model.waiting.isEmpty)
                }
            }
        }
        .navigationTitle(prefs.t("chat.title"))
        .refreshable {
            model.refresh()
        }
        .overlay {
            if model.loading && !model.chatDisabled && !model.noPermission {
                ProgressView()
            }
            if !model.loading && !model.connected && !model.chatDisabled && !model.noPermission {
                VStack(spacing: 10) {
                    Text(prefs.t("error.network"))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Button(prefs.t("common.retry")) {
                        model.disconnect()
                        model.connect(api: session.api)
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }

    // MARK: - Labels

    private func sessionLabel(_ chat: ChatSession) -> String {
        if let url = chat.url?.trimmed, !url.isEmpty, url != "-" {
            return url
        }
        return prefs.t("chat.guest")
    }

    private func rowSubtitle(_ chat: ChatSession) -> String {
        let time = prefs.relativeDate(chat.createdAt)
        guard let last = chat.messages.last else { return time }
        let preview = last.content.trimmed
        return preview.isEmpty ? time : preview
    }

    private func guestLabel(_ chat: ChatSession) -> String {
        if let url = chat.url?.trimmed, !url.isEmpty, url != "-" {
            return url
        }
        return prefs.t("chat.guest") + " · " + prefs.relativeDate(chat.createdAt)
    }
}
