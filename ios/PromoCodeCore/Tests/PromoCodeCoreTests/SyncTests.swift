import XCTest
import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
@testable import PromoCodeCore

/// An in-process stand-in for Supabase that follows the contract in
/// supabase/schema.sql: upserts keep the newer `updated_at`, every accepted
/// write gets an increasing `server_updated_at`, and reads filter on it.
final class FakeSupabase: HTTPTransport, @unchecked Sendable {
    private let lock = NSLock()
    var tables: [String: [String: [String: Any]]] = [:]
    var requests: [URLRequest] = []
    var validToken = "access-1"
    var refreshCount = 0
    private var tick = 0

    func rows(_ table: String) -> [String: [String: Any]] { lock.lock(); defer { lock.unlock() }; return tables[table] ?? [:] }
    var postCount: Int { lock.lock(); defer { lock.unlock() }; return requests.filter { $0.httpMethod == "POST" }.count }

    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) { handle(request) }

    private func handle(_ request: URLRequest) -> (Data, HTTPURLResponse) {
        lock.lock(); defer { lock.unlock() }
        requests.append(request)
        let url = request.url!
        let comps = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        let query = Dictionary((comps.queryItems ?? []).map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { a, _ in a })
        let body = request.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) }
        func reply(_ status: Int, _ json: Any? = nil) -> (Data, HTTPURLResponse) {
            let data = json.map { try! JSONSerialization.data(withJSONObject: $0) } ?? Data()
            return (data, HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)!)
        }
        guard request.value(forHTTPHeaderField: "apikey") == "anon-key" else { return reply(401, ["message": "No API key"]) }

        switch url.path {
        case "/auth/v1/token":
            let b = body as? [String: Any] ?? [:]
            if query["grant_type"] == "refresh_token" {
                guard b["refresh_token"] as? String == "refresh-1" else { return reply(400, ["error_description": "Invalid Refresh Token"]) }
                refreshCount += 1
                validToken = "access-2"
                return reply(200, ["access_token": "access-2", "refresh_token": "refresh-1", "expires_in": 3600,
                                   "user": ["id": "user-1", "email": "a@b.co"]])
            }
            guard b["password"] as? String == "secret1" else { return reply(400, ["error_description": "Invalid login credentials"]) }
            return reply(200, ["access_token": validToken, "refresh_token": "refresh-1", "expires_in": 3600,
                               "user": ["id": "user-1", "email": "a@b.co"]])
        case "/auth/v1/signup": return reply(200, ["id": "user-2", "email": "new@b.co"])
        case "/auth/v1/logout": return reply(204)
        case "/auth/v1/recover": return reply(200, [String: Any]())
        default: break
        }

        guard request.value(forHTTPHeaderField: "Authorization") == "Bearer \(validToken)" else { return reply(401, ["message": "JWT expired"]) }
        let table = String(url.path.dropFirst("/rest/v1/".count))
        var rows = tables[table] ?? [:]
        if request.httpMethod == "POST" {
            precondition(request.value(forHTTPHeaderField: "Prefer")?.contains("resolution=merge-duplicates") == true)
            for case let row as [String: Any] in (body as? [Any] ?? []) {
                precondition(row["server_updated_at"] == nil, "clients must not send server_updated_at")
                let id = (row["id"] ?? row["token"]) as! String
                let incoming = (row["updated_at"] as? NSNumber)?.int64Value ?? 0
                let existing = (rows[id]?["updated_at"] as? NSNumber)?.int64Value
                if existing == nil || incoming >= existing! {
                    tick += 1
                    var stored = row
                    stored["server_updated_at"] = String(format: "2026-03-10T12:00:00.%06d+00:00", tick)
                    rows[id] = stored
                }
            }
            tables[table] = rows
            return reply(201)
        }
        let after = query["server_updated_at"].map { String($0.dropFirst("gt.".count)) }
        let limit = Int(query["limit"] ?? "") ?? 1000
        let result = rows.values
            .filter { after == nil || ($0["server_updated_at"] as! String) > after! }
            .sorted { ($0["server_updated_at"] as! String) < ($1["server_updated_at"] as! String) }
            .prefix(limit)
        return reply(200, Array(result))
    }
}

