import Foundation

/// A public holiday or special day. Multi-day holidays such as Kurban Bayramı are one entry with [days].
struct Holiday: Hashable {
    var date: Day
    var localName: String
    var name: String
    var days = 1
    var tentative = false
    var special = false

    var end: Day { date.adding(days: days - 1) }
}

/// Reads the Nager.Date `PublicHolidays` response: nationwide public holidays only, multi-day holidays merged.
enum HolidayParser {
    private struct Item: Decodable {
        var date: String
        var localName: String?
        var name: String?
        var global: Bool?
        var types: [String]?
    }

    private static func strip(_ text: String, _ pattern: String) -> String {
        text.replacingOccurrences(of: pattern, with: "", options: [.regularExpression, .caseInsensitive])
    }

    private static let tentative = #"\s*\(Tentative Date\)"#
    private static let dayWord = #"\s+(First|Second|Third|Fourth|Fifth|\d+(st|nd|rd|th)?)\s+Day$"#
    private static let localDay = #"\s*\d+\.\s*(Gün|Tag|Jour|Día|Dia|Giorno|Dag)$"#

    static func parse(_ data: Data) throws -> [Holiday] {
        let items = try JSONDecoder().decode([Item].self, from: data)
        let raw = items.filter { ($0.global ?? true) && ($0.types == nil || $0.types!.contains("Public")) }
            .compactMap { item -> Holiday? in
                guard let date = Day(iso: item.date) else { return nil }
                let local = item.localName ?? ""
                let english = item.name ?? ""
                let isTentative = local.range(of: tentative, options: [.regularExpression, .caseInsensitive]) != nil ||
                    english.range(of: tentative, options: [.regularExpression, .caseInsensitive]) != nil
                return Holiday(date: date, localName: strip(local, tentative).trimmingCharacters(in: .whitespaces),
                               name: strip(english, tentative).trimmingCharacters(in: .whitespaces), tentative: isTentative)
            }
            .sorted { $0.date < $1.date }
        return merge(raw)
    }

    /// Joins consecutive days of the same holiday ("Eid al-Fitr First/Second/Third Day") into one entry.
    static func merge(_ list: [Holiday]) -> [Holiday] {
        var result: [Holiday] = []
        for holiday in list.sorted(by: { $0.date < $1.date }) {
            let base = strip(holiday.name, dayWord)
            if let index = result.lastIndex(where: { !$0.special && $0.name == base && $0.end.adding(days: 1) == holiday.date }) {
                result[index].days += 1
                result[index].tentative = result[index].tentative || holiday.tentative
            } else {
                var copy = holiday
                copy.name = base
                copy.localName = strip(holiday.localName, localDay).trimmingCharacters(in: .whitespaces)
                result.append(copy)
            }
        }
        return result
    }

    /// Türkiye's religious holidays move with the lunar calendar and Nager.Date only estimates them.
    /// Years with dates published by Diyanet use those instead.
    static func correctTurkey(_ year: Int, _ list: [Holiday]) -> [Holiday] {
        guard let official = turkeyBayrams[year] else { return list }
        let kept = list.filter { !$0.name.lowercased().hasPrefix("eid al-") && !$0.localName.contains("Bayramı Arife") }
        return (kept + official).sorted { $0.date < $1.date }
    }

    static let turkeyBayrams: [Int: [Holiday]] = [
        2026: [
            Holiday(date: Day(year: 2026, month: 3, day: 19), localName: "Ramazan Bayramı Arifesi", name: "Eid al-Fitr Eve"),
            Holiday(date: Day(year: 2026, month: 3, day: 20), localName: "Ramazan Bayramı", name: "Eid al-Fitr", days: 3),
            Holiday(date: Day(year: 2026, month: 5, day: 26), localName: "Kurban Bayramı Arifesi", name: "Eid al-Adha Eve"),
            Holiday(date: Day(year: 2026, month: 5, day: 27), localName: "Kurban Bayramı", name: "Eid al-Adha", days: 4)
        ],
        2027: [
            Holiday(date: Day(year: 2027, month: 3, day: 8), localName: "Ramazan Bayramı Arifesi", name: "Eid al-Fitr Eve"),
            Holiday(date: Day(year: 2027, month: 3, day: 9), localName: "Ramazan Bayramı", name: "Eid al-Fitr", days: 3),
            Holiday(date: Day(year: 2027, month: 5, day: 15), localName: "Kurban Bayramı Arifesi", name: "Eid al-Adha Eve"),
            Holiday(date: Day(year: 2027, month: 5, day: 16), localName: "Kurban Bayramı", name: "Eid al-Adha", days: 4)
        ]
    ]
}

/// Days people celebrate without a day off. Dates follow each country's own custom.
enum SpecialDays {
    enum Kind: String {
        case valentines, mothers, fathers, parents, teachers
        var nameKey: String { "special_\(rawValue)" }
    }

