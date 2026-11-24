import Foundation

/// A locally stored row plus its sync bookkeeping.
public struct Stored<Value: Syncable>: Hashable, Sendable {
    public var value: Value
    /// Tombstone time; kept until the deletion has been uploaded.
    public var deletedAt: Int64?
    /// Has local changes that still need uploading.
    public var dirty: Bool

    public init(_ value: Value, deletedAt: Int64? = nil, dirty: Bool = true) {
        self.value = value; self.deletedAt = deletedAt; self.dirty = dirty
    }

    public var id: String { value.id }
    public var isDeleted: Bool { deletedAt != nil }
}

/// Persistence the app provides (SwiftData on iOS, in memory in tests).
/// Lists include tombstones; callers filter them.
@MainActor
public protocol RecordStore: AnyObject {
    func coupons() -> [Stored<Coupon>]
    func places() -> [Stored<Place>]
    func memberships() -> [Stored<Membership>]

    func put(_ coupon: Stored<Coupon>)
    func put(_ place: Stored<Place>)
    func put(_ membership: Stored<Membership>)

    func removeCoupon(id: String)
    func removePlace(id: String)
    func removeMembership(id: String)

    /// Commits pending writes, if the store batches them.
    func save() throws
}

public extension RecordStore {
    func coupon(_ id: String) -> Stored<Coupon>? { coupons().first { $0.id == id } }
    func place(_ id: String) -> Stored<Place>? { places().first { $0.id == id } }
    func membership(_ id: String) -> Stored<Membership>? { memberships().first { $0.id == id } }

    func removeAll() {
        coupons().forEach { removeCoupon(id: $0.id) }
        places().forEach { removePlace(id: $0.id) }
        memberships().forEach { removeMembership(id: $0.id) }
    }
}

@MainActor
public final class InMemoryStore: RecordStore {
    public var couponRows: [String: Stored<Coupon>] = [:]
    public var placeRows: [String: Stored<Place>] = [:]
    public var membershipRows: [String: Stored<Membership>] = [:]

    public init() {}

    public func coupons() -> [Stored<Coupon>] { Array(couponRows.values) }
    public func places() -> [Stored<Place>] { Array(placeRows.values) }
    public func memberships() -> [Stored<Membership>] { Array(membershipRows.values) }
    public func put(_ coupon: Stored<Coupon>) { couponRows[coupon.id] = coupon }
    public func put(_ place: Stored<Place>) { placeRows[place.id] = place }
    public func put(_ membership: Stored<Membership>) { membershipRows[membership.id] = membership }
    public func removeCoupon(id: String) { couponRows[id] = nil }
    public func removePlace(id: String) { placeRows[id] = nil }
    public func removeMembership(id: String) { membershipRows[id] = nil }
    public func save() throws {}
}

/// Every edit the app makes goes through here, so timestamps, dirty flags
/// and tombstones are applied consistently.
@MainActor
public final class Library {
    public let store: RecordStore
    let clock: Clock
    /// Called after every change (to schedule sync, reminders and geofences).
    public var onChange: () -> Void = {}

    public init(store: RecordStore, clock: Clock = SystemClock()) {
        self.store = store
        self.clock = clock
    }

    public var coupons: [Coupon] { store.coupons().filter { !$0.isDeleted }.map(\.value) }
    public var places: [Place] {
        store.places().filter { !$0.isDeleted }.map(\.value)
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }
    public var memberships: [Membership] {
        store.memberships().filter { !$0.isDeleted }.map(\.value)
            .sorted { $0.organization.localizedCaseInsensitiveCompare($1.organization) == .orderedAscending }
    }

    public func coupon(_ id: String) -> Coupon? { store.coupon(id).flatMap { $0.isDeleted ? nil : $0.value } }
    public func place(_ id: String) -> Place? { store.place(id).flatMap { $0.isDeleted ? nil : $0.value } }
    public func membership(_ id: String) -> Membership? { store.membership(id).flatMap { $0.isDeleted ? nil : $0.value } }

    // MARK: coupons

    public func save(_ coupon: Coupon) {
        var c = coupon
        c.updatedAt = clock.millis
        if c.createdAt == 0 { c.createdAt = c.updatedAt }
        store.put(Stored(c))
        commit()
    }

    public func setFavorite(_ id: String, _ favorite: Bool) { edit(id) { $0.isFavorite = favorite } }
    public func setUsed(_ id: String, _ used: Bool) { edit(id) { $0.usedAt = used ? self.clock.millis : nil } }
    public func setArchived(_ id: String, _ archived: Bool) { edit(id) { $0.isArchived = archived } }

    public func deleteCoupon(_ id: String) {
        guard var row = store.coupon(id) else { return }
        row.value.updatedAt = clock.millis
        row.deletedAt = row.value.updatedAt
        row.dirty = true
        store.put(row)
        commit()
    }

    /// Archives coupons expired more than [graceDays] ago; returns how many.
    @discardableResult
    public func archiveExpired(graceDays: Int = 7) -> Int {
        let today = clock.today
        let stale = coupons.filter { c in
            if c.isArchived { return false }
            if case .expired(let d) = c.expiry(today) { return d > graceDays }
            return false
        }
        for var c in stale {
            c.isArchived = true
            c.updatedAt = clock.millis
            store.put(Stored(c))
        }
        if !stale.isEmpty { commit() }
        return stale.count
    }

    private func edit(_ id: String, _ change: (inout Coupon) -> Void) {
        guard var c = coupon(id) else { return }
        change(&c)
        save(c)
    }

    // MARK: places

    public func save(_ place: Place) {
        var p = place
        p.updatedAt = clock.millis
        if p.createdAt == 0 { p.createdAt = p.updatedAt }
        store.put(Stored(p))
        commit()
    }

    /// Deletes a place and unlinks it from every coupon and membership.
    public func deletePlace(_ id: String) {
        guard var row = store.place(id) else { return }
        let now = clock.millis
        row.value.updatedAt = now
        row.deletedAt = now
        row.dirty = true
        store.put(row)
        for var c in store.coupons() where c.value.placeIds.contains(id) {
            c.value.placeIds.removeAll { $0 == id }
            c.value.updatedAt = now
            c.dirty = true
            store.put(c)
        }
        for var m in store.memberships() where m.value.placeIds.contains(id) {
            m.value.placeIds.removeAll { $0 == id }
            m.value.updatedAt = now
            m.dirty = true
            store.put(m)
        }
        commit()
    }

    // MARK: memberships

    public func save(_ membership: Membership) {
        var m = membership
        m.updatedAt = clock.millis
        if m.createdAt == 0 { m.createdAt = m.updatedAt }
        store.put(Stored(m))
        commit()
    }

    public func deleteMembership(_ id: String) {
        guard var row = store.membership(id) else { return }
        row.value.updatedAt = clock.millis
        row.deletedAt = row.value.updatedAt
        row.dirty = true
        store.put(row)
        commit()
    }

    private func commit() {
        try? store.save()
        onChange()
    }
}
