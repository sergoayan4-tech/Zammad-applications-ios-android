import SwiftUI
import UIKit

/// One live chat session: message bubbles and an input field.
struct ChatSessionView: View {
    @Environment(AppPreferences.self) private var prefs

    let model: ChatsModel
    let sessionId: String

    @State private var text = ""

    private var session: ChatSession? {
        model.mySessions.first { $0.sessionId == sessionId }
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 10) {
                    if let session {
                        if session.messages.isEmpty {
                            Text(prefs.t("ticket.noMessages"))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        ForEach(session.messages) { message in
                            bubble(message, mine: message.createdByUserId == session.userId)
                                .id(message.id)
                        }
                    } else {
                        Text(prefs.t("chat.empty"))
                            .foregroundStyle(.secondary)
                    }
                }
                .padding()
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color(uiColor: .systemGroupedBackground))
            .safeAreaInset(edge: .bottom, spacing: 0) { composer }
            .navigationTitle(prefs.t("chat.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(prefs.t("chat.close")) {
                        model.close(sessionId: sessionId)
                    }
                }
            }
            .onChange(of: session?.messages.count ?? 0) { _, _ in
                if let last = session?.messages.last {
                    withAnimation {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    // MARK: - Bubble

    private func bubble(_ message: ChatMessage, mine: Bool) -> some View {
        HStack {
            if !mine { Spacer(minLength: 44) }

            VStack(alignment: mine ? .trailing : .leading, spacing: 3) {
                Text(message.content.trimmed.isEmpty ? "…" : message.content.trimmed)
                    .font(.subheadline)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(
                        mine ? Color.accentColor : Color(uiColor: .secondarySystemBackground),
                        in: RoundedRectangle(cornerRadius: 16)
                    )
                    .foregroundStyle(mine ? Color.white : Color.primary)

                Text(prefs.relativeDate(message.createdAt))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            if mine { Spacer(minLength: 44) }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Composer

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField(prefs.t("chat.placeholder"), text: $text, axis: .vertical)
                .lineLimit(1...5)
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(
                    Color(uiColor: .secondarySystemBackground),
                    in: RoundedRectangle(cornerRadius: 14)
                )

            Button {
                let value = text.trimmed
                guard !value.isEmpty else { return }
                model.send(sessionId: sessionId, content: value)
                text = ""
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 30))
            }
            .disabled(text.trimmed.isEmpty)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.bar)
    }
}
