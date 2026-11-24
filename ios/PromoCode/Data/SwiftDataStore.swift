import Foundation
import SwiftData
import PromoCodeCore

// SwiftData rows. They mirror the core models plus sync bookkeeping; all
// logic lives in PromoCodeCore, so these stay plain storage.

@Model
final class CouponRow {
    @Attribute(.unique) var id: String
    var code: String
    var merchant: String
    var discountType: String
    var discountValue: Double
    var currency: String
    var minPurchase: Double?
    var category: String?
    var notes: String?
    var expiresOnDay: Int?
    var barcode: String?
    var barcodeFormat: String?
    var isFavorite: Bool
    var usedAt: Int64?
    var isArchived: Bool
    var placeIds: [String]
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var dirty: Bool

    init(_ s: Stored<Coupon>) {
        id = s.id; code = ""; merchant = ""; discountType = ""; discountValue = 0; currency = "USD"
        isFavorite = false; isArchived = false; placeIds = []; createdAt = 0; updatedAt = 0; dirty = true
        update(from: s)
    }

    func update(from s: Stored<Coupon>) {
        let c = s.value
        code = c.code; merchant = c.merchant
        switch c.discount {
        case .percentage(let p): discountType = "PERCENTAGE"; discountValue = p
        case .fixedAmount(let a, let cur): discountType = "FIXED_AMOUNT"; discountValue = a; currency = cur
        case .buyOneGetOne: discountType = "BOGO"; discountValue = 0
        case .freeShipping: discountType = "FREE_SHIPPING"; discountValue = 0
        }
        minPurchase = c.minPurchase; category = c.category; notes = c.notes
        expiresOnDay = c.expiresOn?.epochDay; barcode = c.barcode; barcodeFormat = c.barcodeFormat?.rawValue
        isFavorite = c.isFavorite; usedAt = c.usedAt; isArchived = c.isArchived; placeIds = c.placeIds
        createdAt = c.createdAt; updatedAt = c.updatedAt; deletedAt = s.deletedAt; dirty = s.dirty
    }

    var stored: Stored<Coupon> {
        let discount: Discount
        switch discountType {
        case "FIXED_AMOUNT": discount = .fixedAmount(discountValue, currency: currency)
        case "BOGO": discount = .buyOneGetOne
        case "FREE_SHIPPING": discount = .freeShipping
        default: discount = .percentage(discountValue)
        }
        return Stored(Coupon(
            id: id, code: code, merchant: merchant, discount: discount, minPurchase: minPurchase, category: category,
            notes: notes, expiresOn: expiresOnDay.map(Day.init(epochDay:)), barcode: barcode,
            barcodeFormat: barcodeFormat.flatMap(BarcodeFormat.init(rawValue:)), isFavorite: isFavorite, usedAt: usedAt,
            isArchived: isArchived, placeIds: placeIds, createdAt: createdAt, updatedAt: updatedAt),
            deletedAt: deletedAt, dirty: dirty)
    }
}

@Model
final class PlaceRow {
    @Attribute(.unique) var id: String
    var name: String
    var address: String?
    var latitude: Double
    var longitude: Double
    var radiusMeters: Int
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var dirty: Bool

    init(_ s: Stored<Place>) {
        id = s.id; name = ""; latitude = 0; longitude = 0; radiusMeters = Place.defaultRadius
        createdAt = 0; updatedAt = 0; dirty = true
        update(from: s)
    }

    func update(from s: Stored<Place>) {
        let p = s.value
        name = p.name; address = p.address; latitude = p.latitude; longitude = p.longitude
        radiusMeters = p.radiusMeters; createdAt = p.createdAt; updatedAt = p.updatedAt
        deletedAt = s.deletedAt; dirty = s.dirty
    }

    var stored: Stored<Place> {
        Stored(Place(id: id, name: name, address: address, latitude: latitude, longitude: longitude,
                     radiusMeters: radiusMeters, createdAt: createdAt, updatedAt: updatedAt),
               deletedAt: deletedAt, dirty: dirty)
    }
}

