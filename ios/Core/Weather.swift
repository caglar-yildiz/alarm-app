import Foundation

/// Simplified sky conditions, ordered by how much they matter for planning the day.
enum Sky: Int, Comparable, CaseIterable {
    case clear, partly, cloudy, fog, rain, sleet, snow, thunder

    var wet: Bool { self >= .rain }
    var labelKey: String { ["sky_clear", "sky_partly", "sky_cloudy", "sky_fog", "sky_rain", "sky_sleet", "sky_snow", "sky_thunder"][rawValue] }
    var label: String { L.s(labelKey) }

    static func < (a: Sky, b: Sky) -> Bool { a.rawValue < b.rawValue }

    /// Maps a MET Norway symbol code such as `lightrainshowers_night` to a [Sky].
    static func from(symbol: String?) -> Sky? {
        guard let symbol else { return nil }
        let code = (symbol.split(separator: "_").first.map(String.init) ?? "").lowercased()
        if code.isEmpty { return nil }
        if code.contains("thunder") { return .thunder }
        if code.contains("sleet") { return .sleet }
        if code.contains("snow") { return .snow }
        if code.contains("rain") { return .rain }
        if code.contains("fog") { return .fog }
        if code == "partlycloudy" || code == "fair" { return .partly }
        if code.contains("cloudy") { return .cloudy }
        if code == "clearsky" { return .clear }
        return nil
    }

    static func isNight(_ symbol: String?) -> Bool { symbol?.hasSuffix("_night") == true }
}

struct WeatherPlace: Codable, Hashable {
    var name: String
    var region: String
    var lat: Double
    var lon: Double
}

struct WeatherNow { var temp: Double; var sky: Sky; var night: Bool; var humidity: Int; var windKmh: Int }
struct WeatherHour { var time: Date; var temp: Double; var sky: Sky; var night: Bool; var precip: Double }
struct WeatherDay { var date: Day; var min: Double; var max: Double; var sky: Sky; var precip: Double }

struct Forecast {
    var updated: Date
    var now: WeatherNow
    var hours: [WeatherHour]
    var days: [WeatherDay]
}

/// Reads the MET Norway `locationforecast/2.0/compact` response.
enum ForecastParser {
    private struct Response: Decodable {
        struct Properties: Decodable {
            struct Meta: Decodable { var updated_at: String }
            var meta: Meta
            var timeseries: [Series]
        }
        struct Series: Decodable {
            var time: String
            var data: SeriesData
        }
        struct SeriesData: Decodable {
            struct Instant: Decodable {
                struct Details: Decodable { var air_temperature: Double; var relative_humidity: Double?; var wind_speed: Double? }
                var details: Details
            }
            struct Period: Decodable {
                struct Summary: Decodable { var symbol_code: String? }
                struct Details: Decodable { var precipitation_amount: Double? }
                var summary: Summary?
                var details: Details?
            }
            var instant: Instant
            var next_1_hours: Period?
            var next_6_hours: Period?
            var next_12_hours: Period?
        }
        var properties: Properties
    }

    private struct Step {
        var time: Date
        var temp: Double
        var humidity: Double?
        var wind: Double?
        var symbol1: String?
        var precip1: Double?
        var symbol6: String?
        var precip6: Double?
        var symbol12: String?
        var symbol: String? { symbol1 ?? symbol6 ?? symbol12 }
    }

    struct EmptyForecast: Error {}

    private static let iso: ISO8601DateFormatter = ISO8601DateFormatter()