@MainActor
final class SyncTests: XCTestCase {
    let backend = FakeSupabase()
    let clock = TestClock()
    let config = SupabaseConfig(url: "https://example.supabase.co", anonKey: "anon-key")
    let session = Session(accessToken: "access-1", refreshToken: "refresh-1", expiresAt: Int64.max, userId: "user-1", email: "a@b.co")

    /// One simulated phone.
    @MainActor
    final class Device {
        let store = InMemoryStore()
        let keys = InMemoryKeyValueStore()
        let sessions: InMemorySessionStore
        let client: SupabaseClient
        let engine: SyncEngine
        let library: Library
        let accounts: AccountManager

        init(_ t: SyncTests, signedIn: Bool = true) {
            sessions = InMemorySessionStore(signedIn ? t.session : nil)
            client = SupabaseClient(config: t.config, sessions: sessions, transport: t.backend, clock: t.clock)
            engine = SyncEngine(client: client, store: store, keys: keys, clock: t.clock)
            library = Library(store: store, clock: t.clock)
            accounts = AccountManager(client: client, store: store, keys: keys)
        }
    }

    func testLocalChangesAreUploadedAndFlaggedClean() async throws {
        let phone = Device(self)
        phone.library.save(place(id: "p1"))
        phone.library.save(coupon(id: "c1", expiresOn: clock.today.adding(days: 5), placeIds: ["p1"]))

        let outcome = try await phone.engine.sync()
        XCTAssertEqual(outcome, .synced)

        let remote = try XCTUnwrap(backend.rows("coupons")["c1"])
        XCTAssertEqual(remote["merchant"] as? String, "Target")
        XCTAssertEqual(remote["expires_on"] as? String, clock.today.adding(days: 5).iso)
        XCTAssertEqual(remote["place_ids"] as? [String], ["p1"])
        XCTAssertTrue(remote["notes"] is NSNull)   // nulls are sent so fields can be cleared
        XCTAssertNil(remote["user_id"])            // ownership comes from the JWT
        XCTAssertEqual(Array(backend.rows("places").keys), ["p1"])
        XCTAssertEqual(phone.store.coupon("c1")?.dirty, false)
        XCTAssertNotNil(phone.engine.lastSyncAt)

        let posts = backend.postCount
        try await phone.engine.sync()
        XCTAssertEqual(backend.postCount, posts) // nothing changed, nothing uploaded
    }

    func testASecondDeviceReceivesDataAndEditsFlowBothWays() async throws {
        let phone = Device(self), tablet = Device(self)
        phone.library.save(place(id: "p1", name: "Target Downtown"))
        phone.library.save(coupon(id: "c1", placeIds: ["p1"]))
        try await phone.engine.sync()

        try await tablet.engine.sync()
        XCTAssertEqual(tablet.library.coupon("c1")?.placeIds, ["p1"])
        XCTAssertEqual(tablet.library.place("p1")?.name, "Target Downtown")
        XCTAssertEqual(tablet.store.coupon("c1")?.dirty, false)

        clock.advance(millis: 1000)
        tablet.library.setUsed("c1", true)
        try await tablet.engine.sync()
        try await phone.engine.sync()
        XCTAssertEqual(phone.library.coupon("c1")?.isUsed, true)
    }

    func testDeletionsPropagateAndArePurged() async throws {
        let phone = Device(self), tablet = Device(self)
        phone.library.save(coupon(id: "c1"))
        try await phone.engine.sync()
        try await tablet.engine.sync()

        clock.advance(millis: 1000)
        phone.library.deleteCoupon("c1")
        try await phone.engine.sync()
        XCTAssertNil(phone.store.coupon("c1"))

        try await tablet.engine.sync()
        XCTAssertTrue(tablet.library.coupons.isEmpty)
        XCTAssertNil(tablet.store.coupon("c1"))
    }