@Model
final class MembershipRow {
    @Attribute(.unique) var id: String
    var organization: String
    var memberNumber: String
    var type: String
    var startedOnDay: Int?
    var renewsOnDay: Int?
    var fee: Double?
    var billingCycle: String
    var currency: String
    var notes: String?
    var reminderDays: Int
    var isActive: Bool
    var placeIds: [String]
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var dirty: Bool

    init(_ s: Stored<Membership>) {
        id = s.id; organization = ""; memberNumber = ""; type = "OTHER"; billingCycle = "NONE"; currency = "USD"
        reminderDays = 7; isActive = true; placeIds = []; createdAt = 0; updatedAt = 0; dirty = true
        update(from: s)
    }

    func update(from s: Stored<Membership>) {
        let m = s.value
        organization = m.organization; memberNumber = m.memberNumber; type = m.type.rawValue
        startedOnDay = m.startedOn?.epochDay; renewsOnDay = m.renewsOn?.epochDay; fee = m.fee
        billingCycle = m.billingCycle.rawValue; currency = m.currency; notes = m.notes
        reminderDays = m.reminderDays; isActive = m.isActive; placeIds = m.placeIds
        createdAt = m.createdAt; updatedAt = m.updatedAt; deletedAt = s.deletedAt; dirty = s.dirty
    }

    var stored: Stored<Membership> {
        Stored(Membership(
            id: id, organization: organization, memberNumber: memberNumber,
            type: MembershipType(rawValue: type) ?? .OTHER, startedOn: startedOnDay.map(Day.init(epochDay:)),
            renewsOn: renewsOnDay.map(Day.init(epochDay:)), fee: fee,
            billingCycle: BillingCycle(rawValue: billingCycle) ?? .NONE, currency: currency, notes: notes,
            reminderDays: reminderDays, isActive: isActive, placeIds: placeIds,
            createdAt: createdAt, updatedAt: updatedAt),
            deletedAt: deletedAt, dirty: dirty)
    }
}

/// PromoCodeCore's storage interface on top of SwiftData.
@MainActor
final class SwiftDataStore: RecordStore {
    let container: ModelContainer
    private var context: ModelContext { container.mainContext }

    init(inMemory: Bool = false) throws {
        let config = ModelConfiguration(isStoredInMemoryOnly: inMemory)
        container = try ModelContainer(for: CouponRow.self, PlaceRow.self, MembershipRow.self, configurations: config)
    }

    func coupons() -> [Stored<Coupon>] { ((try? context.fetch(FetchDescriptor<CouponRow>())) ?? []).map(\.stored) }
    func places() -> [Stored<Place>] { ((try? context.fetch(FetchDescriptor<PlaceRow>())) ?? []).map(\.stored) }
    func memberships() -> [Stored<Membership>] { ((try? context.fetch(FetchDescriptor<MembershipRow>())) ?? []).map(\.stored) }

    func put(_ coupon: Stored<Coupon>) {
        if let row = couponRow(coupon.id) { row.update(from: coupon) } else { context.insert(CouponRow(coupon)) }
    }

    func put(_ place: Stored<Place>) {
        if let row = placeRow(place.id) { row.update(from: place) } else { context.insert(PlaceRow(place)) }
    }

    func put(_ membership: Stored<Membership>) {
        if let row = membershipRow(membership.id) { row.update(from: membership) } else { context.insert(MembershipRow(membership)) }
    }

    func removeCoupon(id: String) { if let row = couponRow(id) { context.delete(row) } }
    func removePlace(id: String) { if let row = placeRow(id) { context.delete(row) } }
    func removeMembership(id: String) { if let row = membershipRow(id) { context.delete(row) } }

    func save() throws {
        if context.hasChanges { try context.save() }
    }

    private func couponRow(_ id: String) -> CouponRow? {
        try? context.fetch(FetchDescriptor<CouponRow>(predicate: #Predicate { $0.id == id })).first
    }

    private func placeRow(_ id: String) -> PlaceRow? {
        try? context.fetch(FetchDescriptor<PlaceRow>(predicate: #Predicate { $0.id == id })).first
    }

    private func membershipRow(_ id: String) -> MembershipRow? {
        try? context.fetch(FetchDescriptor<MembershipRow>(predicate: #Predicate { $0.id == id })).first
    }
}
