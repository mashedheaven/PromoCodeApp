import Foundation
import Observation
import PromoCodeCore

enum Tab: Hashable { case coupons, memberships, places, settings }

enum CouponRoute: Hashable { case detail(String) }

/// Navigation state, so notifications and links can open any screen.
@MainActor
@Observable
final class Router {
    var tab: Tab = .coupons
    var couponPath: [CouponRoute] = []
    /// Narrows the coupon list to one place (from a nearby alert).
    var placeFilter: String?
    var couponFilter: CouponFilter = .active
    var editingMembership: String?

    func open(_ link: DeepLink) {
        switch link {
        case .coupon(let id):
            tab = .coupons
            couponPath = [.detail(id)]
        case .place(let id):
            tab = .coupons
            couponPath = []
            couponFilter = .active
            placeFilter = id
        case .membership(let id):
            tab = .memberships
            editingMembership = id
        case .expiring:
            tab = .coupons
            couponPath = []
            placeFilter = nil
            couponFilter = .expiringSoon
        }
    }
}
