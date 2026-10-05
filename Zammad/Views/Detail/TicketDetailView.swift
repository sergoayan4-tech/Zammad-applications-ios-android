import SwiftUI
import UIKit

/// Ticket header, message timeline and reply composer.
struct TicketDetailView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppPreferences.self) private var prefs

    @State private var model: TicketDetailModel
    @State private var showActions = false
    @State private var preview: AttachmentPreviewItem?

    init(ticket: Ticket) {
        _model = State(initialValue: TicketDetailModel(ticket: ticket))
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                header
                messagesHeader

                if model.loading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                } else if model.articles.isEmpty {
                    Text(prefs.t("ticket.noMessages"))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(model.articles) { article in
                        ArticleBubble(
                            article: article,
                            prefs: prefs,
                            onAttachment: { attachment in
                                download(attachment, from: article)
                            }
                        )
                    }
                }
            }
            .padding()
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle(model.ticket.displayNumber)
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom, spacing: 0) { composer }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showActions = true
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel(prefs.t("ticket.actions"))
            }
        }
        .overlay {
            if model.updating {
                ProgressView()
                    .padding(22)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
            }
        }
        .task {
            await model.load(api: session.api)
        }
        .sheet(isPresented: $showActions) {
            TicketActionsSheet(model: model)
        }
        .sheet(item: $preview) { item in
            QuickLookPreview(url: item.url)
        }
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(model.ticket.displayTitle)
                .font(.title3.weight(.semibold))

            if let note = model.ticket.note, !note.trimmed.isEmpty {
                Text(note)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            VStack(spacing: 10) {
                HStack(spacing: 10) {
                    DetailChip(title: prefs.t("ticket.state"), value: stateName)
                    DetailChip(title: prefs.t("ticket.priority"), value: priorityName)
                }
                HStack(spacing: 10) {
                    DetailChip(title: prefs.t("ticket.group"), value: groupName)
                    DetailChip(title: prefs.t("ticket.owner"), value: ownerName)
                }
                HStack(spacing: 10) {
                    DetailChip(title: prefs.t("ticket.customer"), value: displayName(model.ticket.customer))
                    DetailChip(title: prefs.t("ticket.organization"), value: displayName(model.ticket.organization))
                }
                HStack(spacing: 10) {
                    DetailChip(title: prefs.t("ticket.created"), value: prefs.dateTime(model.ticket.createdAt))
                    DetailChip(title: prefs.t("ticket.updated"), value: prefs.dateTime(model.ticket.updatedAt))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(
            Color(uiColor: .secondarySystemGroupedBackground),
            in: RoundedRectangle(cornerRadius: 16)
        )
    }

    private var messagesHeader: some View {
        HStack {
            Text(prefs.t("ticket.messages"))
                .font(.subheadline.weight(.semibold))
            Spacer()
            Text("\(model.articles.count)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Composer

    private var composer: some View {
        VStack(spacing: 8) {
            if let error = model.errorMessage {
                Text(prefs.message(for: error))
                    .font(.caption)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Picker("", selection: $model.isInternal) {
                Text(prefs.t("reply.public")).tag(false)
                Text(prefs.t("reply.internal")).tag(true)
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            HStack(alignment: .bottom, spacing: 10) {
                TextField(prefs.t("reply.placeholder"), text: $model.replyText, axis: .vertical)
                    .lineLimit(1...6)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(
                        Color(uiColor: .secondarySystemBackground),
                        in: RoundedRectangle(cornerRadius: 14)
                    )

                Button {
                    Task { await model.send(api: session.api) }
                } label: {
                    if model.sending {
                        ProgressView()
                            .frame(width: 30, height: 30)
                    } else {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.system(size: 32))
                    }
                }
                .disabled(!model.canSend)
            }

            if model.isInternal {
                Text(prefs.t("reply.internalHint"))
                    .font(.caption2)
                    .foregroundStyle(.orange)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.bar)
    }

    // MARK: - Helpers

    private func download(_ attachment: ArticleAttachment, from article: TicketArticle) {
        Task {
            do {
                let url = try await model.downloadAttachment(
                    attachment,
                    article: article,
                    api: session.api
                )
                preview = AttachmentPreviewItem(url: url)
            } catch {
                model.errorMessage = error
            }
        }
    }

    private func displayName(_ value: String?) -> String {
        guard let value, !value.trimmed.isEmpty, value != "-" else { return "—" }
        return value
    }

    private var stateName: String {
        ticketStateName(model.ticket, session.reference)
    }

    private var priorityName: String {
        ticketPriorityName(model.ticket, session.reference)
    }

    private var groupName: String {
        ticketGroupName(model.ticket, session.reference)
    }

    private var ownerName: String {
        ticketOwnerName(model.ticket)
    }
}
