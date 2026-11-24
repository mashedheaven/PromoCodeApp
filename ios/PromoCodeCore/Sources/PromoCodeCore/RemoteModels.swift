import Foundation

/*
 Wire format of the Supabase tables (PromoCodeApp/supabase/schema.sql), shared
 with the Android app. `server_updated_at` is set by the database and never sent.
 */

public protocol RemoteRow: Codable, Sendable {
    var id: String { get }
    var updated_at: Int64 { get }
    var deleted_at: Int64? { get }
    var server_updated_at: String? { get }
}

// Property names mirror the column names so the JSON needs no key mapping.
public struct CouponDTO: RemoteRow, Equatable {
    public var id: String
    public var code: String
    public var merchant: String
    public var discount_type: String
    public var discount_value: Double
    public var currency: String
    public var min_purchase: Double?
    public var category: String?
    public var notes: String?
    public var expires_on: String?
    public var barcode: String?
    public var barcode_format: String?
    public var is_favorite: Bool
    public var used_at: Int64?
    public var is_archived: Bool
    public var place_ids: [String]
    public var created_at: Int64
    public var updated_at: Int64
    public var deleted_at: Int64?
    public var server_updated_at: String?
}

public struct PlaceDTO: RemoteRow, Equatable {
    public var id: String
    public var name: String
    public var address: String?
    public var latitude: Double
    public var longitude: Double
    public var radius_meters: Int
    public var created_at: Int64
    public var updated_at: Int64
    public var deleted_at: Int64?
    public var server_updated_at: String?
}

public struct MembershipDTO: RemoteRow, Equatable {
    public var id: String
    public var organization: String
    public var member_number: String
    public var type: String
    public var started_on: String?
    public var renews_on: String?
    public var fee: Double?
    public var billing_cycle: String
    public var currency: String
    public var notes: String?
    public var reminder_days: Int
    public var is_active: Bool
    public var place_ids: [String]
    public var created_at: Int64
    public var updated_at: Int64
    public var deleted_at: Int64?
    public var server_updated_at: String?
}

public struct DeviceTokenDTO: Codable, Equatable, Sendable {
    public var token: String
    public var platform: String
}

// MARK: - Encoding: nulls are sent explicitly so a cleared field clears on the server.

extension CouponDTO {
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(code, forKey: .code); try c.encode(merchant, forKey: .merchant)
        try c.encode(discount_type, forKey: .discount_type); try c.encode(discount_value, forKey: .discount_value)
        try c.encode(currency, forKey: .currency); try c.encode(min_purchase, forKey: .min_purchase)
        try c.encode(category, forKey: .category); try c.encode(notes, forKey: .notes)
        try c.encode(expires_on, forKey: .expires_on); try c.encode(barcode, forKey: .barcode)
        try c.encode(barcode_format, forKey: .barcode_format); try c.encode(is_favorite, forKey: .is_favorite)
        try c.encode(used_at, forKey: .used_at); try c.encode(is_archived, forKey: .is_archived)
        try c.encode(place_ids, forKey: .place_ids); try c.encode(created_at, forKey: .created_at)
        try c.encode(updated_at, forKey: .updated_at); try c.encode(deleted_at, forKey: .deleted_at)
    }
}

extension PlaceDTO {
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(name, forKey: .name); try c.encode(address, forKey: .address)
        try c.encode(latitude, forKey: .latitude); try c.encode(longitude, forKey: .longitude)
        try c.encode(radius_meters, forKey: .radius_meters); try c.encode(created_at, forKey: .created_at)
        try c.encode(updated_at, forKey: .updated_at); try c.encode(deleted_at, forKey: .deleted_at)
    }
}

extension MembershipDTO {
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(organization, forKey: .organization)
        try c.encode(member_number, forKey: .member_number); try c.encode(type, forKey: .type)
        try c.encode(started_on, forKey: .started_on); try c.encode(renews_on, forKey: .renews_on)
        try c.encode(fee, forKey: .fee); try c.encode(billing_cycle, forKey: .billing_cycle)
        try c.encode(currency, forKey: .currency); try c.encode(notes, forKey: .notes)
        try c.encode(reminder_days, forKey: .reminder_days); try c.encode(is_active, forKey: .is_active)
        try c.encode(place_ids, forKey: .place_ids); try c.encode(created_at, forKey: .created_at)
        try c.encode(updated_at, forKey: .updated_at); try c.encode(deleted_at, forKey: .deleted_at)
    }
}

