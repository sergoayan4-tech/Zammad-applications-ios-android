import Foundation

/// Decodes a value that may arrive either as a string or as a number.
///
/// Zammad is not consistent about this (e.g. attachment `size` may be `"19"`,
/// ticket `number` is a string, `time_unit` may be a number).
struct FlexibleString: Decodable, Hashable {
    let value: String

    init(_ value: String) {
        self.value = value
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let text = try? container.decode(String.self) {
            value = text
        } else if let number = try? container.decode(Int.self) {
            value = String(number)
        } else if let number = try? container.decode(Double.self) {
            value = String(number)
        } else if container.decodeNil() {
            value = ""
        } else {
            throw DecodingError.typeMismatch(
                FlexibleString.self,
                DecodingError.Context(
                    codingPath: decoder.codingPath,
                    debugDescription: "Unsupported scalar value"
                )
            )
        }
    }
}
