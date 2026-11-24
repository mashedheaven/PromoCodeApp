import XCTest
@testable import PromoCodeCore

final class DomainTests: XCTestCase {
    let today = Day(year: 2026, month: 3, day: 10)

    func testDayArithmeticAndParsing() {
        XCTAssertEqual(Day(year: 1970, month: 1, day: 1).epochDay, 0)
        XCTAssertEqual(Day(year: 2000, month: 3, day: 1).iso, "2000-03-01")
        XCTAssertEqual(Day(iso: "2026-03-10"), today)
        XCTAssertNil(Day(iso: "2026-02-30"))
        XCTAssertNil(Day(iso: "10/03/2026"))
        XCTAssertEqual(Day(year: 2026, month: 1, day: 31).adding(months: 1).iso, "2026-02-28")
        XCTAssertEqual(Day(year: 2024, month: 1, day: 31).adding(months: 1).iso, "2024-02-29")
        XCTAssertEqual(Day(year: 2026, month: 12, day: 15).adding(months: 1).iso, "2027-01-15")
        XCTAssertEqual(Day(year: 2026, month: 1, day: 15).adding(months: -2).iso, "2025-11-15")
        XCTAssertEqual(today.days(until: today.adding(days: 3)), 3)
    }

    func testDayRoundTripsThroughJSONAsISOString() throws {
        let data = try JSONEncoder().encode([today])
        XCTAssertEqual(String(decoding: data, as: UTF8.self), "[\"2026-03-10\"]")
        XCTAssertEqual(try JSONDecoder().decode([Day].self, from: data), [today])
    }

    func testDayFromDateRespectsTimeZone() {
        let tokyo = TimeZone(identifier: "Asia/Tokyo")!
        let lateUTC = Day(year: 2026, month: 3, day: 10).date(hour: 20, in: TimeZone(identifier: "UTC")!)
        XCTAssertEqual(Day(lateUTC, in: tokyo).iso, "2026-03-11") // already tomorrow in Tokyo
        let nineAM = today.date(hour: 9, in: tokyo)
        XCTAssertEqual(Day(nineAM, in: tokyo), today)
    }

    func testCouponStaysValidThroughItsExpiryDay() {
        XCTAssertEqual(Expiry.of(today, today: today), .today)
        XCTAssertTrue(coupon(expiresOn: today).isRedeemable(today))
        XCTAssertEqual(Expiry.of(today.adding(days: -1), today: today), .expired(daysAgo: 1))
        XCTAssertFalse(coupon(expiresOn: today.adding(days: -1)).isRedeemable(today))
        XCTAssertEqual(Expiry.of(today.adding(days: 3), today: today), .inDays(3))
        XCTAssertEqual(Expiry.of(nil, today: today), .never)
        XCTAssertFalse(coupon(usedAt: 1).isRedeemable(today))
        XCTAssertFalse(coupon(isArchived: true).isRedeemable(today))
    }

    func testDiscountLabels() {
        XCTAssertEqual(Discount.percentage(20).label(locale: us), "20% OFF")
        XCTAssertEqual(Discount.percentage(12.5).label(locale: us), "12.5% OFF")
        XCTAssertEqual(Discount.fixedAmount(5, currency: "USD").label(locale: us), "$5 OFF")
        XCTAssertEqual(Discount.fixedAmount(5.5, currency: "USD").label(locale: us), "$5.50 OFF")
        XCTAssertEqual(Discount.buyOneGetOne.label(), "BUY 1 GET 1")
    }

    func testMoneyParsing() {
        XCTAssertEqual(Money.parse("12,50"), 12.5)
        XCTAssertEqual(Money.parse(" 7 "), 7)
        XCTAssertNil(Money.parse("abc"))
        XCTAssertNil(Money.parse("-3"))
        XCTAssertNil(Money.parse(""))
    }