// MARK: - Mapping

enum DiscountType {
    static let percentage = "PERCENTAGE", fixedAmount = "FIXED_AMOUNT", bogo = "BOGO", freeShipping = "FREE_SHIPPING"
}

extension Stored where Value == Coupon {
    func dto() -> CouponDTO {
        let c = value
        let (type, amount, currency): (String, Double, String) = {
            switch c.discount {
            case .percentage(let p): return (DiscountType.percentage, p, "USD")
            case .fixedAmount(let a, let cur): return (DiscountType.fixedAmount, a, cur)
            case .buyOneGetOne: return (DiscountType.bogo, 0, "USD")
            case .freeShipping: return (DiscountType.freeShipping, 0, "USD")
            }
        }()
        return CouponDTO(
            id: c.id, code: c.code, merchant: c.merchant, discount_type: type, discount_value: amount,
            currency: currency, min_purchase: c.minPurchase, category: c.category, notes: c.notes,
            expires_on: c.expiresOn?.iso, barcode: c.barcode, barcode_format: c.barcodeFormat?.rawValue,
            is_favorite: c.isFavorite, used_at: c.usedAt, is_archived: c.isArchived, place_ids: c.placeIds,
            created_at: c.createdAt, updated_at: c.updatedAt, deleted_at: deletedAt, server_updated_at: nil)
    }
}

extension CouponDTO {
    func stored() -> Stored<Coupon> {
        let discount: Discount
        switch discount_type {
        case DiscountType.fixedAmount: discount = .fixedAmount(discount_value, currency: currency)
        case DiscountType.bogo: discount = .buyOneGetOne
        case DiscountType.freeShipping: discount = .freeShipping
        default: discount = .percentage(discount_value)
        }
        let c = Coupon(
            id: id, code: code, merchant: merchant, discount: discount, minPurchase: min_purchase,
            category: category, notes: notes, expiresOn: expires_on.flatMap(Day.init(iso:)), barcode: barcode,
            barcodeFormat: barcode_format.flatMap(BarcodeFormat.init(rawValue:)), isFavorite: is_favorite,
            usedAt: used_at, isArchived: is_archived, placeIds: place_ids, createdAt: created_at, updatedAt: updated_at)
        return Stored(c, deletedAt: deleted_at, dirty: false)
    }
}

extension Stored where Value == Place {
    func dto() -> PlaceDTO {
        let p = value
        return PlaceDTO(id: p.id, name: p.name, address: p.address, latitude: p.latitude, longitude: p.longitude,
                        radius_meters: p.radiusMeters, created_at: p.createdAt, updated_at: p.updatedAt,
                        deleted_at: deletedAt, server_updated_at: nil)
    }
}

extension PlaceDTO {
    func stored() -> Stored<Place> {
        Stored(Place(id: id, name: name, address: address, latitude: latitude, longitude: longitude,
                     radiusMeters: radius_meters, createdAt: created_at, updatedAt: updated_at),
               deletedAt: deleted_at, dirty: false)
    }
}

extension Stored where Value == Membership {
    func dto() -> MembershipDTO {
        let m = value
        return MembershipDTO(
            id: m.id, organization: m.organization, member_number: m.memberNumber, type: m.type.rawValue,
            started_on: m.startedOn?.iso, renews_on: m.renewsOn?.iso, fee: m.fee, billing_cycle: m.billingCycle.rawValue,
            currency: m.currency, notes: m.notes, reminder_days: m.reminderDays, is_active: m.isActive,
            place_ids: m.placeIds, created_at: m.createdAt, updated_at: m.updatedAt, deleted_at: deletedAt,
            server_updated_at: nil)
    }
}

extension MembershipDTO {
    func stored() -> Stored<Membership> {
        Stored(Membership(
            id: id, organization: organization, memberNumber: member_number,
            type: MembershipType(rawValue: type) ?? .OTHER, startedOn: started_on.flatMap(Day.init(iso:)),
            renewsOn: renews_on.flatMap(Day.init(iso:)), fee: fee,
            billingCycle: BillingCycle(rawValue: billing_cycle) ?? .NONE, currency: currency, notes: notes,
            reminderDays: reminder_days, isActive: is_active, placeIds: place_ids,
            createdAt: created_at, updatedAt: updated_at),
            deletedAt: deleted_at, dirty: false)
    }
}
