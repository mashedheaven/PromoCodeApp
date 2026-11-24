import Foundation

public struct LatLng: Hashable, Sendable {
    public var latitude: Double
    public var longitude: Double
    public init(_ latitude: Double, _ longitude: Double) { self.latitude = latitude; self.longitude = longitude }

    /// Great-circle distance in metres (haversine).
    public func distance(to other: LatLng) -> Double {
        let r = 6_371_000.0
        let dLat = (other.latitude - latitude) * .pi / 180
        let dLng = (other.longitude - longitude) * .pi / 180
        let a = pow(sin(dLat / 2), 2) + cos(latitude * .pi / 180) * cos(other.latitude * .pi / 180) * pow(sin(dLng / 2), 2)
        return 2 * r * asin(min(1, max(0, a)).squareRoot())
    }
}

public struct Fence: Hashable, Sendable {
    public var id: String
    public var center: LatLng
    public var radius: Double
    public init(id: String, center: LatLng, radius: Double) { self.id = id; self.center = center; self.radius = radius }
}

public struct GeofencePlan: Equatable, Sendable {
    public var fences: [Fence]
    /// Only present when there are more places than the OS can watch: leaving
    /// it means the nearest set must be chosen again.
    public var refreshFence: Fence?
    public var all: [Fence] { fences + (refreshFence.map { [$0] } ?? []) }
}

public enum GeofencePlanner {
    /// iOS monitors at most 20 regions per app (Android allows 100).
    public static let iosLimit = 20
    public static let refreshFenceId = "__refresh__"
    static let minRefreshRadius = 500.0

    public static func plan(_ places: [Fence], origin: LatLng?, limit: Int = iosLimit) -> GeofencePlan {
        if places.count <= limit { return GeofencePlan(fences: places) }
        guard let origin else { return GeofencePlan(fences: Array(places.prefix(limit))) }
        let sorted = places.sorted { edgeDistance(origin, $0) < edgeDistance(origin, $1) }
        let nearest = Array(sorted.prefix(limit - 1))
        let firstExcluded = sorted[limit - 1]
        // Staying within half the gap to the closest unwatched place guarantees
        // we re-plan before the user can reach it.
        let radius = max(minRefreshRadius, edgeDistance(origin, firstExcluded) / 2)
        return GeofencePlan(fences: nearest, refreshFence: Fence(id: refreshFenceId, center: origin, radius: radius))
    }

    static func edgeDistance(_ origin: LatLng, _ fence: Fence) -> Double {
        max(0, origin.distance(to: fence.center) - fence.radius)
    }

    /// Places worth watching: those with a redeemable coupon or active membership.
    public static func placesToWatch(places: [Place], coupons: [Coupon], memberships: [Membership], today: Day) -> [Place] {
        var wanted = Set<String>()
        for c in coupons where c.isRedeemable(today) { wanted.formUnion(c.placeIds) }
        for m in memberships where m.isActive { wanted.formUnion(m.placeIds) }
        return places.filter { wanted.contains($0.id) }
    }

    public static func fence(for place: Place) -> Fence {
        Fence(id: place.id, center: place.position, radius: Double(max(place.radiusMeters, Place.minRadius)))
    }
}

/// Device-only memory of a place's last transition.
public struct PlaceAlertState: Codable, Hashable, Sendable {
    public var inside: Bool
    public var lastNotifiedAt: Int64
    public var updatedAt: Int64
    public init(inside: Bool, lastNotifiedAt: Int64, updatedAt: Int64) {
        self.inside = inside; self.lastNotifiedAt = lastNotifiedAt; self.updatedAt = updatedAt
    }
}

/// Decides when arriving somewhere deserves an alert.
public enum ProximityPolicy {
    /// No repeat alert for the same place within this time.
    public static let cooldownMillis: Int64 = 2 * 60 * 60 * 1000
    /// An "inside" not refreshed for this long is distrusted: the exit event may have been lost.
    public static let insideStaleMillis: Int64 = 36 * 60 * 60 * 1000

    public enum Decision: Equatable, Sendable {
        /// Still inside (regions re-fire on registration): stay quiet.
        case alreadyInside
        case cooldown
        case disabled
        case notify
    }

    public static func onEnter(_ state: PlaceAlertState?, now: Int64, alertsEnabled: Bool) -> Decision {
        if let state, state.inside, now - state.updatedAt < insideStaleMillis { return .alreadyInside }
        if !alertsEnabled { return .disabled }
        if let state, now - state.lastNotifiedAt < cooldownMillis { return .cooldown }
        return .notify
    }

    /// State to store after an enter event; [shown] is whether an alert was actually posted.
    public static func stateAfterEnter(_ state: PlaceAlertState?, decision: Decision, shown: Bool, now: Int64) -> PlaceAlertState {
        PlaceAlertState(inside: true, lastNotifiedAt: shown ? now : (state?.lastNotifiedAt ?? 0), updatedAt: now)
    }

    public static func stateAfterExit(_ state: PlaceAlertState?, now: Int64) -> PlaceAlertState {
        PlaceAlertState(inside: false, lastNotifiedAt: state?.lastNotifiedAt ?? 0, updatedAt: now)
    }
}

/// Text and destination of a nearby alert; same wording as Android.
public struct NearbyAlert: Equatable, Sendable {
    public var title: String
    public var body: String
    public var lines: [String]
    public var link: DeepLink

    public static func make(place: Place, coupons: [Coupon], memberships: [Membership]) -> NearbyAlert? {
        guard !coupons.isEmpty || !memberships.isEmpty else { return nil }
        let body: String
        if coupons.count == 1 && memberships.isEmpty { body = coupons[0].headline }
        else if coupons.isEmpty && memberships.count == 1 { body = "Your \(memberships[0].organization) membership is valid here" }
        else if coupons.isEmpty { body = "\(memberships.count) memberships are valid here" }
        else { body = "You have \(coupons.count) coupons to use here" }

        let link: DeepLink
        if coupons.count == 1 { link = .coupon(coupons[0].id) }
        else if coupons.isEmpty && memberships.count == 1 { link = .membership(memberships[0].id) }
        else { link = .place(place.id) }

        return NearbyAlert(
            title: "You're near \(place.name)",
            body: body,
            lines: coupons.map(\.headline) + memberships.map { "\($0.organization) membership" },
            link: link
        )
    }
}

/// In-app destinations, shared by notifications and URLs (promocode://...).
public enum DeepLink: Hashable, Sendable {
    case coupon(String)
    case place(String)
    case membership(String)
    case expiring

    public static let scheme = "promocode"

    public var url: URL {
        switch self {
        case .coupon(let id): return URL(string: "\(Self.scheme)://coupon/\(id)")!
        case .place(let id): return URL(string: "\(Self.scheme)://place/\(id)")!
        case .membership(let id): return URL(string: "\(Self.scheme)://membership/\(id)")!
        case .expiring: return URL(string: "\(Self.scheme)://coupons?filter=EXPIRING_SOON")!
        }
    }

    public init?(url: URL) {
        guard url.scheme == Self.scheme, let host = url.host else { return nil }
        let id = url.pathComponents.dropFirst().first
        switch (host, id) {
        case ("coupon", let id?): self = .coupon(id)
        case ("place", let id?): self = .place(id)
        case ("membership", let id?): self = .membership(id)
        case ("coupons", _) where url.query?.contains("EXPIRING_SOON") == true: self = .expiring
        default: return nil
        }
    }
}