    static func parse(_ data: Data, zone: TimeZone = .current, now: Date = Date()) throws -> Forecast {
        let response = try JSONDecoder().decode(Response.self, from: data)
        let updated = iso.date(from: response.properties.meta.updated_at) ?? now
        let steps: [Step] = response.properties.timeseries.compactMap { item in
            guard let time = iso.date(from: item.time) else { return nil }
            let d = item.data
            return Step(time: time, temp: d.instant.details.air_temperature, humidity: d.instant.details.relative_humidity,
                        wind: d.instant.details.wind_speed,
                        symbol1: d.next_1_hours?.summary?.symbol_code, precip1: d.next_1_hours?.details?.precipitation_amount,
                        symbol6: d.next_6_hours?.summary?.symbol_code, precip6: d.next_6_hours?.details?.precipitation_amount,
                        symbol12: d.next_12_hours?.summary?.symbol_code)
        }
        // The forecast starts at the current hour: keep the latest step that is not in the future.
        let start = max(0, steps.lastIndex { $0.time <= now } ?? 0)
        let upcoming = Array(steps.dropFirst(start))
        guard let current = upcoming.first else { throw EmptyForecast() }

        func precipitation(_ list: [Step], _ index: Int) -> Double {
            guard index + 1 < list.count else { return 0 }
            let hours = list[index + 1].time.timeIntervalSince(list[index].time) / 3600
            let amount = hours <= 1 ? list[index].precip1 : (list[index].precip6 ?? list[index].precip1)
            return amount ?? 0
        }

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        func day(_ date: Date) -> Day {
            let parts = calendar.dateComponents([.year, .month, .day], from: date)
            return Day(year: parts.year!, month: parts.month!, day: parts.day!)
        }

        let weatherNow = WeatherNow(temp: current.temp, sky: Sky.from(symbol: current.symbol) ?? .cloudy,
                                    night: Sky.isNight(current.symbol), humidity: Int(current.humidity ?? 0),
                                    windKmh: Int((current.wind ?? 0) * 3.6))

        var hours: [WeatherHour] = []
        for (index, step) in upcoming.enumerated() {
            guard step.symbol1 != nil, hours.count < 25 else { break }
            hours.append(WeatherHour(time: step.time, temp: step.temp, sky: Sky.from(symbol: step.symbol1) ?? .cloudy,
                                     night: Sky.isNight(step.symbol1), precip: precipitation(upcoming, index)))
        }

        // Days use every step of the response, so today's high and low include the hours already passed.
        let today = day(now)
        var order: [Day] = []
        var byDate: [Day: [Int]] = [:]
        for index in steps.indices {
            let date = day(steps[index].time)
            guard date >= today else { continue }
            if byDate[date] == nil { order.append(date) }
            byDate[date, default: []].append(index)
        }
        let days = order.enumerated().filter { $0.offset == 0 || byDate[$0.element]!.count >= 2 }.prefix(7).map { _, date in
            let indices = byDate[date]!
            let temps = indices.map { steps[$0].temp }
            let precip = indices.reduce(0) { $0 + precipitation(steps, $1) }
            let daytime = indices.filter { (6...20).contains(calendar.component(.hour, from: steps[$0].time)) }
            let skies = (daytime.isEmpty ? indices : daytime).compactMap { Sky.from(symbol: steps[$0].symbol1 ?? steps[$0].symbol6) }
            return WeatherDay(date: date, min: temps.min()!, max: temps.max()!, sky: daySky(skies, precip), precip: precip)
        }
        return Forecast(updated: updated, now: weatherNow, hours: hours, days: Array(days))
    }

    /// A wet day shows its strongest precipitation, a dry day its most common sky.
    static func daySky(_ skies: [Sky], _ precip: Double) -> Sky {
        guard !skies.isEmpty else { return .cloudy }
        if precip >= 0.5, let wet = skies.filter(\.wet).max() { return wet }
        let dry = skies.filter { !$0.wet }
        let pool = dry.isEmpty ? skies : dry
        var counts: [Sky: Int] = [:]
        pool.forEach { counts[$0, default: 0] += 1 }
        return counts.max { a, b in a.value != b.value ? a.value < b.value : a.key < b.key }!.key
    }
}

/// The chosen city and the last forecast response, kept on the device.
enum WeatherStore {
    static var place: WeatherPlace? { Shared.load("weather.place", as: WeatherPlace.self) }

    static var json: Data? { Shared.defaults.data(forKey: "weather.json") }
    static var lastModified: String? { Shared.defaults.string(forKey: "weather.lastModified") }
    static var fetchedAt: Date? { Shared.defaults.object(forKey: "weather.fetchedAt") as? Date }
    static var expiresAt: Date { Shared.defaults.object(forKey: "weather.expiresAt") as? Date ?? .distantPast }
    static var stale: Bool { json == nil || Date() >= expiresAt }

    static func savePlace(_ place: WeatherPlace) {
        for key in ["weather.json", "weather.lastModified", "weather.fetchedAt", "weather.expiresAt", "weather.dayDate",
                    "weather.dayMin", "weather.dayMax"] {
            Shared.defaults.removeObject(forKey: key)
        }
        Shared.save(place, "weather.place")
        notifyChanged()
    }

