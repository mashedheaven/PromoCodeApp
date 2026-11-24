import Foundation

/// What a coupon is worth.
public enum Discount: Hashable, Sendable {
    case percentage(Double)
    case fixedAmount(Double, currency: String)
    case buyOneGetOne
    case freeShipping

    /// Short badge text, e.g. "20% OFF".
    public func label(locale: Locale = .current) -> String {
        switch self {
        case .percentage(let p): return "\(Self.trim(p, locale: locale))% OFF"
        case .fixedAmount(let a, let currency): return "\(Money.format(a, currency: currency, locale: locale)) OFF"
        case .buyOneGetOne: return "BUY 1 GET 1"
        case .freeShipping: return "FREE SHIPPING"
        }
    }

    private static func trim(_ value: Double, locale: Locale) -> String {
        let f = NumberFormatter()
        f.locale = locale
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = value.truncatingRemainder(dividingBy: 1) == 0 ? 0 : 1
        return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }
}

/// Where a coupon stands relative to its expiry day.
public enum Expiry: Hashable, Sendable {
    case never
    case expired(daysAgo: Int)
    case today
    case inDays(Int)

    public var isExpired: Bool { if case .expired = self { return true } else { return false } }

    public static func of(_ expiresOn: Day?, today: Day) -> Expiry {
        guard let expiresOn else { return .never }
        let days = today.days(until: expiresOn)
        if days < 0 { return .expired(daysAgo: -days) }
        return days == 0 ? .today : .inDays(days)
    }

    public var text: String {
        switch self {
        case .never: return "No expiry"
        case .expired(let d): return d == 1 ? "Expired yesterday" : "Expired \(d) days ago"
        case .today: return "Expires today"
        case .inDays(let d): return d == 1 ? "Expires tomorrow" : "Expires in \(d) days"
        }
    }
}

/// Barcode symbologies. Names match the Android app so synced rows agree.
public enum BarcodeFormat: String, CaseIterable, Codable, Sendable {
    case QR_CODE, AZTEC, DATA_MATRIX, PDF_417
    case CODE_128, CODE_39, CODE_93, CODABAR, ITF
    case EAN_13, EAN_8, UPC_A, UPC_E

    public var isTwoDimensional: Bool { self == .QR_CODE || self == .AZTEC || self == .DATA_MATRIX }
}

/// Anything stored locally and synced: identified by a client-made UUID and
/// stamped on every edit for last-write-wins conflict resolution.
public protocol Syncable: Identifiable, Hashable, Sendable where ID == String {
    var updatedAt: Int64 { get set }
}

public struct Coupon: Syncable {
    public var id: String
    public var code: String
    public var merchant: String
    public var discount: Discount
    public var minPurchase: Double?
    public var category: String?
    public var notes: String?
    public var expiresOn: Day?
    public var barcode: String?
    public var barcodeFormat: BarcodeFormat?
    public var isFavorite: Bool
    /// When it was redeemed (epoch millis), or nil if unused.
    public var usedAt: Int64?
    public var isArchived: Bool
    /// Places where it can be used; drives nearby alerts.
    public var placeIds: [String]
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String = UUID().uuidString.lowercased(), code: String, merchant: String, discount: Discount,
        minPurchase: Double? = nil, category: String? = nil, notes: String? = nil, expiresOn: Day? = nil,
        barcode: String? = nil, barcodeFormat: BarcodeFormat? = nil, isFavorite: Bool = false,
        usedAt: Int64? = nil, isArchived: Bool = false, placeIds: [String] = [],
        createdAt: Int64 = 0, updatedAt: Int64 = 0
    ) {
        self.id = id; self.code = code; self.merchant = merchant; self.discount = discount
        self.minPurchase = minPurchase; self.category = category; self.notes = notes
        self.expiresOn = expiresOn; self.barcode = barcode; self.barcodeFormat = barcodeFormat
        self.isFavorite = isFavorite; self.usedAt = usedAt; self.isArchived = isArchived
        self.placeIds = placeIds; self.createdAt = createdAt; self.updatedAt = updatedAt
    }

    public var isUsed: Bool { usedAt != nil }
    public func expiry(_ today: Day) -> Expiry { .of(expiresOn, today: today) }
    public func isRedeemable(_ today: Day) -> Bool { !isUsed && !isArchived && !expiry(today).isExpired }

    /// "20% OFF · SAVE20", or just the discount when there is no code.
    public var headline: String {
        code.trimmingCharacters(in: .whitespaces).isEmpty ? discount.label() : "\(discount.label()) · \(code)"
    }
}

