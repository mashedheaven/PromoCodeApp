import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Where the backend lives. Blank values mean local-only mode.
public struct SupabaseConfig: Sendable, Equatable {
    public var url: String
    public var anonKey: String
    public init(url: String, anonKey: String) {
        self.url = url.trimmingCharacters(in: .whitespacesAndNewlines)
        self.anonKey = anonKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    public var isConfigured: Bool { url.hasPrefix("https://") && !anonKey.isEmpty && URL(string: url) != nil }
    var base: URL { URL(string: url.hasSuffix("/") ? String(url.dropLast()) : url)! }
}

public struct Session: Codable, Equatable, Sendable {
    public var accessToken: String
    public var refreshToken: String
    /// Epoch millis after which the access token is no longer valid.
    public var expiresAt: Int64
    public var userId: String
    public var email: String
    public init(accessToken: String, refreshToken: String, expiresAt: Int64, userId: String, email: String) {
        self.accessToken = accessToken; self.refreshToken = refreshToken; self.expiresAt = expiresAt
        self.userId = userId; self.email = email
    }
}

/// Holds the signed-in session (Keychain on iOS).
public protocol SessionStore: AnyObject, Sendable {
    var session: Session? { get }
    func set(_ session: Session?)
}

public final class InMemorySessionStore: SessionStore, @unchecked Sendable {
    private let lock = NSLock()
    private var value: Session?
    public init(_ session: Session? = nil) { value = session }
    public var session: Session? { lock.lock(); defer { lock.unlock() }; return value }
    public func set(_ session: Session?) { lock.lock(); value = session; lock.unlock() }
}

/// Small persistent key-value storage (UserDefaults on iOS).
public protocol KeyValueStore: AnyObject {
    func string(_ key: String) -> String?
    func set(_ value: String?, for key: String)
}

public final class InMemoryKeyValueStore: KeyValueStore {
    public var values: [String: String] = [:]
    public init() {}
    public func string(_ key: String) -> String? { values[key] }
    public func set(_ value: String?, for key: String) { values[key] = value }
}

public protocol HTTPTransport: Sendable {
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

public struct URLSessionTransport: HTTPTransport {
    let session: URLSession
    public init(session: URLSession = .shared) { self.session = session }
    public func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw SupabaseError.network("No HTTP response") }
        return (data, http)
    }
}

public enum SupabaseError: Error, Equatable, LocalizedError {
    case notConfigured
    case notSignedIn
    case network(String)
    case http(status: Int, message: String)
    case invalidResponse

    public var errorDescription: String? {
        switch self {
        case .notConfigured: return "Cloud sync isn't set up in this build."
        case .notSignedIn: return "Sign in to sync."
        case .network: return "Can't reach the server. Check your connection and try again."
        case .http(let status, let message): return message.isEmpty ? "The server rejected the request (\(status))." : message
        case .invalidResponse: return "Unexpected response from the server."
        }
    }

    /// Worth retrying later (as opposed to a request that will never succeed).
    public var isTransient: Bool {
        switch self {
        case .network: return true
        case .http(let status, _): return status == 408 || status == 429 || status >= 500
        default: return false
        }
    }
}

/// Supabase Auth (GoTrue) and REST (PostgREST) over plain HTTP. Row-level
/// security scopes every request to the signed-in user.
public final class SupabaseClient: @unchecked Sendable {
    public let config: SupabaseConfig
    public let sessions: SessionStore
    let transport: HTTPTransport
    let clock: Clock
    private let refresher = RefreshGate()

    public init(config: SupabaseConfig, sessions: SessionStore, transport: HTTPTransport = URLSessionTransport(), clock: Clock = SystemClock()) {
        self.config = config; self.sessions = sessions; self.transport = transport; self.clock = clock
    }

    // MARK: auth

    struct Credentials: Encodable { let email: String; let password: String }
    struct AuthUser: Decodable { let id: String?; let email: String? }
    struct AuthResponse: Decodable {
        let access_token: String?
        let refresh_token: String?
        let expires_in: Int64?
        let user: AuthUser?
        let id: String?
        let email: String?

        func session(now: Int64, fallbackEmail: String) -> Session? {
            guard let access_token, let refresh_token, let userId = user?.id ?? id else { return nil }
            return Session(accessToken: access_token, refreshToken: refresh_token,
                           expiresAt: now + (expires_in ?? 3600) * 1000, userId: userId,
                           email: user?.email ?? email ?? fallbackEmail)
        }
    }

    /// Returns nil when the account was created but must confirm its e-mail first.
    public func signUp(email: String, password: String) async throws -> Session? {
        let data = try await send(path: "auth/v1/signup", method: "POST", body: Credentials(email: email, password: password), auth: .anon)
        return try decode(AuthResponse.self, data).session(now: clock.millis, fallbackEmail: email)
    }

    public func signIn(email: String, password: String) async throws -> Session {
        let data = try await send(path: "auth/v1/token", query: [("grant_type", "password")], method: "POST",
                                  body: Credentials(email: email, password: password), auth: .anon)
        guard let s = try decode(AuthResponse.self, data).session(now: clock.millis, fallbackEmail: email) else {
            throw SupabaseError.invalidResponse
        }
        return s
    }

    public func sendPasswordReset(email: String) async throws {
        _ = try await send(path: "auth/v1/recover", method: "POST", body: ["email": email], auth: .anon)
    }