    static func saveResponse(_ data: Data, lastModified: String?, expiresAt: Date, for place: WeatherPlace) {
        guard self.place == place else { return }
        Shared.defaults.set(data, forKey: "weather.json")
        Shared.defaults.set(lastModified, forKey: "weather.lastModified")
        Shared.defaults.set(expiresAt, forKey: "weather.expiresAt")
        Shared.defaults.set(Date(), forKey: "weather.fetchedAt")
    }

    static func markFresh(_ expiresAt: Date, for place: WeatherPlace) {
        guard self.place == place else { return }
        Shared.defaults.set(expiresAt, forKey: "weather.expiresAt")
        Shared.defaults.set(Date(), forKey: "weather.fetchedAt")
    }

    /// The cached forecast. Today's range keeps the lowest and highest values seen today, because each
    /// response starts at the current hour.
    static func forecast() -> Forecast? {
        guard let json, var forecast = try? ForecastParser.parse(json) else { return nil }
        guard var today = forecast.days.first else { return forecast }
        if Shared.defaults.string(forKey: "weather.dayDate") == today.date.iso {
            today.min = min(today.min, Shared.defaults.double(forKey: "weather.dayMin"))
            today.max = max(today.max, Shared.defaults.double(forKey: "weather.dayMax"))
        }
        Shared.defaults.set(today.date.iso, forKey: "weather.dayDate")
        Shared.defaults.set(today.min, forKey: "weather.dayMin")
        Shared.defaults.set(today.max, forKey: "weather.dayMax")
        forecast.days[0] = today
        return forecast
    }
}

/// Talks to MET Norway (free, also for commercial apps, CC BY 4.0). MET asks clients to identify themselves,
/// to cache until `Expires` and to send `If-Modified-Since`.
enum WeatherClient {
    enum Failure { case offline, service }

    @MainActor static var loading = false
    @MainActor private static var lastAttempt = Date.distantPast

    /// Refreshes the stored forecast. Without [force] it only asks when the cache expired.
    @MainActor
    static func refresh(force: Bool) async -> Failure? {
        guard let place = WeatherStore.place, !loading else { return nil }
        if !force && (!WeatherStore.stale || Date().timeIntervalSince(lastAttempt) < 120) { return nil }
        loading = true
        lastAttempt = Date()
        notifyChanged()
        defer {
            loading = false
            notifyChanged()
        }
        let url = URL(string: String(format: "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
                                      locale: Locale(identifier: "en_US_POSIX"), place.lat, place.lon))!
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
        request.setValue("BENG/\(version) (iOS; +https://github.com/caglar-yildiz/alarm-app)", forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if WeatherStore.json != nil, let modified = WeatherStore.lastModified {
            request.setValue(modified, forHTTPHeaderField: "If-Modified-Since")
        }
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { return .service }
            let fallback = Date().addingTimeInterval(30 * 60)
            var expires = (http.value(forHTTPHeaderField: "Expires")).flatMap(httpDate) ?? fallback
            expires = min(max(expires, Date().addingTimeInterval(60)), Date().addingTimeInterval(6 * 3600))
            switch http.statusCode {
            case 200, 203:
                _ = try ForecastParser.parse(data)
                WeatherStore.saveResponse(data, lastModified: http.value(forHTTPHeaderField: "Last-Modified"), expiresAt: expires, for: place)
                return nil
            case 304:
                WeatherStore.markFresh(expires, for: place)
                return nil
            default:
                return .service
            }
        } catch let error as URLError {
            _ = error
            return .offline
        } catch {
            return .service
        }
    }

    private static func httpDate(_ text: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "GMT")
        formatter.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        return formatter.date(from: text)
    }
}

/// One row of the bundled place list: a province, or a district inside a province.
struct LocalPlace: Hashable {
    var province: String
    var district: String?
    var lat: Double
    var lon: Double
    var population: Int
    let provinceKey: String
    let districtKey: String

    init(province: String, district: String?, lat: Double, lon: Double, population: Int) {
        self.province = province
        self.district = district
        self.lat = lat
        self.lon = lon
        self.population = population
        provinceKey = PlaceSearch.normalize(province)
        districtKey = district.map(PlaceSearch.normalize) ?? ""
    }