public struct Place: Syncable {
    public static let minRadius = 100
    public static let maxRadius = 2000
    public static let defaultRadius = 150

    public var id: String
    public var name: String
    public var address: String?
    public var latitude: Double
    public var longitude: Double
    public var radiusMeters: Int
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String = UUID().uuidString.lowercased(), name: String, address: String? = nil,
        latitude: Double, longitude: Double, radiusMeters: Int = Place.defaultRadius,
        createdAt: Int64 = 0, updatedAt: Int64 = 0
    ) {
        self.id = id; self.name = name; self.address = address
        self.latitude = latitude; self.longitude = longitude
        self.radiusMeters = min(max(radiusMeters, Place.minRadius), Place.maxRadius)
        self.createdAt = createdAt; self.updatedAt = updatedAt
    }

    public var position: LatLng { LatLng(latitude, longitude) }

    public static func isValid(latitude: Double, longitude: Double) -> Bool {
        (-90...90).contains(latitude) && (-180...180).contains(longitude) && !(latitude == 0 && longitude == 0)
    }
}

public enum MembershipType: String, CaseIterable, Codable, Sendable {
    case GYM, SUBSCRIPTION, LOYALTY, CLUB, OTHER

    public var label: String {
        switch self {
        case .GYM: return "Gym"
        case .SUBSCRIPTION: return "Subscription"
        case .LOYALTY: return "Loyalty program"
        case .CLUB: return "Club"
        case .OTHER: return "Other"
        }
    }
}

public enum BillingCycle: String, CaseIterable, Codable, Sendable {
    case NONE, MONTHLY, YEARLY

    public var label: String {
        switch self {
        case .NONE: return "Free"
        case .MONTHLY: return "Monthly"
        case .YEARLY: return "Yearly"
        }
    }

    public var perYear: Int {
        switch self {
        case .NONE: return 0
        case .MONTHLY: return 12
        case .YEARLY: return 1
        }
    }
}

public struct Membership: Syncable {
    public var id: String
    public var organization: String
    public var memberNumber: String
    public var type: MembershipType
    public var startedOn: Day?
    public var renewsOn: Day?
    public var fee: Double?
    public var billingCycle: BillingCycle
    public var currency: String
    public var notes: String?
    /// Days before renewal to remind; 0 turns the reminder off.
    public var reminderDays: Int
    public var isActive: Bool
    public var placeIds: [String]
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String = UUID().uuidString.lowercased(), organization: String, memberNumber: String = "",
        type: MembershipType = .OTHER, startedOn: Day? = nil, renewsOn: Day? = nil, fee: Double? = nil,
        billingCycle: BillingCycle = .NONE, currency: String = "USD", notes: String? = nil,
        reminderDays: Int = 7, isActive: Bool = true, placeIds: [String] = [],
        createdAt: Int64 = 0, updatedAt: Int64 = 0
    ) {
        self.id = id; self.organization = organization; self.memberNumber = memberNumber; self.type = type
        self.startedOn = startedOn; self.renewsOn = renewsOn; self.fee = fee; self.billingCycle = billingCycle
        self.currency = currency; self.notes = notes; self.reminderDays = reminderDays; self.isActive = isActive
        self.placeIds = placeIds; self.createdAt = createdAt; self.updatedAt = updatedAt
    }

    public var annualCost: Double { (fee ?? 0) * Double(billingCycle.perYear) }

    /// Next renewal on or after [today]; recurring memberships roll forward.
    public func nextRenewal(_ today: Day) -> Day? {
        guard var next = renewsOn else { return nil }
        guard billingCycle != .NONE else { return next }
        var steps = 0
        while next < today && steps < 1200 {
            next = billingCycle == .MONTHLY ? next.adding(months: 1) : next.adding(years: 1)
            steps += 1
        }
        return next
    }
}

public enum Money {
    public static func format(_ amount: Double, currency: String, locale: Locale = .current) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.locale = locale
        f.currencyCode = currency
        if amount.truncatingRemainder(dividingBy: 1) == 0 {
            f.minimumFractionDigits = 0
            f.maximumFractionDigits = 0
        }
        return f.string(from: NSNumber(value: amount)) ?? "\(currency) \(amount)"
    }

    /// Parses typed amounts, accepting "," or "." as the decimal separator.
    public static func parse(_ text: String) -> Double? {
        let cleaned = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        guard !cleaned.isEmpty, let value = Double(cleaned), value.isFinite, value >= 0 else { return nil }
        return value
    }

    public static func defaultCurrency(locale: Locale = .current) -> String {
        locale.currency?.identifier ?? "USD"
    }
}