    func testTheMostRecentEditWinsOnBothDevices() async throws {
        let phone = Device(self), tablet = Device(self)
        phone.library.save(coupon(id: "c1", code: "ORIGINAL"))
        try await phone.engine.sync()
        try await tablet.engine.sync()

        clock.advance(millis: 1000)
        var p = phone.library.coupon("c1")!; p.code = "PHONE"; phone.library.save(p)
        clock.advance(millis: 1000)
        var t = tablet.library.coupon("c1")!; t.code = "TABLET"; tablet.library.save(t)

        try await tablet.engine.sync()
        try await phone.engine.sync() // the older edit must not overwrite the newer one
        XCTAssertEqual(backend.rows("coupons")["c1"]?["code"] as? String, "TABLET")
        XCTAssertEqual(phone.library.coupon("c1")?.code, "TABLET")
    }

    func testANewerUnsyncedLocalEditSurvivesPullingAnOlderRemoteVersion() async throws {
        let phone = Device(self), tablet = Device(self)
        phone.library.save(coupon(id: "c1", code: "ORIGINAL"))
        try await phone.engine.sync()
        try await tablet.engine.sync()

        clock.advance(millis: 1000)
        var t = tablet.library.coupon("c1")!; t.code = "TABLET"; tablet.library.save(t)
        try await tablet.engine.sync()
        clock.advance(millis: 1000)
        var p = phone.library.coupon("c1")!; p.code = "PHONE"; phone.library.save(p)

        try await phone.engine.sync()
        XCTAssertEqual(phone.library.coupon("c1")?.code, "PHONE")
        XCTAssertEqual(backend.rows("coupons")["c1"]?["code"] as? String, "PHONE")
    }

    func testSyncIsSkippedWhenSignedOut() async throws {
        let phone = Device(self, signedIn: false)
        phone.library.save(coupon(id: "c1"))
        let outcome = try await phone.engine.sync()
        XCTAssertEqual(outcome, .skipped)
        XCTAssertTrue(backend.requests.isEmpty)
        XCTAssertEqual(phone.store.coupon("c1")?.dirty, true)
    }

    func testExpiredTokenIsRefreshedOnceAndTheRequestRetried() async throws {
        let phone = Device(self)
        backend.validToken = "access-0" // the server no longer accepts access-1
        phone.library.save(coupon(id: "c1"))
        let outcome = try await phone.engine.sync()
        XCTAssertEqual(outcome, .synced)
        XCTAssertEqual(backend.refreshCount, 1)
        XCTAssertEqual(phone.sessions.session?.accessToken, "access-2")
        XCTAssertEqual(Array(backend.rows("coupons").keys), ["c1"])
    }

    func testARejectedRefreshTokenSignsOutWithoutLosingData() async throws {
        let phone = Device(self)
        var revoked = session; revoked.refreshToken = "revoked"
        phone.sessions.set(revoked)
        backend.validToken = "access-0"
        phone.library.save(coupon(id: "c1"))
        do {
            try await phone.engine.sync()
            XCTFail("expected failure")
        } catch let e as SupabaseError {
            if case .http(let status, _) = e { XCTAssertEqual(status, 401) } else { XCTFail("\(e)") }
        }
        XCTAssertNil(phone.sessions.session)
        XCTAssertEqual(phone.store.coupon("c1")?.dirty, true)
    }

    func testSigningInUploadsDataCreatedBeforeTheAccountExisted() async throws {
        let phone = Device(self, signedIn: false)
        phone.library.save(coupon(id: "c1"))
        var row = phone.store.coupon("c1")!; row.dirty = false; phone.store.put(row)

        let result = await phone.accounts.signIn(email: "a@b.co", password: "secret1")
        XCTAssertEqual(result, .signedIn)
        XCTAssertEqual(phone.sessions.session?.userId, "user-1")
        XCTAssertEqual(phone.store.coupon("c1")?.dirty, true)
        try await phone.engine.sync()
        XCTAssertEqual(Array(backend.rows("coupons").keys), ["c1"])
    }

    func testSignInFailuresAndUnconfirmedSignUps() async {
        let phone = Device(self, signedIn: false)
        let bad = await phone.accounts.signIn(email: "a@b.co", password: "wrong")
        XCTAssertEqual(bad, .failed("Invalid login credentials"))
        let signUp = await phone.accounts.signUp(email: "new@b.co", password: "secret1")
        XCTAssertEqual(signUp, .confirmationRequired)
        XCTAssertNil(phone.sessions.session)
    }

