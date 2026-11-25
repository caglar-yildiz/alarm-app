import SwiftUI
import CoreLocation

/// The free Weather tab: a city picker, today's sky, the next 24 hours and the week ahead.
struct WeatherView: View {
    @EnvironmentObject private var app: AppModel
    @State private var picking = false
    @State private var query = ""
    @State private var world: [WeatherPlace]?
    @State private var searching = false
    @State private var searchFailed = false
    @State private var failure: WeatherClient.Failure?
    @State private var searchTask: Task<Void, Never>?
    @FocusState private var focused: Bool

    var body: some View {
        let _ = app.revision
        VStack(alignment: .leading, spacing: 10) {
            if let place = WeatherStore.place, !picking {
                if let forecast = WeatherStore.forecast() {
                    hero(place, forecast)
                    tip(forecast)
                    stats(forecast)
                    hours(forecast)
                    days(forecast)
                    footer
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        if WeatherClient.loading || failure == nil {
                            Text(L.s("weather_loading")).foregroundStyle(Color.muted)
                        } else {
                            Text(L.s(failure == .offline ? "weather_failed_offline" : "weather_failed")).foregroundStyle(.white)
                            Button(L.s("weather_refresh")) { refresh(force: true) }.bengStyle()
                        }
                    }
                    .card()
                    Button(L.s("weather_change_city")) { startPicking() }.bengStyle(.secondary)
                }
            } else {
                picker
            }
        }
        .screen()
        .task(id: WeatherStore.place?.name) { refresh(force: false) }
    }

    private func refresh(force: Bool) {
        Task { @MainActor in
            failure = await WeatherClient.refresh(force: force)
        }
    }

    private func startPicking() {
        picking = true
        world = nil
        searchFailed = false
        query = ""
    }

    // MARK: - City picker

    private var picker: some View {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        let local = trimmed.isEmpty ? [] : PlaceSearch.search(PlaceSearch.places(), trimmed)
        let others = (world ?? []).filter { found in !local.contains { abs($0.lat - found.lat) < 0.05 && abs($0.lon - found.lon) < 0.05 } }
        return VStack(alignment: .leading, spacing: 10) {
            VStack(spacing: 12) {
                if trimmed.isEmpty {
                    WeatherGlyph(sky: .partly, night: false, floating: true).frame(width: 112, height: 112)
                    Text(L.s("weather_intro_title")).font(.title3.weight(.semibold)).foregroundStyle(.white).multilineTextAlignment(.center)
                    Text(L.s("weather_intro_body")).font(.subheadline).foregroundStyle(Color.muted).multilineTextAlignment(.center)
                }
                TextField(L.s("weather_search_hint"), text: $query)
                    .focused($focused)
                    .submitLabel(.search)
                    .onSubmit { searchWorldwide(trimmed) }
                    .textInputAutocapitalization(.words)
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(Color(argb: 0xDA11192F), in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(argb: 0x748C8CD8)))
                    .onChange(of: query) { _, value in queryChanged(value) }
            }
            .frame(maxWidth: .infinity)
            .card()
            if !local.isEmpty || !others.isEmpty {
                VStack(spacing: 0) {
                    ForEach(local, id: \.self) { place in
                        let region = PlaceSearch.toWeatherPlace(place).region
                        row(place.title, L.s(place.isProvince ? "weather_place_province" : "weather_place_district", region),
                            PlaceSearch.toWeatherPlace(place))
                    }
                    ForEach(others, id: \.self) { place in row(place.name, place.region, place) }
                }
                .card()
            }
            if let status = status(trimmed, local) {
                Text(status).font(.subheadline).foregroundStyle(Color.muted).frame(maxWidth: .infinity)
            }
            if WeatherStore.place != nil {
                Button(L.s("cancel")) {
                    searchTask?.cancel()
                    picking = false
                    query = ""
                    world = nil
                }.bengStyle(.secondary)
            }
        }
        .onAppear { if WeatherStore.place == nil { focused = true } }
    }

    private func status(_ query: String, _ local: [LocalPlace]) -> String? {
        if searching { return L.s("weather_searching") }
        if searchFailed && local.isEmpty { return L.s("weather_search_failed") }
        if query.count >= 2 && local.isEmpty && world?.isEmpty == true { return L.s("weather_no_results") }
        return nil
    }

    private func row(_ title: String, _ subtitle: String, _ place: WeatherPlace) -> some View {
        Button { choose(place) } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body.weight(.semibold)).foregroundStyle(.white)
                if !subtitle.isEmpty { Text(subtitle).font(.footnote).foregroundStyle(Color.muted) }
            }
            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// Local results update on every keystroke; the worldwide geocoder only runs when they fall short.
    private func queryChanged(_ value: String) {
        searchTask?.cancel()
        world = nil
        searchFailed = false
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        let local = PlaceSearch.search(PlaceSearch.places(), trimmed)
        if trimmed.count >= 3 && local.count < 3 {
            searchTask = Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(700))
                if !Task.isCancelled { searchWorldwide(trimmed) }
            }
        }
    }

    private func searchWorldwide(_ query: String) {
        guard query.count >= 2, !searching else { return }
        searching = true
        searchFailed = false
        CLGeocoder().geocodeAddressString(query, in: nil, preferredLocale: AppLanguage.locale) { placemarks, error in
            DispatchQueue.main.async {
                searching = false
                guard self.query.trimmingCharacters(in: .whitespaces) == query else { return }
                if let placemarks {
                    var seen = Set<String>()
                    world = placemarks.compactMap { mark -> WeatherPlace? in
                        guard let location = mark.location else { return nil }
                        let name = [mark.locality, mark.subAdministrativeArea, mark.administrativeArea, mark.name]
                            .compactMap { $0 }.first { !$0.isEmpty } ?? query
                        let region = [mark.administrativeArea, mark.country].compactMap { $0 }.filter { !$0.isEmpty && $0 != name }
                        let unique = Array(NSOrderedSet(array: region)) as? [String] ?? region
                        let place = WeatherPlace(name: name, region: unique.joined(separator: ", "), lat: location.coordinate.latitude,
                                                 lon: location.coordinate.longitude)
                        return seen.insert(place.name + place.region).inserted ? place : nil
                    }
                } else {
                    let noResult = (error as? CLError)?.code == .geocodeFoundNoResult
                    world = noResult ? [] : nil
                    searchFailed = !noResult
                }
            }
        }
    }

    private func choose(_ place: WeatherPlace) {
        searchTask?.cancel()
        focused = false
        WeatherStore.savePlace(place)
        picking = false
        world = nil
        query = ""
        failure = nil
        refresh(force: true)
    }

    // MARK: - Forecast

    private func hero(_ place: WeatherPlace, _ forecast: Forecast) -> some View {
        let now = forecast.now
        let colors: [UInt32]
        if now.sky == .thunder { colors = [0x3E2F78, 0x241D52, 0x14122E] } else if now.sky.wet {
            colors = [0x34548A, 0x27306A, 0x161D40]
        } else if now.sky == .cloudy || now.sky == .fog {
            colors = [0x4A5486, 0x2F3566, 0x1A1F42]
        } else if now.night { colors = [0x33307E, 0x211F57, 0x111430] } else { colors = [0x3F7DD6, 0x5547C0, 0x2A2F78] }
        let temp = "\(Int(now.temp.rounded()))°"
        return VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(place.name).font(.title2.weight(.semibold)).foregroundStyle(.white).lineLimit(2)
                    Text(L.pattern("EEEEdMMMM", Date())).font(.footnote).foregroundStyle(Color(hex: 0xD9DCF5))
                }
                Spacer()
                WeatherGlyph(sky: now.sky, night: now.night, floating: true).frame(width: 112, height: 112)
            }
            TimeText(temp, size: 78)
            Text(now.sky.label).font(.title3.weight(.semibold)).foregroundStyle(.white)
            if let day = forecast.days.first, Int(day.max.rounded()) != Int(day.min.rounded()) {
                Text(L.s("weather_high_low", "\(Int(day.max.rounded()))°", "\(Int(day.min.rounded()))°")).font(.subheadline)
                    .foregroundStyle(Color(hex: 0xD9DCF5))
            }
        }
        .card(colors.map { Color(hex: $0) }, radius: 26)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(place.name), \(temp), \(now.sky.label)")
    }

    private func tip(_ forecast: Forecast) -> some View {
        let today = Day.today()
        let todayHours = forecast.hours.filter { Day($0.time) == today }
        let hours = todayHours.isEmpty ? Array(forecast.hours.prefix(6)) : todayHours
        let worst = (hours.map(\.sky) + [forecast.now.sky]).max()!
        let rain = hours.reduce(0) { $0 + $1.precip }
        let temps = hours.map(\.temp) + [forecast.now.temp]
        let key: String
        if worst == .thunder { key = "weather_tip_thunder" } else if worst == .snow || worst == .sleet { key = "weather_tip_snow" } else if worst.wet || rain >= 0.5 {
            key = "weather_tip_rain"
        } else if temps.max()! >= 30 { key = "weather_tip_hot" } else if temps.min()! <= 3 { key = "weather_tip_cold" } else if forecast.now.windKmh >= 40 {
            key = "weather_tip_wind"
        } else if worst == .clear || worst == .partly { key = "weather_tip_clear" } else { key = "weather_tip_calm" }
        return VStack(alignment: .leading, spacing: 2) {
            Text(L.s("weather_today_for")).font(.footnote.weight(.semibold)).tracking(1).foregroundStyle(Color.gold)
            Text(L.s(key)).foregroundStyle(.white)
        }
        .card([Color(argb: 0x40FFD98C), Color(argb: 0x1AFFD98C)], stroke: Color(argb: 0x80FFD98C))
    }

    private func stats(_ forecast: Forecast) -> some View {
        let rain = forecast.days.first?.precip ?? 0
        let items = [(L.s("weather_wind"), L.s("weather_wind_value", forecast.now.windKmh)),
                     (L.s("weather_humidity"), L.s("weather_percent", forecast.now.humidity)),
                     (L.s("weather_precip"), formatPrecip(rain))]
        return HStack(spacing: 8) {
            ForEach(items.indices, id: \.self) { index in
                let (title, value) = items[index]
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.caption).foregroundStyle(Color.muted)
                    Text(value).font(.headline).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.7)
                }
                .card(radius: 18)
                .accessibilityElement(children: .combine)
            }
        }
    }

    @ViewBuilder private func hours(_ forecast: Forecast) -> some View {
        if forecast.hours.count >= 2 {
            Text(L.s("weather_next_hours")).sectionTitle()
            ScrollView(.horizontal, showsIndicators: false) {
                HourlyChart(hours: forecast.hours).padding(.horizontal, 4)
            }
            .padding(.vertical, 6)
            .background(LinearGradient(colors: [Color(argb: 0xDD242F58), Color(argb: 0xD137204F)], startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color(argb: 0x748C8CD8)))
            .accessibilityElement()
            .accessibilityLabel(forecast.hours.map { "\(L.clock($0.time)), \(Int($0.temp.rounded()))°, \($0.sky.label)" }.joined(separator: "; "))
        }
    }

    @ViewBuilder private func days(_ forecast: Forecast) -> some View {
        if !forecast.days.isEmpty {
            let weekLow = forecast.days.map(\.min).min()!
            let weekHigh = forecast.days.map(\.max).max()!
            let today = Day.today()
            Text(L.s("weather_days")).sectionTitle()
            VStack(spacing: 0) {
                ForEach(Array(forecast.days.enumerated()), id: \.offset) { index, day in
                    if index > 0 { Divider().overlay(Color(argb: 0x338C8CD8)) }
                    let name = day.date == today ? L.s("weather_today") : day.date == today.adding(days: 1) ? L.s("weather_tomorrow") : L.weekday(day.date.weekday)
                    HStack(spacing: 6) {
                        Text(name).font(.subheadline.weight(index == 0 ? .semibold : .regular)).foregroundStyle(.white).lineLimit(1)
                            .frame(width: 92, alignment: .leading)
                        WeatherGlyph(sky: day.sky, night: false).frame(width: 34, height: 34)
                        Text(day.precip >= 0.5 ? formatPrecip(day.precip) : "").font(.caption).foregroundStyle(Color(hex: 0x8FD0FF)).frame(width: 52)
                        Text("\(Int(day.min.rounded()))°").foregroundStyle(Color.muted).frame(width: 34, alignment: .trailing)
                        TemperatureRange(weekLow: weekLow, weekHigh: weekHigh, low: day.min, high: day.max).padding(.horizontal, 6)
                        Text("\(Int(day.max.rounded()))°").fontWeight(.semibold).foregroundStyle(.white).frame(width: 34, alignment: .leading)
                    }
                    .frame(minHeight: 54)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(name), \(day.sky.label), " + L.s("weather_high_low", "\(Int(day.max.rounded()))°", "\(Int(day.min.rounded()))°"))
                }
            }
            .card()
        }
    }

    private var footer: some View {
        VStack(spacing: 8) {
            if WeatherClient.loading {
                Text(L.s("weather_updating")).font(.footnote).foregroundStyle(Color.gold)
            } else if let failure {
                Text(L.s(failure == .offline ? "weather_offline" : "weather_failed")).font(.footnote).foregroundStyle(Color.gold)
            }
            Text(L.s("weather_updated", WeatherStore.fetchedAt.map(L.clock) ?? "–")).font(.caption).foregroundStyle(Color.muted)
            HStack(spacing: 10) {
                Button(L.s("weather_change_city")) { startPicking() }.bengStyle(.secondary)
                Button(L.s("weather_refresh")) { refresh(force: true) }.bengStyle(.secondary).disabled(WeatherClient.loading)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 12)
    }
}