    public func signOut(_ session: Session) async throws {
        _ = try await send(path: "auth/v1/logout", method: "POST", body: Optional<String>.none, auth: .token(session.accessToken))
    }

    /// Exchanges the refresh token, once per stale access token even under concurrency.
    func refresh(stale: String?) async throws -> Session {
        try await refresher.run { [self] in
            guard let current = sessions.session else { throw SupabaseError.notSignedIn }
            // Someone else refreshed while we waited.
            if let stale, current.accessToken != stale { return current }
            do {
                let data = try await send(path: "auth/v1/token", query: [("grant_type", "refresh_token")], method: "POST",
                                          body: ["refresh_token": current.refreshToken], auth: .anon)
                guard let next = try decode(AuthResponse.self, data).session(now: clock.millis, fallbackEmail: current.email) else {
                    throw SupabaseError.invalidResponse
                }
                sessions.set(next)
                return next
            } catch SupabaseError.http(let status, let message) where (400...403).contains(status) {
                // The refresh token itself was rejected: the session is over.
                sessions.set(nil)
                throw SupabaseError.http(status: 401, message: message.isEmpty ? "Your session has ended. Please sign in again." : message)
            }
        }
    }

    // MARK: rest

    public func pull<Row: RemoteRow>(_ table: String, after cursor: String?, limit: Int) async throws -> [Row] {
        var query = [("select", "*"), ("order", "server_updated_at.asc"), ("limit", String(limit))]
        if let cursor { query.append(("server_updated_at", "gt.\(cursor)")) }
        let data = try await authorized { try await self.send(path: "rest/v1/\(table)", query: query, method: "GET", body: Optional<String>.none, auth: .token($0)) }
        return try decode([Row].self, data)
    }

    /// Upsert keyed by [conflictKey]; the database keeps the newer `updated_at`.
    public func push<Row: Encodable>(_ table: String, rows: [Row], conflictKey: String = "id") async throws {
        guard !rows.isEmpty else { return }
        _ = try await authorized {
            try await self.send(path: "rest/v1/\(table)", query: [("on_conflict", conflictKey)], method: "POST", body: rows,
                                auth: .token($0), headers: ["Prefer": "resolution=merge-duplicates,return=minimal"])
        }
    }

    /// Runs [call] with a fresh token; on 401 refreshes once and retries.
    private func authorized(_ call: (String) async throws -> Data) async throws -> Data {
        guard config.isConfigured else { throw SupabaseError.notConfigured }
        guard var session = sessions.session else { throw SupabaseError.notSignedIn }
        if session.expiresAt - clock.millis < 60_000 { session = (try? await refresh(stale: session.accessToken)) ?? session }
        do {
            return try await call(session.accessToken)
        } catch SupabaseError.http(let status, _) where status == 401 {
            let fresh = try await refresh(stale: session.accessToken)
            return try await call(fresh.accessToken)
        }
    }

    // MARK: plumbing

    enum Auth { case anon, token(String) }

    private func send<Body: Encodable>(
        path: String, query: [(String, String)] = [], method: String, body: Body?, auth: Auth, headers: [String: String] = [:]
    ) async throws -> Data {
        guard config.isConfigured else { throw SupabaseError.notConfigured }
        var components = URLComponents(url: config.base.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty {
            components.queryItems = query.map { URLQueryItem(name: $0.0, value: $0.1) }
            // "+" in timestamps (e.g. +00:00) must not be read as a space.
            components.percentEncodedQuery = components.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        }
        var request = URLRequest(url: components.url!)
        request.httpMethod = method
        request.timeoutInterval = 30
        request.setValue(config.anonKey, forHTTPHeaderField: "apikey")
        switch auth {
        case .anon: request.setValue("Bearer \(config.anonKey)", forHTTPHeaderField: "Authorization")
        case .token(let t): request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }
        for (k, v) in headers { request.setValue(v, forHTTPHeaderField: k) }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(body)
        }
        let data: Data, response: HTTPURLResponse
        do {
            (data, response) = try await transport.send(request)
        } catch let e as SupabaseError {
            throw e
        } catch {
            throw SupabaseError.network(error.localizedDescription)
        }
        guard (200..<300).contains(response.statusCode) else {
            throw SupabaseError.http(status: response.statusCode, message: Self.message(from: data))
        }
        return data
    }

    private func decode<T: Decodable>(_ type: T.Type, _ data: Data) throws -> T {
        do { return try JSONDecoder().decode(type, from: data) } catch { throw SupabaseError.invalidResponse }
    }

    static func message(from data: Data) -> String {
        struct Body: Decodable { let msg: String?; let message: String?; let error_description: String?; let error: String? }
        guard let b = try? JSONDecoder().decode(Body.self, from: data) else { return "" }
        return b.msg ?? b.error_description ?? b.message ?? b.error ?? ""
    }
}

/// Serializes token refreshes so concurrent 401s trigger a single refresh.
actor RefreshGate {
    private var inFlight: Task<Session, Error>?
    func run(_ work: @escaping @Sendable () async throws -> Session) async throws -> Session {
        if let inFlight { return try await inFlight.value }
        let task = Task { try await work() }
        inFlight = task
        defer { inFlight = nil }
        return try await task.value
    }
}
