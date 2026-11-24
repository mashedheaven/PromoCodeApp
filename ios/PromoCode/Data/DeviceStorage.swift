import Foundation
import Security
import PromoCodeCore

/// Small values in UserDefaults: settings, sync cursors, alert state.
final class Preferences: KeyValueStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    func string(_ key: String) -> String? { defaults.string(forKey: key) }
    func set(_ value: String?, for key: String) { defaults.set(value, forKey: key) }

    func decode<T: Decodable>(_ type: T.Type, _ key: String) -> T? {
        defaults.data(forKey: key).flatMap { try? JSONDecoder().decode(type, from: $0) }
    }

    func encode<T: Encodable>(_ value: T, _ key: String) {
        defaults.set(try? JSONEncoder().encode(value), forKey: key)
    }

    var settings: Settings {
        get { decode(Settings.self, "settings") ?? Settings() }
        set { encode(newValue.clamped(), "settings") }
    }

    var alertStates: [String: PlaceAlertState] {
        get { decode([String: PlaceAlertState].self, "alertStates") ?? [:] }
        set { encode(newValue, "alertStates") }
    }

    var reminderLedger: ReminderLedger {
        get { decode(ReminderLedger.self, "reminderLedger") ?? ReminderLedger() }
        set { encode(newValue, "reminderLedger") }
    }

    /// Whether the user has seen the "Allow all the time" step (iOS asks only once).
    var askedForAlwaysLocation: Bool {
        get { defaults.bool(forKey: "askedAlways") }
        set { defaults.set(newValue, forKey: "askedAlways") }
    }
}

/// The signed-in session, kept in the Keychain on this device only. It stays
/// readable after first unlock so region alerts can sync in the background.
final class KeychainSessionStore: SessionStore, @unchecked Sendable {
    private let service = "com.promocodeapp.ios.session"
    private let lock = NSLock()
    private var cached: Session?
    private var loaded = false

    var session: Session? {
        lock.lock(); defer { lock.unlock() }
        if !loaded {
            cached = read()
            loaded = true
        }
        return cached
    }

    func set(_ session: Session?) {
        lock.lock(); defer { lock.unlock() }
        cached = session
        loaded = true
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service]
        SecItemDelete(query as CFDictionary)
        guard let session, let data = try? JSONEncoder().encode(session) else { return }
        var item = query
        item[kSecValueData as String] = data
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }

    private func read() -> Session? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(Session.self, from: data)
    }
}

extension SupabaseConfig {
    /// Read from Info.plist, which the build fills from Config/Secrets.xcconfig.
    static var fromBundle: SupabaseConfig {
        let host = (Bundle.main.object(forInfoDictionaryKey: "SupabaseHost") as? String ?? "")
            .trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: "https://", with: "")
        let key = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String ?? ""
        return SupabaseConfig(url: host.isEmpty ? "" : "https://\(host)", anonKey: key)
    }
}
