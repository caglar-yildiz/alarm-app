import XCTest
@testable import BENG

/// Ports of the Android unit tests: the same rules must hold on both platforms.
final class CoreTests: XCTestCase {
    override func setUp() {
        super.setUp()
        Cal.zone = TimeZone(identifier: "Europe/Istanbul")!
    }

    override func tearDown() {
        Cal.zone = .current
        super.tearDown()
    }

    private func at(_ iso: String, _ hour: Int, _ minute: Int = 0) -> Date {
        Day(iso: iso)!.at(ClockTime(hour: hour, minute: minute))
    }

    private func resource(_ name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle(for: CoreTests.self).url(forResource: name, withExtension: "json"))
        return try Data(contentsOf: url)
    }

    // MARK: Vacation and skip

    private let weekdays = Alarm(time: ClockTime(hour: 7, minute: 0), days: Set(1...5))
    // Saturday 21 November 2026, 22:00.
    private var saturdayNight: Date { at("2026-11-21", 22) }

    func testNoVacationRingsOnTheNextWeekday() {
        XCTAssertEqual(Day(nextOutside(weekdays, saturdayNight, nil)!), Day(iso: "2026-11-23"))
    }

    func testVacationStartingLaterLeavesTheNextRingAlone() {
        let vacation = Vacation(from: Day(iso: "2026-11-25")!, until: Day(iso: "2026-11-30")!)
        XCTAssertEqual(Day(nextOutside(weekdays, saturdayNight, vacation)!), Day(iso: "2026-11-23"))
    }

    func testRingsAgainOnTheFirstWeekdayAfterTheVacation() {
        let vacation = Vacation(from: Day(iso: "2026-11-23")!, until: Day(iso: "2026-11-27")!)
        XCTAssertEqual(Day(nextOutside(weekdays, saturdayNight, vacation)!), Day(iso: "2026-11-30"))
    }

    func testOneTimeAlarmsStillRingDuringAVacation() {
        let once = Alarm(time: ClockTime(hour: 9, minute: 0), date: Day(iso: "2026-11-24"))
        let vacation = Vacation(from: Day(iso: "2026-11-23")!, until: Day(iso: "2026-11-27")!)
        XCTAssertEqual(Day(nextOutside(once, saturdayNight, vacation)!), Day(iso: "2026-11-24"))
    }

    func testSkipAndVacationTogether() {
        var skipped = weekdays
        skipped.skipDate = Day(iso: "2026-11-23")
        let vacation = Vacation(from: Day(iso: "2026-11-24")!, until: Day(iso: "2026-11-26")!)
        XCTAssertEqual(Day(nextOutside(skipped, saturdayNight, vacation)!), Day(iso: "2026-11-27"))
    }

    func testWeekdaysAreIso() {
        XCTAssertEqual(Day(iso: "2026-11-23")!.weekday, 1)
        XCTAssertEqual(Day(iso: "2026-11-22")!.weekday, 7)
    }

    // MARK: Wake statistics

    private let today = Day(iso: "2026-11-20")!
    private func entry(_ daysAgo: Int, snoozes: Int = 0, rerung: Bool = false, missed: Bool = false, seconds: Int = 30) -> WakeEntry {
        WakeEntry(date: today.adding(days: -daysAgo), alarmId: "a", snoozes: snoozes, seconds: seconds, rerung: rerung, missed: missed,
                  at: Date(timeIntervalSince1970: 0))
    }

    func testStreakCountsConsecutiveOnTimeDays() {
        XCTAssertEqual(WakeMath.streak([entry(0), entry(1), entry(2), entry(3, snoozes: 1)], today), 3)
    }

    func testDaysWithoutAlarmsDoNotBreakTheStreak() {
        XCTAssertEqual(WakeMath.streak([entry(3), entry(0)], today), 2)
        XCTAssertEqual(WakeMath.streak([entry(1), entry(2)], today), 2)
    }

    func testRerungOrMissedDaysAreLate() {
        XCTAssertEqual(WakeMath.streak([entry(1), entry(0, rerung: true)], today), 0)
        XCTAssertEqual(WakeMath.dayResult([entry(0, missed: true)], today), .late)
        XCTAssertEqual(WakeMath.dayResult([entry(0, snoozes: 2), entry(0)], today), .onTime)
    }

    func testSummaryCoversThirtyDaysAndTheLastWeek() {
        let summary = WakeMath.summary([entry(40), entry(5, snoozes: 2, seconds: 90), entry(1, seconds: 30), entry(0, missed: true)], today)
        XCTAssertEqual(summary.onTimeDays, 1)
        XCTAssertEqual(summary.alarmDays, 3)
        XCTAssertEqual(summary.snoozes, 2)
        XCTAssertEqual(summary.averageSeconds, 60)
        XCTAssertEqual(summary.week.count, 7)
        XCTAssertEqual(summary.week.last?.0, today)
        XCTAssertEqual(summary.week.last?.1, .late)
        XCTAssertEqual(summary.total, 4)
        XCTAssertEqual(WakeMath.bestStreak([entry(6), entry(5), entry(4), entry(3, snoozes: 1), entry(2), entry(1)]), 3)
    }

    func testReviewPromptRules() {
        let day: TimeInterval = 86400
        let now = Date(timeIntervalSince1970: 1000 * day)
        func summary(_ streak: Int, _ total: Int) -> WakeSummary {
            WakeSummary(streak: streak, bestStreak: streak, onTimeDays: streak, alarmDays: streak, snoozes: 0, averageSeconds: 30, week: [], total: total)
        }
        XCTAssertTrue(ReviewPrompt.eligible(summary(3, 5), installedAt: now - 10 * day, lastAsked: .distantPast, now: now))
        XCTAssertFalse(ReviewPrompt.eligible(summary(2, 9), installedAt: now - 10 * day, lastAsked: .distantPast, now: now))
        XCTAssertFalse(ReviewPrompt.eligible(summary(3, 4), installedAt: now - 10 * day, lastAsked: .distantPast, now: now))
        XCTAssertFalse(ReviewPrompt.eligible(summary(5, 9), installedAt: now - 2 * day, lastAsked: .distantPast, now: now))
        XCTAssertFalse(ReviewPrompt.eligible(summary(5, 9), installedAt: now - 300 * day, lastAsked: now - 30 * day, now: now))
        XCTAssertTrue(ReviewPrompt.eligible(summary(5, 9), installedAt: now - 300 * day, lastAsked: now - 121 * day, now: now))
    }

    // MARK: Missions

    func testShapeBoardsAreSolvable() {
        for seed in 0..<100 {
            var random = SeededGenerator(seed: UInt64(seed + 1))
            let mission = ShapeMission(random: &random)
            XCTAssertEqual(mission.board.count, 16)
            XCTAssertEqual(Set(mission.targets), [0, 1, 2, 3])
            for shape in 0...3 { XCTAssertEqual(mission.board.filter { $0 == shape }.count, 4) }
            for target in mission.targets { XCTAssertTrue(mission.pick(mission.board.firstIndex(of: target)!)) }
            XCTAssertTrue(mission.complete)
            XCTAssertFalse(mission.pick(0))
        }
    }

    func testWrongShapeTapsDoNotAdvance() {
        var random = SeededGenerator(seed: 7)
        let mission = ShapeMission(random: &random)
        XCTAssertFalse(mission.pick(-1))
        XCTAssertFalse(mission.pick(16))
        XCTAssertFalse(mission.pick(mission.board.firstIndex { $0 != mission.targets[0] }!))
        let first = mission.board.firstIndex(of: mission.targets[0])!
        XCTAssertTrue(mission.pick(first))
        XCTAssertFalse(mission.pick(first))
        XCTAssertEqual(mission.progress, 1)
    }

    func testStepCounterCatchesUp() {
        let mission = ExtraMission(task: .steps, qrCode: "", phrase: "", stepGoal: 30)
        for _ in 0..<4 { mission.step() }
        mission.stepTotal(10_000)
        XCTAssertEqual(mission.progress, 4)
        mission.stepTotal(10_026)
        XCTAssertEqual(mission.progress, 26)
        XCTAssertFalse(mission.complete)
        mission.stepTotal(10_040)
        XCTAssertTrue(mission.complete)
        XCTAssertEqual(mission.progress, 30)
        let shake = ExtraMission(task: .shake, qrCode: "", phrase: "")
        for _ in 0..<50 { shake.step() }
        XCTAssertEqual(shake.progress, 0)
    }

    func testMathMissionNeedsEveryAnswer() {
        let mission = MathMission(MathSettings(difficulty: .hard, count: 3))
        XCTAssertEqual(mission.questions.count, 3)
        XCTAssertEqual(mission.submit(-1), .incorrect)
        XCTAssertEqual(mission.submit(mission.question!.answer), .next)
        XCTAssertEqual(mission.submit(mission.question!.answer), .next)
        XCTAssertEqual(mission.submit(mission.question!.answer), .complete)
    }

    // MARK: Typed input

    func testTypedTimesAndDates() {
        let today = Day(iso: "2026-11-21")!
        XCTAssertEqual(TypedInput.time("7", "05"), ClockTime(hour: 7, minute: 5))
        XCTAssertEqual(TypedInput.time("06", ""), ClockTime(hour: 6, minute: 0))
        XCTAssertNil(TypedInput.time("24", "00"))
        XCTAssertNil(TypedInput.time("12", "60"))
        XCTAssertNil(TypedInput.time("", "30"))
        XCTAssertEqual(TypedInput.date("3", "1", "27", earliest: today), .ok(Day(iso: "2027-01-03")!))
        XCTAssertEqual(TypedInput.date("24", "12", "", earliest: today), .ok(Day(iso: "2026-12-24")!))
        XCTAssertEqual(TypedInput.date("31", "2", "2027", earliest: today), .invalid)
        XCTAssertEqual(TypedInput.date("20", "11", "2026", earliest: today), .tooEarly)
        XCTAssertEqual(TypedInput.dateOrder(Locale(identifier: "tr_TR")), [.day, .month, .year])
        XCTAssertEqual(TypedInput.dateOrder(Locale(identifier: "en_US")), [.month, .day, .year])
        XCTAssertEqual(TypedInput.dateOrder(Locale(identifier: "ja_JP")), [.year, .month, .day])
    }

    // MARK: Reminders and holidays

    func testOneTimeReminderFiresOnce() {
        let reminder = Reminder(title: "Dentist", date: Day(iso: "2026-12-03")!, time: ClockTime(hour: 14, minute: 30))
        XCTAssertEqual(reminder.nextNotice(at("2026-12-01", 8))?.at, at("2026-12-03", 14, 30))
        XCTAssertNil(reminder.nextNotice(at("2026-12-03", 15)))
    }

    func testEarlyNoticeComesBeforeTheEvent() {
        let reminder = Reminder(title: "Birthday", date: Day(iso: "2026-12-10")!, time: ClockTime(hour: 9, minute: 0), repeatRule: .yearly, leadDays: 7)
        let first = reminder.nextNotice(at("2026-12-01", 8))!
        XCTAssertTrue(first.early)
        XCTAssertEqual(first.at, at("2026-12-03", 9))
        let second = reminder.nextNotice(at("2026-12-03", 9, 1))!
        XCTAssertFalse(second.early)
        XCTAssertEqual(second.at, at("2026-12-10", 9))
    }

    func testMonthlyAndYearlyUseTheMonthsLastDay() {
        let monthly = Reminder(title: "Rent", date: Day(iso: "2026-01-31")!, time: ClockTime(hour: 12, minute: 0), repeatRule: .monthly)
        XCTAssertEqual(Day(monthly.nextEvent(at("2026-02-01", 0))!), Day(iso: "2026-02-28"))
        XCTAssertEqual(Day(monthly.nextEvent(at("2026-03-01", 0))!), Day(iso: "2026-03-31"))
        let leap = Reminder(title: "Leap", date: Day(iso: "2028-02-29")!, time: ClockTime(hour: 12, minute: 0), repeatRule: .yearly)
        XCTAssertEqual(Day(leap.nextEvent(at("2028-03-01", 0))!), Day(iso: "2029-02-28"))
    }

    func testWeeklyUsesTheChosenDays() {
        let reminder = Reminder(title: "Gym", date: Day(iso: "2026-11-16")!, time: ClockTime(hour: 18, minute: 0), repeatRule: .weekly, days: [1, 3])
        XCTAssertEqual(reminder.nextEvent(at("2026-11-18", 19)), at("2026-11-23", 18))
    }

    func testHolidaysAreMergedAndCorrected() throws {
        let holidays = try HolidayParser.parse(resource("nager_tr_2027"))
        let kurban = try XCTUnwrap(holidays.first { $0.name == "Eid al-Adha" })
        XCTAssertEqual(kurban.date, Day(iso: "2027-05-16"))
        XCTAssertEqual(kurban.days, 4)
        XCTAssertTrue(kurban.tentative)
        XCTAssertEqual(kurban.localName, "Kurban Bayramı")
        XCTAssertTrue(holidays.contains { $0.localName.hasPrefix("Atatürk") && $0.date == Day(iso: "2027-05-19") })
        let corrected = HolidayParser.correctTurkey(2027, holidays)
        let ramazan = try XCTUnwrap(corrected.first { $0.localName == "Ramazan Bayramı" })
        XCTAssertEqual(ramazan.date, Day(iso: "2027-03-09"))
        XCTAssertEqual(ramazan.days, 3)
        XCTAssertFalse(ramazan.tentative)
        XCTAssertEqual(corrected.filter { $0.localName == "Kurban Bayramı" }.count, 1)
        let german = try HolidayParser.parse(resource("nager_de_2026"))
        XCTAssertTrue(german.contains { $0.localName == "Tag der Deutschen Einheit" })
        XCTAssertFalse(german.contains { $0.localName == "Heilige Drei Könige" })
    }

    func testSpecialDaysFollowEachCountry() {
        XCTAssertEqual(SpecialDays.easter(2026), Day(iso: "2026-04-05"))
        XCTAssertEqual(SpecialDays.easter(2027), Day(iso: "2027-03-28"))
        XCTAssertEqual(SpecialDays.easter(2030), Day(iso: "2030-04-21"))
        XCTAssertEqual(SpecialDays.mothersDay("TR", 2026), Day(iso: "2026-05-10"))
        XCTAssertEqual(SpecialDays.fathersDay("TR", 2026), Day(iso: "2026-06-21"))
        XCTAssertEqual(SpecialDays.mothersDay("GB", 2026), Day(iso: "2026-03-15"))
        XCTAssertEqual(SpecialDays.fathersDay("DE", 2026), Day(iso: "2026-05-14"))
        XCTAssertEqual(SpecialDays.mothersDay("FR", 2026), Day(iso: "2026-05-31"))
        XCTAssertTrue(SpecialDays.forCountry("TR", 2026).contains { $0.1 == .teachers })
    }

    // MARK: Weather and places

    private func step(_ time: String, _ temp: Double, _ symbol: String?, _ precip: Double, hourly: Bool = true) -> String {
        let period = hourly ? "next_1_hours" : "next_6_hours"
        let next = symbol.map { #", "\#(period)": {"summary": {"symbol_code": "\#($0)"}, "details": {"precipitation_amount": \#(precip)}}"# } ?? ""
        return #"{"time": "\#(time)", "data": {"instant": {"details": {"air_temperature": \#(temp), "relative_humidity": 81.4, "wind_speed": 5.0}}\#(next)}}"#
    }

    private func forecast(_ steps: String...) -> Data {
        Data(#"{"properties": {"meta": {"updated_at": "2026-11-18T10:00:00Z"}, "timeseries": [\#(steps.joined(separator: ","))]}}"#.utf8)
    }

    private let istanbul = TimeZone(identifier: "Europe/Istanbul")!

    func testSkySymbols() {
        XCTAssertEqual(Sky.from(symbol: "partlycloudy_day"), .partly)
        XCTAssertEqual(Sky.from(symbol: "fair_night"), .partly)
        XCTAssertEqual(Sky.from(symbol: "heavyrainshowers_night"), .rain)
        XCTAssertEqual(Sky.from(symbol: "rainandthunder"), .thunder)
        XCTAssertEqual(Sky.from(symbol: "lightsleetshowers_day"), .sleet)
        XCTAssertEqual(Sky.from(symbol: "clearsky_polartwilight"), .clear)
        XCTAssertNil(Sky.from(symbol: "unknown"))
        XCTAssertTrue(Sky.isNight("clearsky_night"))
    }

    func testForecastStartsAtTheCurrentHour() throws {
        let json = forecast(step("2026-11-18T08:00:00Z", 10, "cloudy", 0), step("2026-11-18T09:00:00Z", 12.4, "clearsky_day", 0),
                            step("2026-11-18T10:00:00Z", 13, "rain", 1.2), step("2026-11-18T11:00:00Z", 13.5, "rain", 0.4))
        let result = try ForecastParser.parse(json, zone: istanbul, now: ISO8601DateFormatter().date(from: "2026-11-18T09:30:00Z")!)
        XCTAssertEqual(result.now.temp, 12.4)
        XCTAssertEqual(result.now.sky, .clear)
        XCTAssertEqual(result.now.windKmh, 18)
        XCTAssertEqual(result.now.humidity, 81)
        XCTAssertEqual(result.hours.count, 3)
        XCTAssertEqual(result.hours[1].precip, 1.2)
        XCTAssertEqual(result.days.first?.min, 10)
        XCTAssertEqual(result.days.first?.max, 13.5)
    }

    func testForecastGroupsDaysLocally() throws {
        let json = forecast(step("2026-11-18T20:00:00Z", 9, "clearsky_night", 0), step("2026-11-18T21:00:00Z", 8, "rain", 0, hourly: false),
                            step("2026-11-19T03:00:00Z", 6, "rain", 2, hourly: false), step("2026-11-19T09:00:00Z", 14, "lightrain", 1, hourly: false),
                            step("2026-11-19T15:00:00Z", 11, nil, 0, hourly: false))
        let result = try ForecastParser.parse(json, zone: istanbul, now: ISO8601DateFormatter().date(from: "2026-11-18T20:10:00Z")!)
        XCTAssertEqual(result.days.count, 2)
        XCTAssertEqual(result.days[1].date, Day(iso: "2026-11-19"))
        XCTAssertEqual(result.days[1].min, 6)
        XCTAssertEqual(result.days[1].max, 14)
        XCTAssertEqual(result.days[1].precip, 3)
        XCTAssertEqual(result.days[1].sky, .rain)
        XCTAssertEqual(ForecastParser.daySky([.clear, .partly, .partly, .rain], 0.1), .partly)
        XCTAssertEqual(ForecastParser.daySky([.clear, .partly, .partly, .rain], 2), .rain)
    }

    func testPlaceSearch() {
        let places = PlaceSearch.places(bundle: Bundle(for: AppModel.self))
        func titles(_ query: String, _ limit: Int = 30) -> [String] { PlaceSearch.search(places, query, limit: limit).map(\.title) }
        XCTAssertEqual(places.filter(\.isProvince).count, 81)
        XCTAssertEqual(places.filter { $0.province == "İstanbul" && !$0.isProvince }.count, 39)
        XCTAssertEqual(titles("istan").first, "İstanbul")
        XCTAssertTrue(titles("istan").dropFirst().allSatisfy { $0.hasPrefix("İstanbul / ") })
        for query in ["izmir", "İZMİR", "ızmır", "Izmir"] { XCTAssertEqual(titles(query).first, "İzmir") }
        XCTAssertEqual(titles("sanliurfa").first, "Şanlıurfa")
        XCTAssertEqual(titles("kadıköy").first, "İstanbul / Kadıköy")
        XCTAssertTrue(titles("esen", 5).contains("İstanbul / Esenyurt"))
        XCTAssertEqual(titles("istanbul esen").first, "İstanbul / Esenyurt")
        XCTAssertEqual(titles("İstanbul/Esenyurt").first, "İstanbul / Esenyurt")
        XCTAssertEqual(titles("ankara çank").first, "Ankara / Çankaya")
        XCTAssertTrue(titles("qqqq").isEmpty)
        XCTAssertTrue(titles("   ").isEmpty)
    }

    // MARK: Scheduling plan

    func testStableAlarmIds() {
        XCTAssertEqual(Shared.uuid(for: "a|1"), Shared.uuid(for: "a|1"))
        XCTAssertNotEqual(Shared.uuid(for: "a|1"), Shared.uuid(for: "a|2"))
    }

    func testEveryLanguageHasItsStrings() {
        for tag in AppLanguage.supported where !tag.isEmpty {
            let path = Bundle(for: AppModel.self).path(forResource: tag, ofType: "lproj")
            XCTAssertNotNil(path, "Missing \(tag).lproj")
        }
    }
}
