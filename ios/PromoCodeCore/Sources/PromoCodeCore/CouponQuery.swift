import Foundation

public enum CouponFilter: String, CaseIterable, Sendable {
    case active, favorites, expiringSoon, used, expired, archived

    public var label: String {
        switch self {
        case .active: return "Active"
        case .favorites: return "Favorites"
        case .expiringSoon: return "Expiring soon"
        case .used: return "Used"
        case .expired: return "Expired"
        case .archived: return "Archived"
        }
    }

    public var emptyMessage: String {
        switch self {
        case .active: return "You have no active coupons."
        case .favorites: return "Tap the heart on a coupon to keep it here."
        case .expiringSoon: return "Nothing expires in the next week."
        case .used: return "Coupons you mark as used show up here."
        case .expired: return "No expired coupons."
        case .archived: return "The archive is empty."
        }
    }
}

public enum CouponSort: String, CaseIterable, Sendable {
    case expiry, merchant, newest, discount

    public var label: String {
        switch self {
        case .expiry: return "Expiring first"
        case .merchant: return "Store name"
        case .newest: return "Recently added"
        case .discount: return "Biggest discount"
        }
    }
}

/// Search, filter and sort rules for the coupon list. Same behaviour as Android.
public struct CouponQuery: Equatable, Sendable {
    public static let expiringSoonDays = 7

    public var text: String
    public var filter: CouponFilter
    public var sort: CouponSort
    public var placeId: String?

    public init(text: String = "", filter: CouponFilter = .active, sort: CouponSort = .expiry, placeId: String? = nil) {
        self.text = text; self.filter = filter; self.sort = sort; self.placeId = placeId
    }

    public func apply(_ coupons: [Coupon], today: Day) -> [Coupon] {
        let needle = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return coupons
            .filter { matches($0, today: today) }
            .filter { placeId == nil || $0.placeIds.contains(placeId!) }
            .filter { needle.isEmpty || matchesText($0, needle) }
            .sorted(by: areInOrder)
    }

    private func matches(_ c: Coupon, today: Day) -> Bool {
        switch filter {
        case .active: return c.isRedeemable(today)
        case .favorites: return c.isFavorite && c.isRedeemable(today)
        case .expiringSoon:
            guard c.isRedeemable(today) else { return false }
            switch c.expiry(today) {
            case .today: return true
            case .inDays(let d): return d <= Self.expiringSoonDays
            default: return false
            }
        case .used: return c.isUsed && !c.isArchived
        case .expired: return !c.isUsed && !c.isArchived && c.expiry(today).isExpired
        case .archived: return c.isArchived
        }
    }

    private func matchesText(_ c: Coupon, _ needle: String) -> Bool {
        [c.merchant, c.code, c.category ?? "", c.notes ?? ""].contains { $0.localizedCaseInsensitiveContains(needle) }
    }

    private func areInOrder(_ a: Coupon, _ b: Coupon) -> Bool {
        // Coupons without an expiry sort after every dated one.
        let ea = a.expiresOn?.epochDay ?? Int.max, eb = b.expiresOn?.epochDay ?? Int.max
        let ma = a.merchant.lowercased(), mb = b.merchant.lowercased()
        switch sort {
        case .expiry: return ea != eb ? ea < eb : ma < mb
        case .merchant: return ma != mb ? ma < mb : ea < eb
        case .newest: return a.createdAt > b.createdAt
        case .discount:
            let ra = Self.rank(a.discount), rb = Self.rank(b.discount)
            return ra != rb ? ra > rb : ea < eb
        }
    }

    /// Percentages and amounts aren't comparable, so percentages rank first.
    private static func rank(_ d: Discount) -> Double {
        switch d {
        case .percentage(let p): return 1_000_000 + p
        case .fixedAmount(let a, _): return a
        case .buyOneGetOne: return 0.5
        case .freeShipping: return 0.25
        }
    }
}
