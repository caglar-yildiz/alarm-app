import Foundation

/// In-app language choice, like the Android app's Tools › Language. Empty tag means "follow the device".
/// Strings come from the matching `.lproj` folder, so the language can change without restarting the app.
enum AppLanguage {
    static let supported = ["", "en", "tr", "de", "fr", "es", "it", "pt-BR", "nl", "ja", "ko", "zh-Hant", "ar", "th", "id"]
    private static let key = "language"
    private static var cachedBundle: (String, Bundle)?

    /// The stored choice ("" = device language).
    static var current: String {
        get { Shared.defaults.string(forKey: key) ?? "" }
        set {
            Shared.defaults.set(newValue, forKey: key)
            cachedBundle = nil
        }
    }

    /// The language actually used: the stored choice, or the best match for the device languages.
    static var resolved: String {
        let choice = current
        if !choice.isEmpty { return choice }
        let available = supported.filter { !$0.isEmpty }
        return Bundle.preferredLocalizations(from: available, forPreferences: Locale.preferredLanguages).first ?? "en"
    }

    static var locale: Locale {
        let tag = resolved
        // Keep the device region (for country defaults), but the app language.
        if let region = Locale.current.region?.identifier, !tag.contains("-") {
            return Locale(identifier: "\(tag)_\(region)")
        }
        return Locale(identifier: tag)
    }

    static var isRightToLeft: Bool { Locale.Language(identifier: resolved).characterDirection == .rightToLeft }

    static var bundle: Bundle {
        let tag = resolved
        if let cached = cachedBundle, cached.0 == tag { return cached.1 }
        let bundle = Self.lproj(tag) ?? Self.lproj("en") ?? .main
        cachedBundle = (tag, bundle)
        return bundle
    }

    static let english: Bundle = lproj("en") ?? .main

    private static func lproj(_ tag: String) -> Bundle? {
        let candidates = [tag, tag.lowercased(), tag.replacingOccurrences(of: "-", with: "_")]
        for name in candidates {
            if let path = Bundle.main.path(forResource: name, ofType: "lproj"), let bundle = Bundle(path: path) {
                return bundle
            }
        }
        return nil
    }

    static func displayName(_ tag: String) -> String {
        if tag.isEmpty { return L.s("language_system") }
        let locale = Locale(identifier: tag)
        let name = locale.localizedString(forIdentifier: tag) ?? tag
        return name.prefix(1).uppercased() + name.dropFirst()
    }
}

/// String lookup in the app language, with English as the fallback for keys a language lacks.
enum L {
    private static let missing = "\u{0}missing"

    static func raw(_ key: String) -> String {
        let value = AppLanguage.bundle.localizedString(forKey: key, value: missing, table: nil)
        if value != missing { return value }
        let english = AppLanguage.english.localizedString(forKey: key, value: missing, table: nil)
        return english == missing ? key : english
    }

    static func s(_ key: String, _ args: CVarArg...) -> String {
        let format = raw(key)
        if args.isEmpty { return format.replacingOccurrences(of: "%%", with: "%") }
        return String(format: format, locale: AppLanguage.locale, arguments: args)
    }

    /// A plural string (stringsdict) for [count].
    static func p(_ key: String, _ count: Int) -> String {
        let format = raw(key)
        return String(format: format, locale: AppLanguage.locale, arguments: [count])
    }

    /// Localized weekday name for ISO day 1 (Monday) … 7 (Sunday).
    static func weekday(_ day: Int, short: Bool = false) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = AppLanguage.locale
        let symbols = short ? calendar.shortStandaloneWeekdaySymbols : calendar.standaloneWeekdaySymbols
        // Calendar symbols start on Sunday.
        let name = symbols[day % 7]
        return name.prefix(1).uppercased() + name.dropFirst()
    }

    static func date(_ day: Day, style: DateFormatter.Style = .long) -> String {
        let formatter = DateFormatter()
        formatter.locale = AppLanguage.locale
        formatter.dateStyle = style
        formatter.timeStyle = .none
        return formatter.string(from: day.start)
    }

    static func pattern(_ template: String, _ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = AppLanguage.locale
        formatter.setLocalizedDateFormatFromTemplate(template)
        return formatter.string(from: date)
    }

    static func clock(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return ClockTime(hour: parts.hour ?? 0, minute: parts.minute ?? 0).text
    }

    /// "Monday · 07:30", as on the Android next-alarm card.
    static func nextAlarm(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = AppLanguage.locale
        formatter.dateFormat = "EEEE"
        let day = formatter.string(from: date)
        return (day.prefix(1).uppercased() + day.dropFirst()) + " · " + clock(date)
    }

    /// "7 h 30 min", in the app language.
    static func sleepDuration(_ minutes: Int) -> String {
        minutes % 60 == 0 ? s("duration_hours", minutes / 60) : s("duration_hours_minutes", minutes / 60, minutes % 60)
    }
}
