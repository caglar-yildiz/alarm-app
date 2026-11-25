import AVFoundation
import AudioToolbox

/// Plays the alarm tone inside the app while a mission is open (the system alarm sound stops when the app opens),
/// for previews in the editor, and the morning briefing voice.
@MainActor
final class AlarmAudio {
    static let shared = AlarmAudio()

    private var player: AVAudioPlayer?
    private var ramp: Timer?
    private var stopTimer: Timer?
    private var vibration: Timer?
    private let speech = AVSpeechSynthesizer()

    /// Plays [tone] in a loop. With [crescendo] it starts quiet and grows to full volume over 30 seconds.
    /// [limit] stops it after that many seconds (previews).
    @discardableResult
    func play(tone: String?, crescendo: Bool, loop: Bool = true, limit: TimeInterval? = nil, vibrate: Bool = false) -> Bool {
        stop()
        let session = AVAudioSession.sharedInstance()
        // Playback ignores the silent switch, like an alarm.
        try? session.setCategory(.playback, mode: .default, options: [])
        try? session.setActive(true)
        let name = tone ?? BuiltInTone.all[0].id
        guard let url = Bundle.main.url(forResource: name, withExtension: "wav"),
              let player = try? AVAudioPlayer(contentsOf: url) else { return false }
        player.numberOfLoops = loop ? -1 : 0
        player.volume = crescendo ? 0.08 : 1
        player.play()
        self.player = player
        if crescendo {
            ramp = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] timer in
                Task { @MainActor in
                    guard let player = self?.player else { timer.invalidate(); return }
                    player.volume = min(1, player.volume + (1 - 0.08) / 30)
                    if player.volume >= 1 { timer.invalidate() }
                }
            }
        }
        if let limit {
            stopTimer = Timer.scheduledTimer(withTimeInterval: limit, repeats: false) { [weak self] _ in
                Task { @MainActor in self?.stop() }
            }
        }
        if vibrate {
            vibration = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: true) { _ in
                AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
            }
        }
        return true
    }

    func stop() {
        ramp?.invalidate()
        stopTimer?.invalidate()
        vibration?.invalidate()
        ramp = nil
        stopTimer = nil
        vibration = nil
        player?.stop()
        player = nil
    }

    var playing: Bool { player?.isPlaying == true }

    func speak(_ text: String) {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: AppLanguage.resolved == "zh-Hant" ? "zh-TW" : AppLanguage.resolved)
        speech.speak(utterance)
    }

    func stopSpeaking() { speech.stopSpeaking(at: .immediate) }
}
