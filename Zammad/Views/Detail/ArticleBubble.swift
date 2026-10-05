import SwiftUI
import UIKit

/// One message inside the ticket timeline.
struct ArticleBubble: View {
    let article: TicketArticle
    let prefs: AppPreferences
    var onAttachment: (ArticleAttachment) -> Void

    var body: some View {
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
                HTMLText(article.body ?? "", contentType: article.contentType)

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
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                .font(.caption)
                                .foregroundStyle(Color.accentColor)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(10)
            .background(bubbleColor, in: RoundedRectangle(cornerRadius: 14))
            .foregroundStyle(article.isFromSystem ? Color.secondary : Color.primary)
        }
        .frame(maxWidth: .infinity, alignment: frameAlignment)
    }

    // MARK: - Appearance

    private var alignment: HorizontalAlignment {
        article.isFromCustomer ? .leading : .trailing
    }

    private var frameAlignment: Alignment {
        article.isFromCustomer ? .leading : .trailing
    }

    private var author: String {
        let value = (article.createdBy ?? article.from ?? "").trimmed
        if value == "-" { return "" }
        return value
    }

    private var bubbleColor: Color {
        if article.isInternal == true {
            return Color.orange.opacity(0.15)
        }
        if article.isFromCustomer {
            return Color(uiColor: .secondarySystemBackground)
        }
        if article.isFromSystem {
            return Color(uiColor: .tertiarySystemBackground)
        }
        return Color.accentColor.opacity(0.12)
    }
}
