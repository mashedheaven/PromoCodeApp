import Foundation
@testable import PromoCodeCore

final class TestClock: Clock, @unchecked Sendable {
    var now: Date
    let timeZone: TimeZone
    init(_ day: Day = Day(year: 2026, month: 3, day: 10), hour: Int = 12, timeZone: TimeZone = TimeZone(identifier: "UTC")!) {
        self.timeZone = timeZone
        now = day.date(hour: hour, in: timeZone)
    }
    func advance(millis: Int64) { now = now.addingTimeInterval(Double(millis) / 1000) }
    func advance(days: Int) { now = now.addingTimeInterval(Double(days) * 86_400) }
}

let us = Locale(identifier: "en_US")

func coupon(
    id: String = UUID().uuidString.lowercased(), merchant: String = "Target", code: String = "SAVE20",
    discount: Discount = .percentage(20), expiresOn: Day? = nil, placeIds: [String] = [],
    isFavorite: Bool = false, usedAt: Int64? = nil, isArchived: Bool = false, category: String? = nil, createdAt: Int64 = 0
) -> Coupon {
    Coupon(id: id, code: code, merchant: merchant, discount: discount, category: category, expiresOn: expiresOn,
           isFavorite: isFavorite, usedAt: usedAt, isArchived: isArchived, placeIds: placeIds, createdAt: createdAt)
}

func place(id: String = UUID().uuidString.lowercased(), name: String = "Target Downtown", lat: Double = 40, lng: Double = -74) -> Place {
    Place(id: id, name: name, latitude: lat, longitude: lng)
}

func membership(id: String = UUID().uuidString.lowercased(), organization: String = "Gym", placeIds: [String] = []) -> Membership {
    Membership(id: id, organization: organization, placeIds: placeIds)
}
