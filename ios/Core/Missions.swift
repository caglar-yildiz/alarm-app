import Foundation

struct MathQuestion: Hashable {
    var expression: String
    var answer: Int
}

enum MathResult { case incorrect, next, complete }

final class MathMission {
    private(set) var questions: [MathQuestion] = []
    private var solved = 0
    let total: Int

    init<G: RandomNumberGenerator>(_ settings: MathSettings, random: inout G) {
        total = min(max(settings.count, MathSettings.counts.lowerBound), MathSettings.counts.upperBound)
        while questions.count < total {
            let question = Self.generate(settings.difficulty, &random)
            if !questions.contains(where: { $0.expression == question.expression }) { questions.append(question) }
        }
    }

    convenience init(_ settings: MathSettings) {
        var random = SystemRandomNumberGenerator()
        self.init(settings, random: &random)
    }

    var question: MathQuestion? { solved < questions.count ? questions[solved] : nil }
    var current: Int { min(solved + 1, total) }

    func submit(_ answer: Int?) -> MathResult {
        guard let current = question else { return .complete }
        guard answer == current.answer else { return .incorrect }
        solved += 1
        return question == nil ? .complete : .next
    }

    private static func generate<G: RandomNumberGenerator>(_ difficulty: MathDifficulty, _ random: inout G) -> MathQuestion {
        switch difficulty {
        case .easy:
            let a = Int.random(in: 1..<10, using: &random), b = Int.random(in: 1..<10, using: &random)
            return MathQuestion(expression: "\(a) + \(b)", answer: a + b)
        case .medium:
            let a = Int.random(in: 12..<50, using: &random), b = Int.random(in: 8..<40, using: &random)
            return MathQuestion(expression: "\(a) + \(b)", answer: a + b)
        case .hard:
            let a = Int.random(in: 3..<13, using: &random), b = Int.random(in: 3..<13, using: &random)
            let c = Int.random(in: 10..<40, using: &random)
            return MathQuestion(expression: "(\(a) × \(b)) + \(c)", answer: a * b + c)
        }
    }
}

/// Each shape appears four times; match the four distinct targets in order.
final class ShapeMission {
    static let symbols = ["●", "▲", "■", "◆"]
    static let nameKeys = ["shape_circle", "shape_triangle", "shape_square", "shape_diamond"]

    let board: [Int]
    let targets: [Int]
    private(set) var selected: Set<Int> = []

    init<G: RandomNumberGenerator>(random: inout G) {
        board = (0..<16).map { $0 % 4 }.shuffled(using: &random)
        targets = Array(0...3).shuffled(using: &random)
    }

    convenience init() {
        var random = SystemRandomNumberGenerator()
        self.init(random: &random)
    }

    var progress: Int { selected.count }
    var complete: Bool { progress == targets.count }

    func pick(_ index: Int) -> Bool {
        guard !complete, board.indices.contains(index), !selected.contains(index), board[index] == targets[progress] else {
            return false
        }
        selected.insert(index)
        return true
    }
}

/// The non-math missions: memory sequence, typing, shake, QR, shapes and walking.
final class ExtraMission {
    static let shakeCount = 12

    let task: AlarmTask
    let qrCode: String
    let phrase: String
    let stepGoal: Int
    let shapes = ShapeMission()
    let sequence: [Int]
    private(set) var progress = 0
    private(set) var complete = false

    private var detectorSteps = 0
    private var counterBase: Double?
    private var counterSteps = 0

    init(task: AlarmTask, qrCode: String, phrase: String, stepGoal: Int = StepGoal.default) {
        self.task = task
        self.qrCode = qrCode
        self.phrase = phrase
        self.stepGoal = StepGoal.coerce(stepGoal)
        sequence = (0..<4).map { _ in Int.random(in: 1...4) }
    }