    static func forCountry(_ country: String, _ year: Int) -> [(Day, Kind)] {
        var days: [(Day, Kind)] = [(Day(year: year, month: 2, day: 14), .valentines)]
        if let day = mothersDay(country, year) { days.append((day, country == "KR" ? .parents : .mothers)) }
        if let day = fathersDay(country, year) { days.append((day, .fathers)) }
        if country == "TR" { days.append((Day(year: year, month: 11, day: 24), .teachers)) }
        return days.sorted { $0.0 < $1.0 }
    }

    static func nthSunday(_ year: Int, _ month: Int, _ n: Int) -> Day {
        let first = Day(year: year, month: month, day: 1)
        let offset = (7 - first.weekday) % 7
        return first.adding(days: offset + (n - 1) * 7)
    }

    static func lastSunday(_ year: Int, _ month: Int) -> Day {
        let first = Day(year: year, month: month, day: 1)
        let last = first.with(day: first.lengthOfMonth)
        return last.adding(days: -(last.weekday % 7))
    }

    static func mothersDay(_ country: String, _ year: Int) -> Day? {
        switch country {
        case "TR", "US", "CA", "AU", "NZ", "DE", "AT", "CH", "IT", "NL", "BE", "JP", "BR", "HK", "TW", "DK", "FI":
            return nthSunday(year, 5, 2)
        case "FR":
            let last = lastSunday(year, 5)
            return last == easter(year).adding(days: 49) ? nthSunday(year, 6, 1) : last
        case "ES", "PT": return nthSunday(year, 5, 1)
        case "MX": return Day(year: year, month: 5, day: 10)
        case "GB", "IE": return easter(year).adding(days: -21)
        case "KR": return Day(year: year, month: 5, day: 8)
        case "TH": return Day(year: year, month: 8, day: 12)
        case "SA", "AE", "EG", "JO", "LB", "QA", "KW", "BH", "OM": return Day(year: year, month: 3, day: 21)
        case "ID": return Day(year: year, month: 12, day: 22)
        default: return nil
        }
    }

    static func fathersDay(_ country: String, _ year: Int) -> Day? {
        switch country {
        case "TR", "US", "CA", "GB", "IE", "FR", "NL", "JP", "HK", "MX": return nthSunday(year, 6, 3)
        case "DE": return easter(year).adding(days: 39)
        case "IT", "ES", "PT": return Day(year: year, month: 3, day: 19)
        case "BR": return nthSunday(year, 8, 2)
        case "AU", "NZ": return nthSunday(year, 9, 1)
        case "AT", "BE": return nthSunday(year, 6, 2)
        case "CH": return nthSunday(year, 6, 1)
        case "TW": return Day(year: year, month: 8, day: 8)
        case "TH": return Day(year: year, month: 12, day: 5)
        case "ID": return Day(year: year, month: 11, day: 12)
        default: return nil
        }
    }

    /// Western (Gregorian) Easter Sunday (Meeus/Jones/Butcher).
    static func easter(_ year: Int) -> Day {
        let a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4
        let f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30
        let i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451
        let n = h + l - 7 * m + 114
        return Day(year: year, month: n / 31, day: n % 31 + 1)
    }
}

/// Public holiday reminders: settings, an on-device cache of Nager.Date (free, no key) and their notices.
enum Holidays {
    static var enabled: Bool {
        get { Shared.defaults.bool(forKey: "holidays.enabled") }
        set { Shared.defaults.set(newValue, forKey: "holidays.enabled"); notifyChanged() }
    }

    static var country: String {
        get { Shared.defaults.string(forKey: "holidays.country") ?? defaultCountry }
        set { Shared.defaults.set(newValue, forKey: "holidays.country"); notifyChanged() }
    }

    static var time: ClockTime {
        get {
            guard Shared.defaults.object(forKey: "holidays.hour") != nil else { return ClockTime(hour: 9, minute: 0) }
            return ClockTime(hour: Shared.defaults.integer(forKey: "holidays.hour"), minute: Shared.defaults.integer(forKey: "holidays.minute"))
        }
        set {
            Shared.defaults.set(newValue.hour, forKey: "holidays.hour")
            Shared.defaults.set(newValue.minute, forKey: "holidays.minute")
            notifyChanged()
        }
    }

    /// Premium: also a heads-up the day before.
    static var dayBefore: Bool {
        get { Shared.defaults.bool(forKey: "holidays.dayBefore") }
        set { Shared.defaults.set(newValue, forKey: "holidays.dayBefore"); notifyChanged() }
    }

    /// Premium: Mother's Day, Father's Day, Valentine's Day and similar.
    static var specialDays: Bool {
        get { Shared.defaults.bool(forKey: "holidays.special") }
        set { Shared.defaults.set(newValue, forKey: "holidays.special"); notifyChanged() }
    }

    static var defaultCountry: String {
        let region = Locale.current.region?.identifier ?? ""
        return region.count == 2 ? region.uppercased() : "TR"
    }

    /// Countries Nager.Date covers, as ISO codes; empty until the list was loaded once.
    static var countries: [String] {
        (Shared.defaults.string(forKey: "holidays.countries") ?? "").split(separator: ",").map(String.init).filter { $0.count == 2 }
    }

