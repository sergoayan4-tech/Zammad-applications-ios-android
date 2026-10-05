import SwiftUI
import QuickLook

/// Small rounded label used for ticket state.
struct StateChip: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .lineLimit(1)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(tint.opacity(0.16), in: Capsule())
            .foregroundStyle(tint)
    }

    private var tint: Color {
        let name = text.lowercased()
        if name.contains("closed") || name.contains("removed") || name.contains("merged") {
            return .gray
        }
        if name.contains("pending") {
            return .orange
        }
        if name.contains("open") {
            return .green
        }
        if name.contains("new") {
            return .blue
        }
        return .secondary
    }
}

/// Compact key/value label used in the ticket header.
struct DetailChip: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(value.isEmpty ? "—" : value)
                .font(.callout)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Centered progress indicator.
struct LoadingView: View {
    let title: String

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(title)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Centered error with a retry button.
struct ErrorRetryView: View {
    let message: String
    let retryTitle: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            ContentUnavailableView(
                message,
                systemImage: "exclamationmark.triangle"
            )
            Button(retryTitle, action: retry)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// File name → SF Symbol.
func attachmentIcon(for extensionName: String) -> String {
    switch extensionName {
    case "png", "jpg", "jpeg", "gif", "heic", "webp", "bmp", "tiff":
        return "photo"
    case "pdf":
        return "doc.richtext"
    case "zip", "rar", "7z", "gz", "tar":
        return "doc.zipper"
    case "mov", "mp4", "m4v", "avi", "mkv":
        return "film"
    case "mp3", "wav", "m4a", "aac":
        return "waveform"
    case "doc", "docx", "pages":
        return "doc.text"
    case "xls", "xlsx", "numbers", "csv":
        return "tablecells"
    case "txt", "md", "log":
        return "doc.plaintext"
    default:
        return "paperclip"
    }
}

/// Identifiable wrapper used to present a file preview sheet.
struct AttachmentPreviewItem: Identifiable {
    let id = UUID()
    let url: URL
}

/// QuickLook preview for a downloaded attachment.
struct QuickLookPreview: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: QLPreviewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(url: url)
    }

    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        private let url: URL

        init(url: URL) {
            self.url = url
        }

        func numberOfPreviewItems(in controller: QLPreviewController) -> Int {
            1
        }

        func previewController(
            _ controller: QLPreviewController,
            previewItemAt index: Int
        ) -> QLPreviewItem {
            url as NSURL
        }
    }
}
