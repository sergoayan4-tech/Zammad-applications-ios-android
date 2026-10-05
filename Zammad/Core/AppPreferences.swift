import SwiftUI
import Observation

/// Languages the user can pick inside the app.
enum AppLanguage: String, CaseIterable, Identifiable {
    case system
    case en
    case ru

    var id: String { rawValue }

    /// Native name of the language (never translated).
    var nativeName: String {
        switch self {
        case .system: return "System"
        case .en: return "English"
        case .ru: return "Русский"
        }
    }
}

/// Appearance modes.
enum AppearanceMode: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

/// User facing preferences: language, appearance and localized strings.
@MainActor
@Observable
final class AppPreferences {
    private let defaults = UserDefaults.standard

    var language: AppLanguage
    var appearance: AppearanceMode

    init() {
        language = AppLanguage(rawValue: defaults.string(forKey: "prefs.language") ?? "") ?? .system
        appearance = AppearanceMode(rawValue: defaults.string(forKey: "prefs.appearance") ?? "") ?? .system
        L10n.currentCode = resolvedLanguageCode
    }

    /// Language code actually used for string lookup ("en", "ru", ...).
    var resolvedLanguageCode: String {
        switch language {
        case .system:
            return Locale.current.language.languageCode?.identifier ?? "en"
        case .en, .ru:
            return language.rawValue
        }
    }

    var locale: Locale {
        Locale(identifier: resolvedLanguageCode)
    }

    func setLanguage(_ value: AppLanguage) {
        language = value
        defaults.set(value.rawValue, forKey: "prefs.language")
        L10n.currentCode = resolvedLanguageCode
    }

    func setAppearance(_ value: AppearanceMode) {
        appearance = value
        defaults.set(value.rawValue, forKey: "prefs.appearance")
    }

    /// Localized label for a language option.
    func label(for value: AppLanguage) -> String {
        value == .system ? t("settings.language.system") : value.nativeName
    }

    /// Localized string lookup. Reading `language` inside a view body makes
    /// SwiftUI re-render the view when the language changes.
    func t(_ key: String) -> String {
        L10n.translate(key, code: resolvedLanguageCode)
    }

    /// Localized description of an error.
    func message(for error: Error) -> String {
        if let apiError = error as? APIError {
            return apiError.localizedMessage(code: resolvedLanguageCode)
        }
        return error.localizedDescription
    }

    /// "2 hours ago" style date.
    func relativeDate(_ date: Date?) -> String {
        guard let date, date.timeIntervalSince1970 > 0 else { return "—" }
        var style = Date.RelativeFormatStyle(presentation: .named)
        style.locale = locale
        return date.formatted(style)
    }

    /// "12 Jan 2025, 14:03" style date.
    func dateTime(_ date: Date?) -> String {
        guard let date, date.timeIntervalSince1970 > 0 else { return "—" }
        var style = Date.FormatStyle(date: .abbreviated, time: .shortened)
        style.locale = locale
        return date.formatted(style)
    }
}