    func testSwitchingAccountsDoesNotLeakData() async throws {
        let phone = Device(self, signedIn: false)
        phone.keys.set("someone-else", for: SyncEngine.Keys.lastAccount)
        phone.library.save(coupon(id: "theirs"))
        _ = await phone.accounts.signIn(email: "a@b.co", password: "secret1")
        try await phone.engine.sync()
        XCTAssertTrue(phone.library.coupons.isEmpty)
        XCTAssertTrue(backend.rows("coupons").isEmpty)
    }

    func testSigningOutKeepsLocalData() async {
        let phone = Device(self)
        phone.library.save(coupon(id: "c1"))
        await phone.accounts.signOut()
        XCTAssertNil(phone.sessions.session)
        XCTAssertNotNil(phone.library.coupon("c1"))
    }

    func testNotConfiguredBuildSkipsQuietly() async throws {
        let store = InMemoryStore()
        let client = SupabaseClient(config: SupabaseConfig(url: "", anonKey: ""), sessions: InMemorySessionStore(session), transport: backend)
        let outcome = try await SyncEngine(client: client, store: store, keys: InMemoryKeyValueStore()).sync()
        XCTAssertEqual(outcome, .skipped)
        let result = await AccountManager(client: client, store: store, keys: InMemoryKeyValueStore()).signIn(email: "a", password: "b")
        XCTAssertEqual(result, .failed("Cloud sync isn't set up in this build."))
    }

    /// A row exactly as the Android app uploads it must load on iOS and come
    /// back with the same keys and values.
    func testWireFormatMatchesTheAndroidApp() async throws {
        let android = """
        {"id":"c1","code":"SAVE20","merchant":"Target","discount_type":"FIXED_AMOUNT","discount_value":5.0,
         "currency":"EUR","min_purchase":25.0,"category":"Groceries","notes":null,"expires_on":"2026-03-15",
         "barcode":"SAVE20","barcode_format":"CODE_128","is_favorite":true,"used_at":null,"is_archived":false,
         "place_ids":["p1","p2"],"created_at":1,"updated_at":2,"deleted_at":null,
         "server_updated_at":"2026-03-10T12:00:00.000001+00:00"}
        """
        let dto = try JSONDecoder().decode(CouponDTO.self, from: Data(android.utf8))
        let c = dto.stored().value
        XCTAssertEqual(c.discount, .fixedAmount(5, currency: "EUR"))
        XCTAssertEqual(c.expiresOn, Day(year: 2026, month: 3, day: 15))
        XCTAssertEqual(c.barcodeFormat, .CODE_128)
        XCTAssertEqual(c.placeIds, ["p1", "p2"])

        let back = try JSONSerialization.jsonObject(with: JSONEncoder().encode(dto.stored().dto())) as! [String: Any]
        let original = try JSONSerialization.jsonObject(with: Data(android.utf8)) as! [String: Any]
        XCTAssertEqual(Set(back.keys), Set(original.keys).subtracting(["server_updated_at"]))
        XCTAssertEqual(back["expires_on"] as? String, "2026-03-15")
        XCTAssertEqual(back["discount_type"] as? String, "FIXED_AMOUNT")

        let membershipJSON = """
        {"id":"m1","organization":"Gym","member_number":"42","type":"GYM","started_on":null,"renews_on":"2026-04-01",
         "fee":10.0,"billing_cycle":"MONTHLY","currency":"USD","notes":null,"reminder_days":7,"is_active":true,
         "place_ids":[],"created_at":1,"updated_at":2,"deleted_at":null,"server_updated_at":"x"}
        """
        let m = try JSONDecoder().decode(MembershipDTO.self, from: Data(membershipJSON.utf8)).stored().value
        XCTAssertEqual(m.type, .GYM)
        XCTAssertEqual(m.billingCycle, .MONTHLY)
        XCTAssertEqual(m.renewsOn?.iso, "2026-04-01")
    }
}
