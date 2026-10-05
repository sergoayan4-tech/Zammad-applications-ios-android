import SwiftUI
import UIKit

private final class AttributedBox {
    let value: AttributedString
    init(_ value: AttributedString) {
        self.value = value
    }
}

/// Renders Zammad article bodies (usually HTML, sometimes plain text).
struct HTMLText: View {
    private let attributed: AttributedString

    init(_ text: String, contentType: String?) {
        let isHTML = (contentType ?? "text/plain").lowercased().contains("html")
        attributed = Self.render(isHTML ? text : Self.plainToHTML(text))
    }

    var body: some View {
        Text(attributed)
            .frame(maxWidth: .infinity, alignment: .leading)
            .textSelection(.enabled)
    }

    // MARK: - Conversion

    private static let cache: NSCache<NSString, AttributedBox> = {
        let cache = NSCache<NSString, AttributedBox>()
        cache.countLimit = 200
        return cache
    }()

    private static func render(_ markup: String) -> AttributedString {
        let key = markup as NSString
        if let cached = cache.object(forKey: key) {
            return cached.value
        }
        let value = convert(markup)
        cache.setObject(AttributedBox(value), forKey: key)
        return value
    }

    private static func convert(_ html: String) -> AttributedString {
        let document = "<div style=\"font-family:-apple-system,'HelveticaNeue',sans-serif;font-size:15px;line-height:1.35;\">\(html)</div>"

        guard let data = document.data(using: .utf8),
              let native = try? NSAttributedString(
                  data: data,
                  options: [
                      .documentType: NSAttributedString.DocumentType.html,
                      .characterEncoding: String.Encoding.utf8.rawValue
                  ],
                  documentAttributes: nil
              ) else {
            return AttributedString(stripTags(html))
        }

        var result = AttributedString(native)

        // HTML import brings explicit colors that do not adapt to Dark Mode.
        result.foregroundColor = Color.primary

        // Give links their accent color back.
        var linkRanges: [Range<AttributedString.Index>] = []
        for (link, range) in result.runs[\.link] {
            if (link as URL?) != nil {
                linkRanges.append(range)
            }
        }
        for range in linkRanges {
            result[range].foregroundColor = Color.accentColor
        }

        return result
    }

    private static func plainToHTML(_ text: String) -> String {
        escape(text).replacingOccurrences(of: "\n", with: "<br>")
    }

    private static func escape(_ text: String) -> String {
        text
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
    }

    private static func stripTags(_ text: String) -> String {
        var value = text
            .replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
            .replacingOccurrences(of: "</p\\s*>", with: "\n", options: .regularExpression)
        value = value.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        return value.trimmed
    }
}
