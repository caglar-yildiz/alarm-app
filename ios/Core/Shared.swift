import Foundation
import CryptoKit

/// Storage shared by the app, its widget and its intents (App Group), with JSON for structured values.
enum Shared {
    static let groupID = "group.com.caglaryildiz.alarm"
    static let defaults: UserDefaults = UserDefaults(suiteName: groupID) ?? .standard

    static func load<T: Decodable>(_ key: String, as type: T.Type) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? decoder.decode(T.self, from: data)
    }

    static func save<T: Encodable>(_ value: T?, _ key: String) {
        guard let value, let data = try? encoder.encode(value) else {
            defaults.removeObject(forKey: key)
            return
        }
        defaults.set(data, forKey: key)
    }

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        return decoder
    }()

    /// A stable UUID for a text key, so the same scheduled occurrence always gets the same system alarm id.
    static func uuid(for key: String) -> UUID {
        var bytes = Array(SHA256.hash(data: Data(key.utf8)).prefix(16))
        bytes[6] = (bytes[6] & 0x0F) | 0x50
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
                           bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]))
    }
}

/// Tells open screens that stored data changed (alarms, reminders, Premium, ...).
extension Notification.Name {
    static let bengDataChanged = Notification.Name("bengDataChanged")
}

func notifyChanged() {
    DispatchQueue.main.async { NotificationCenter.default.post(name: .bengDataChanged, object: nil) }
}