    var isProvince: Bool { district == nil }
    /// "İstanbul" for a province, "İstanbul / Esenyurt" for a district.
    var title: String { district.map { "\(province) / \($0)" } ?? province }
}

/// Offline, as-you-type search over Turkey's 81 provinces and their districts (GeoNames, CC BY 4.0).
/// It ignores Turkish letters and case and ranks provinces, then districts, then districts of a matching
/// province, then names that merely contain the query; bigger places first inside each group.
enum PlaceSearch {
    private static var cache: [LocalPlace]?

    static func places(bundle: Bundle = .main) -> [LocalPlace] {
        if let cache { return cache }
        guard let url = bundle.url(forResource: "places_tr", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        let list = parse(text)
        cache = list
        return list
    }

    static func parse(_ text: String) -> [LocalPlace] {
        text.split(whereSeparator: \.isNewline).compactMap { line in
            line.hasPrefix("#") || line.isEmpty ? nil : parseLine(String(line))
        }
    }

    static func parseLine(_ line: String) -> LocalPlace? {
        let parts = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
        guard parts.count >= 6, let lat = Double(parts[3]), let lon = Double(parts[4]) else { return nil }
        return LocalPlace(province: parts[1], district: parts[0] == "D" && !parts[2].isEmpty ? parts[2] : nil,
                          lat: lat, lon: lon, population: Int(parts[5]) ?? 0)
    }

    /// Lower-case ASCII form, so Turkish letters, dotted/dotless I and accents never block a match.
    static func normalize(_ text: String) -> String {
        var out = ""
        for char in text {
            switch char {
            case "İ", "I", "ı", "î", "Î": out.append("i")
            case "Ş", "ş": out.append("s")
            case "Ğ", "ğ": out.append("g")
            case "Ü", "ü", "û", "Û": out.append("u")
            case "Ö", "ö": out.append("o")
            case "Ç", "ç": out.append("c")
            case "Â", "â": out.append("a")
            default: out.append(contentsOf: String(char).lowercased())
            }
        }
        return out
    }

    private static func tokens(_ query: String) -> [String] {
        normalize(query).split(whereSeparator: { " /,-.".contains($0) }).map(String.init)
    }

    static func search(_ all: [LocalPlace], _ query: String, limit: Int = 30) -> [LocalPlace] {
        let words = tokens(query)
        guard !words.isEmpty else { return [] }
        return all.compactMap { place in rank(place, words).map { ($0, place) } }
            .sorted { $0.0 != $1.0 ? $0.0 < $1.0 : $0.1.population > $1.1.population }
            .prefix(limit).map(\.1)
    }

    private static func startsWithWord(_ key: String, _ word: String) -> Bool {
        key.hasPrefix(word) || key.split(separator: " ").contains { $0.hasPrefix(word) }
    }

    /// Lower is better; nil means no match.
    private static func rank(_ place: LocalPlace, _ words: [String]) -> Int? {
        let province = place.provinceKey, district = place.districtKey
        if words.count == 1 {
            let word = words[0]
            if place.isProvince && startsWithWord(province, word) { return province == word ? 0 : 1 }
            if !place.isProvince && startsWithWord(district, word) { return district == word ? 2 : 3 }
            if !place.isProvince && startsWithWord(province, word) { return 4 }
            if place.isProvince && word.count >= 3 && province.contains(word) { return 5 }
            if !place.isProvince && word.count >= 3 && district.contains(word) { return 6 }
            return nil
        }
        // Several words: one must name the province, the others the district ("istanbul esen").
        guard let provinceWord = words.first(where: { startsWithWord(province, $0) }) else { return nil }
        var rest = words
        rest.remove(at: rest.firstIndex(of: provinceWord)!)
        if place.isProvince { return rest.isEmpty ? 0 : nil }
        return rest.allSatisfy { startsWithWord(district, $0) } ? 2 : nil
    }

    static func toWeatherPlace(_ place: LocalPlace) -> WeatherPlace {
        WeatherPlace(name: place.title, region: AppLanguage.locale.localizedString(forRegionCode: "TR") ?? "Türkiye",
                     lat: place.lat, lon: place.lon)
    }
}
