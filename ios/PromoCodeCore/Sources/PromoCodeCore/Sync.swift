import Foundation

public enum SyncOutcome: Equatable, Sendable { case synced, skipped }

/// Two-way sync between the local store and Supabase, compatible with the
/// Android app's sync engine.
///
/// Push: dirty rows are upserted; the server keeps the newer `updated_at`.
/// A row's dirty flag is cleared only if it wasn't edited during the upload.
/// Pull: rows changed since the saved cursor are applied unless a newer local
/// edit is still waiting to be pushed. Deletions travel as tombstones.
@MainActor
public final class SyncEngine {
    static let page = 200
    let client: SupabaseClient
    let store: RecordStore
    let keys: KeyValueStore
    let clock: Clock
    private var running: Task<SyncOutcome, Error>?

    public init(client: SupabaseClient, store: RecordStore, keys: KeyValueStore, clock: Clock = SystemClock()) {
        self.client = client; self.store = store; self.keys = keys; self.clock = clock
    }

    public var lastSyncAt: Date? {
        keys.string(Keys.lastSync).flatMap(Double.init).map { Date(timeIntervalSince1970: $0 / 1000) }
    }

    /// Runs a sync, or joins the one already running.
    @discardableResult
    public func sync() async throws -> SyncOutcome {
        if let running { return try await running.value }
        let task = Task { try await self.run() }
        running = task
        defer { running = nil }
        return try await task.value
    }

    private func run() async throws -> SyncOutcome {
        guard client.config.isConfigured, client.sessions.session != nil else { return .skipped }
        // Places first, so coupons arriving in the same run can resolve them.
        try await syncTable("places", read: store.places, write: store.put, remove: store.removePlace,
                            toDTO: { $0.dto() }, fromDTO: { (dto: PlaceDTO) in dto.stored() })
        try await syncTable("coupons", read: store.coupons, write: store.put, remove: store.removeCoupon,
                            toDTO: { $0.dto() }, fromDTO: { (dto: CouponDTO) in dto.stored() })
        try await syncTable("memberships", read: store.memberships, write: store.put, remove: store.removeMembership,
                            toDTO: { $0.dto() }, fromDTO: { (dto: MembershipDTO) in dto.stored() })
        if let token = keys.string(Keys.pushToken) {
            try? await client.push("device_tokens", rows: [DeviceTokenDTO(token: token, platform: "ios")], conflictKey: "token")
        }
        keys.set(String(clock.millis), for: Keys.lastSync)
        return .synced
    }

    private func syncTable<V: Syncable, D: RemoteRow>(
        _ name: String,
        read: () -> [Stored<V>], write: (Stored<V>) -> Void, remove: (String) -> Void,
        toDTO: (Stored<V>) -> D, fromDTO: (D) -> Stored<V>
    ) async throws {
        // Push in pages; remember exactly which version each upload carried.
        let dirty = read().filter(\.dirty)
        for start in stride(from: 0, to: dirty.count, by: Self.page) {
            let batch = Array(dirty[start..<min(start + Self.page, dirty.count)])
            try await client.push(name, rows: batch.map(toDTO))
            let current = Dictionary(read().map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            for sent in batch {
                guard var row = current[sent.id], row.value.updatedAt == sent.value.updatedAt else { continue }
                if row.isDeleted { remove(row.id) } else { row.dirty = false; write(row) }
            }
            try store.save()
        }

        // Pull everything changed since the cursor.
        var cursor = keys.string(Keys.cursor(name))
        while true {
            let page: [D] = try await client.pull(name, after: cursor, limit: Self.page)
            if page.isEmpty { break }
            let local = Dictionary(read().map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            for remote in page {
                if let mine = local[remote.id], mine.dirty, mine.value.updatedAt > remote.updated_at { continue }
                if remote.deleted_at != nil { remove(remote.id) } else { write(fromDTO(remote)) }
            }
            try store.save()
            guard let last = page.last?.server_updated_at else { break }
            cursor = last
            keys.set(last, for: Keys.cursor(name))
            if page.count < Self.page { break }
        }
    }

    enum Keys {
        static let lastSync = "sync.lastSyncAt"
        static let pushToken = "push.token"
        static let lastAccount = "account.lastUserId"
        static func cursor(_ table: String) -> String { "sync.cursor.\(table)" }
    }
}

public enum AuthResult: Equatable, Sendable {
    case signedIn
    /// The account exists but its e-mail must be confirmed first.
    case confirmationRequired
    case failed(String)
}

/// Sign-in and sign-out, keeping the device's data correct across accounts.
@MainActor
public final class AccountManager {
    let client: SupabaseClient
    let store: RecordStore
    let keys: KeyValueStore
    /// Called once a session exists, to upload local data straight away.
    public var onSignedIn: () -> Void = {}

    public init(client: SupabaseClient, store: RecordStore, keys: KeyValueStore) {
        self.client = client; self.store = store; self.keys = keys
    }

    public var isAvailable: Bool { client.config.isConfigured }
    public var session: Session? { client.sessions.session }

    public func signIn(email: String, password: String) async -> AuthResult {
        await attempt {
            let s = try await self.client.signIn(email: email.trimmingCharacters(in: .whitespaces), password: password)
            self.adopt(s)
            return .signedIn
        }
    }

    public func signUp(email: String, password: String) async -> AuthResult {
        await attempt {
            guard let s = try await self.client.signUp(email: email.trimmingCharacters(in: .whitespaces), password: password) else {
                return .confirmationRequired
            }
            self.adopt(s)
            return .signedIn
        }
    }

    public func sendPasswordReset(email: String) async -> AuthResult {
        await attempt {
            try await self.client.sendPasswordReset(email: email.trimmingCharacters(in: .whitespaces))
            return .confirmationRequired
        }
    }

    /// Ends the session. Data stays on the device and simply stops syncing.
    public func signOut() async {
        guard let current = session else { return }
        client.sessions.set(nil)
        try? await client.signOut(current) // best effort
    }

    func adopt(_ session: Session) {
        let previous = keys.string(SyncEngine.Keys.lastAccount)
        if let previous, previous != session.userId {
            // Another account's data must not leak into this one.
            store.removeAll()
        } else {
            // Upload everything on the device, including data made before
            // signing in; deletions the server never saw are simply dropped.
            for var c in store.coupons() { if c.isDeleted { store.removeCoupon(id: c.id) } else { c.dirty = true; store.put(c) } }
            for var p in store.places() { if p.isDeleted { store.removePlace(id: p.id) } else { p.dirty = true; store.put(p) } }
            for var m in store.memberships() { if m.isDeleted { store.removeMembership(id: m.id) } else { m.dirty = true; store.put(m) } }
        }
        try? store.save()
        for table in ["places", "coupons", "memberships"] { keys.set(nil, for: SyncEngine.Keys.cursor(table)) }
        keys.set(nil, for: SyncEngine.Keys.lastSync)
        keys.set(session.userId, for: SyncEngine.Keys.lastAccount)
        client.sessions.set(session)
        onSignedIn()
    }

    private func attempt(_ work: () async throws -> AuthResult) async -> AuthResult {
        guard client.config.isConfigured else { return .failed(SupabaseError.notConfigured.errorDescription!) }
        do { return try await work() } catch let e as SupabaseError {
            return .failed(e.errorDescription ?? "Something went wrong.")
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    public func setPushToken(_ token: String) { keys.set(token, for: SyncEngine.Keys.pushToken) }
}
