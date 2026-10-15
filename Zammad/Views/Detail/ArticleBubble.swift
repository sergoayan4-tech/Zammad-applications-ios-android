import SwiftUI
import UIKit

/// One message inside the ticket chat, styled like the Zammad web UI:
/// customer messages on the left (gray), agent messages on the right
/// (accent-colored), internal notes highlighted, system entries centered.
struct ArticleBubble: View {
    let article: TicketArticle
    let prefs: AppPreferences
    var onAttachment: (ArticleAttachment) -> Void

    var body: some View {
        if article.isFromSystem {
            systemLine
        } else {
            message
        }
    }

    // MARK: - Message

    private var message: some View {
        HStack {
            if !isMine { Spacer(minLength: 44) }

            VStack(alignment: alignment, spacing: 5) {
                HStack(spacing: 6) {
                    if !author.isEmpty {
                        Text(author)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                    }
                    if article.isInternal == true {
                        Text(prefs.t("article.internal"))
                            .font(.caption2.weight(.bold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.orange.opacity(0.25), in: Capsule())
                            .foregroundStyle(Color.orange)
                    }
                    Text(prefs.relativeDate(article.createdAt))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 8) {
                    HTMLText(article.body ?? "", contentType: article.contentType, tint: textColor)

                    if let attachments = article.attachments, !attachments.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(attachments) { attachment in
                                Button {
                                    onAttachment(attachment)
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: attachmentIcon(for: attachment.fileExtension))
                                        Text(attachment.filename)
                                            .lineLimit(1)
                                        if let size = attachment.sizeText {
                                            Text(size)
                                                .font(.caption2)
                                                .opacity(0.75)
                                        }
                                    }
                                    .font(.caption)
                                    .foregroundStyle(linkColor)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(10)
                .background(bubbleColor, in: RoundedRectangle(cornerRadius: 16))
                .foregroundStyle(textColor)
                // Chat proportions: the bubble never spans the whole row.
                .frame(maxWidth: 320, alignment: .leading)
            }

            if isMine { Spacer(minLength: 44) }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - System entries (centered gray line, like Zammad web)

    private var systemLine: some View {
        VStack(spacing: 2) {
            Text(prefs.relativeDate(article.createdAt))
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(Self.plainBody(article.body ?? ""))
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 2)
    }

    // MARK: - Appearance

    private var alignment: HorizontalAlignment {
        article.isFromCustomer ? .leading : .trailing
    }

    private var isMine: Bool {
        !article.isFromCustomer && !article.isFromSystem
    }

    private var author: String {
        let value = (article.createdBy ?? article.from ?? "").trimmed
        if value == "-" { return "" }
        return value
    }

    private var bubbleColor: Color {
        if article.isInternal == true {
            return Color.orange.opacity(0.18)
        }
        if article.isFromCustomer {
            return Color(uiColor: .secondarySystemBackground)
        }
        return Color.accentColor
    }

    private var textColor: Color {
        if article.isInternal == true {
            return .primary
        }
        return isMine ? .white : .primary
    }

    private var linkColor: Color {
        isMine && article.isInternal != true ? Color.white : Color.accentColor
    }

    /// Strips HTML tags for the compact system line.
    private static func plainBody(_ body: String) -> String {
        var value = body
            .replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
            .replacingOccurrences(of: "</p\\s*>", with: "\n", options: .regularExpression)
        value = value.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        return value.trimmed
    }
}