    static func unsupported(_ code: String = country) -> Bool { Shared.defaults.bool(forKey: "holidays.unsupported.\(code)") }

    static func hasData(_ code: String = country, year: Int = Day.today().year) -> Bool {
        Shared.defaults.data(forKey: "holidays.cache.\(code).\(year)") != nil
    }

    /// Holidays from today on, for this and next year, with special days when they are on and unlocked.
    static func upcoming(_ today: Day = .today(), includeSpecial: Bool? = nil) -> [Holiday] {
        let code = country
        let special = includeSpecial ?? (specialDays && Premium.unlocked)
        let list = [today.year, today.year + 1].flatMap { year -> [Holiday] in
            let cached = Shared.defaults.data(forKey: "holidays.cache.\(code).\(year)").flatMap { try? HolidayParser.parse($0) } ?? []
            let holidays = code == "TR" ? HolidayParser.correctTurkey(year, cached) : cached
            let extra = special ? SpecialDays.forCountry(code, year).map { day, kind in
                Holiday(date: day, localName: L.s(kind.nameKey), name: L.s(kind.nameKey), special: true)
            } : []
            return holidays + extra
        }
        return list.filter { $0.end >= today }.sorted { $0.date < $1.date }
    }

    /// A holiday's name as people in the app's language know it: the local name, or English abroad.
    static func displayName(_ holiday: Holiday) -> String {
        if holiday.special { return holiday.localName }
        let language = AppLanguage.resolved.split(separator: "-").first.map(String.init) ?? "en"
        let local = Set(Locale.availableIdentifiers.map(Locale.init(identifier:))
            .filter { $0.region?.identifier == country }.compactMap { $0.language.languageCode?.identifier })
        return local.contains(language) || holiday.name.isEmpty ? holiday.localName : holiday.name
    }

    struct Notice: Hashable {
        var at: Date
        var holiday: Holiday
        var early: Bool
    }

    static func notices(_ now: Date = Date(), limit: Int = 20) -> [Notice] {
        guard enabled else { return [] }
        let premium = Premium.unlocked
        return upcoming(Day(now).adding(days: -1)).flatMap { holiday -> [Notice] in
            let onDay = holiday.date.at(time)
            var list = [Notice(at: onDay, holiday: holiday, early: false)]
            if dayBefore && premium { list.append(Notice(at: onDay.addingTimeInterval(-86400), holiday: holiday, early: true)) }
            return list
        }.filter { $0.at > now }.sorted { $0.at < $1.at }.prefix(limit).map { $0 }
    }

    private static var lastAttempt = Date.distantPast

    /// Downloads what is missing or older than a month. Returns nil when there was nothing to do,
    /// otherwise whether every download worked.
    @discardableResult
    static func refresh(force: Bool = false) async -> Bool? {
        let code = country
        let year = Day.today().year
        let now = Date()
        func stale(_ key: String) -> Bool {
            let fetched = Shared.defaults.object(forKey: "holidays.fetched.\(key)") as? Date ?? .distantPast
            return force || now.timeIntervalSince(fetched) > 30 * 86400
        }
        let years = [year, year + 1].filter { stale("\(code).\($0)") }
        let needCountries = countries.isEmpty || stale("countries")
        if years.isEmpty && !needCountries { return nil }
        if !force && now.timeIntervalSince(lastAttempt) < 120 { return nil }
        lastAttempt = now
        var ok = true
        if needCountries, let result = try? await get("https://date.nager.at/api/v3/AvailableCountries"), result.0 == 200 {
            struct Country: Decodable { var countryCode: String }
            if let list = try? JSONDecoder().decode([Country].self, from: result.1) {
                Shared.defaults.set(list.map(\.countryCode).joined(separator: ","), forKey: "holidays.countries")
                Shared.defaults.set(now, forKey: "holidays.fetched.countries")
            }
        }
        for y in years {
            do {
                let (status, data) = try await get("https://date.nager.at/api/v3/PublicHolidays/\(y)/\(code)")
                switch status {
                case 200:
                    _ = try HolidayParser.parse(data)
                    Shared.defaults.set(data, forKey: "holidays.cache.\(code).\(y)")
                    Shared.defaults.set(now, forKey: "holidays.fetched.\(code).\(y)")
                    Shared.defaults.set(false, forKey: "holidays.unsupported.\(code)")
                case 204, 404:
                    Shared.defaults.set(Data("[]".utf8), forKey: "holidays.cache.\(code).\(y)")
                    Shared.defaults.set(now, forKey: "holidays.fetched.\(code).\(y)")
                    Shared.defaults.set(true, forKey: "holidays.unsupported.\(code)")
                default:
                    ok = false
                }
            } catch {
                ok = false
            }
        }
        notifyChanged()
        return ok
    }

    private static func get(_ address: String) async throws -> (Int, Data) {
        var request = URLRequest(url: URL(string: address)!, timeoutInterval: 15)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        return ((response as? HTTPURLResponse)?.statusCode ?? 0, data)
    }
}