    /// A random phrase for the typing mission, in the app language.
    static func randomPhrase() -> String {
        let count = Int(L.raw("typing_phrases_count")) ?? 0
        guard count > 0 else { return "BENG" }
        return L.raw("typing_phrases_\(Int.random(in: 0..<count))")
    }

    func tap(_ number: Int) -> Bool {
        if complete { return true }
        guard sequence[progress] == number else {
            progress = 0
            return false
        }
        progress += 1
        complete = progress == sequence.count
        return true
    }

    func resetSequence() { if !complete { progress = 0 } }

    func pickShape(_ index: Int) -> Bool {
        let correct = shapes.pick(index)
        complete = shapes.complete
        return correct
    }

    func type(_ text: String) -> Bool {
        complete = text.trimmingCharacters(in: .whitespacesAndNewlines) == phrase
        return complete
    }

    func shake() {
        guard !complete else { return }
        progress += 1
        complete = progress == Self.shakeCount
    }

    /// One step event (instant, but may miss steps).
    func step() {
        detectorSteps += 1
        updateSteps()
    }

    /// The pedometer's running total since the mission started; it catches up late but accurately.
    func stepTotal(_ total: Double) {
        let base = counterBase ?? total
        counterBase = base
        counterSteps = max(0, Int(total - base))
        updateSteps()
    }

    private func updateSteps() {
        guard task == .steps, !complete else { return }
        progress = min(max(progress, detectorSteps, counterSteps), stepGoal)
        complete = progress >= stepGoal
    }

    func scan(_ text: String) -> Bool {
        complete = !qrCode.isEmpty && text == qrCode
        return complete
    }
}

/// Parsing for typed times and dates.
enum TypedInput {
    enum Field { case day, month, year }

    enum DateResult: Equatable {
        case ok(Day)
        case invalid
        case tooEarly
    }

    /// "7" and "07" are both fine; anything outside 0–23 / 0–59 is rejected.
    static func time(_ hour: String, _ minute: String) -> ClockTime? {
        guard let h = Int(hour.trimmingCharacters(in: .whitespaces)) else { return nil }
        let trimmed = minute.trimmingCharacters(in: .whitespaces)
        guard let m = Int(trimmed.isEmpty ? "0" : trimmed), (0...23).contains(h), (0...59).contains(m) else { return nil }
        return ClockTime(hour: h, minute: m)
    }

    /// A two-digit year means this century ("27" is 2027). Dates before [earliest] are refused.
    static func date(_ day: String, _ month: String, _ year: String, earliest: Day) -> DateResult {
        guard let d = Int(day.trimmingCharacters(in: .whitespaces)), let m = Int(month.trimmingCharacters(in: .whitespaces)) else {
            return .invalid
        }
        let yearText = year.trimmingCharacters(in: .whitespaces)
        guard var y = Int(yearText.isEmpty ? String(earliest.year) : yearText) else { return .invalid }
        if (0...99).contains(y) { y += 2000 }
        guard Day.isValid(year: y, month: m, day: d) else { return .invalid }
        let date = Day(year: y, month: m, day: d)
        return date < earliest ? .tooEarly : .ok(date)
    }

    /// Day, month and year in the order people write them in [locale].
    static func dateOrder(_ locale: Locale) -> [Field] {
        let pattern = DateFormatter.dateFormat(fromTemplate: "yMd", options: 0, locale: locale) ?? "d.M.y"
        let positions: [(Field, String.Index?)] = [(.day, pattern.firstIndex(of: "d")), (.month, pattern.firstIndex(of: "M")),
                                                    (.year, pattern.firstIndex(of: "y"))]
        guard positions.allSatisfy({ $0.1 != nil }) else { return [.day, .month, .year] }
        return positions.sorted { $0.1! < $1.1! }.map(\.0)
    }

    static func separator(_ locale: Locale) -> String {
        let pattern = DateFormatter.dateFormat(fromTemplate: "yMd", options: 0, locale: locale) ?? "/"
        return pattern.first { "./-".contains($0) }.map(String.init) ?? "/"
    }
}