    func testFiltersPartitionCouponsByState() {
        let all = [
            coupon(merchant: "A", expiresOn: today.adding(days: 30)),
            coupon(merchant: "B", expiresOn: today.adding(days: 2), isFavorite: true),
            coupon(merchant: "C", expiresOn: today),
            coupon(merchant: "D", expiresOn: today.adding(days: -1)),
            coupon(merchant: "E", usedAt: 5),
            coupon(merchant: "F", isArchived: true),
        ]
        func names(_ f: CouponFilter) -> [String] { CouponQuery(filter: f, sort: .merchant).apply(all, today: today).map(\.merchant) }
        XCTAssertEqual(names(.active), ["A", "B", "C"])
        XCTAssertEqual(names(.favorites), ["B"])
        XCTAssertEqual(names(.expiringSoon), ["B", "C"])
        XCTAssertEqual(names(.expired), ["D"])
        XCTAssertEqual(names(.used), ["E"])
        XCTAssertEqual(names(.archived), ["F"])
    }

    func testSearchAndSortAndPlaceFilter() {
        let all = [
            coupon(merchant: "Never", code: "X"),
            coupon(merchant: "Later", code: "LATTE", expiresOn: today.adding(days: 20), placeIds: ["p1"], category: "Restaurants"),
            coupon(merchant: "Soon", code: "SAVE", expiresOn: today.adding(days: 1)),
        ]
        XCTAssertEqual(CouponQuery().apply(all, today: today).map(\.merchant), ["Soon", "Later", "Never"])
        XCTAssertEqual(CouponQuery(text: "latte").apply(all, today: today).map(\.merchant), ["Later"])
        XCTAssertEqual(CouponQuery(text: "restau").apply(all, today: today).map(\.merchant), ["Later"])
        XCTAssertEqual(CouponQuery(placeId: "p1").apply(all, today: today).map(\.merchant), ["Later"])
        XCTAssertTrue(CouponQuery(text: "zzz").apply(all, today: today).isEmpty)
    }

    func testMembershipRenewalRollsForward() {
        let m = Membership(organization: "Gym", renewsOn: today.adding(days: -27), fee: 10, billingCycle: .MONTHLY)
        let next = m.nextRenewal(today)!
        XCTAssertGreaterThanOrEqual(next, today)
        XCTAssertEqual(m.annualCost, 120)
        let oneOff = Membership(organization: "Card", renewsOn: today.adding(days: -3))
        XCTAssertEqual(oneOff.nextRenewal(today), today.adding(days: -3))
    }

    func testDeepLinksRoundTrip() {
        for link in [DeepLink.coupon("abc"), .place("p1"), .membership("m1"), .expiring] {
            XCTAssertEqual(DeepLink(url: link.url), link)
        }
        XCTAssertNil(DeepLink(url: URL(string: "https://example.com/coupon/1")!))
    }

    func testRetailBarcodes() throws {
        // 590123412345 + check digit 7 (standard GS1 example).
        XCTAssertEqual(RetailBarcode.checkDigit([5, 9, 0, 1, 2, 3, 4, 1, 2, 3, 4, 5]), 7)
        let full = try XCTUnwrap(RetailBarcode.modules("5901234123457", format: .EAN_13))
        XCTAssertEqual(full.count, 95 + 18) // 95 modules + quiet zones
        XCTAssertEqual(RetailBarcode.modules("590123412345", format: .EAN_13), full) // check digit added
        XCTAssertNil(RetailBarcode.modules("5901234123458", format: .EAN_13))       // wrong check digit
        XCTAssertNil(RetailBarcode.modules("ABC", format: .EAN_13))
        XCTAssertEqual(RetailBarcode.modules("96385074", format: .EAN_8)?.count, 67 + 18)
        // UPC-A 036000291452 renders identically to EAN-13 0036000291452.
        XCTAssertEqual(RetailBarcode.modules("036000291452", format: .UPC_A), RetailBarcode.modules("0036000291452", format: .EAN_13))
        // Guard bars sit right after the quiet zone.
        XCTAssertEqual(Array(full[9..<12]), [true, false, true])
    }
}
