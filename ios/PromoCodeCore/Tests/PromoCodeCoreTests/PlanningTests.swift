import XCTest
@testable import PromoCodeCore

final class PlanningTests: XCTestCase {
    let today = Day(year: 2026, month: 3, day: 10)
    let utc = TimeZone(identifier: "UTC")!

    // MARK: geofences

    func testOnlyPlacesWithSomethingRedeemableAreWatched() {
        let places = ["a", "b", "c", "d"].map { place(id: $0) }
        let watched = GeofencePlanner.placesToWatch(
            places: places,
            coupons: [
                coupon(placeIds: ["a"]),
                coupon(expiresOn: today.adding(days: -1), placeIds: ["b"]),
                coupon(placeIds: ["b"], usedAt: 1),
            ],
            memberships: [membership(placeIds: ["c"])],
            today: today)
        XCTAssertEqual(watched.map(\.id), ["a", "c"])
    }

    func testPlanRegistersEverythingUnderTheLimit() {
        let fences = (1...5).map { Fence(id: "p\($0)", center: LatLng(40 + Double($0), -74), radius: 150) }
        let plan = GeofencePlanner.plan(fences, origin: nil)
        XCTAssertEqual(plan.fences.count, 5)
        XCTAssertNil(plan.refreshFence)
    }

    func testPlanKeepsNearestAndAddsRefreshFenceOverTheIOSLimit() throws {
        let origin = LatLng(40, -74)
        let fences = (1...50).map { Fence(id: "p\($0)", center: LatLng(40, -74 + Double($0) * 0.01), radius: 150) }
        let plan = GeofencePlanner.plan(fences.shuffled(), origin: origin)
        XCTAssertEqual(plan.all.count, GeofencePlanner.iosLimit)
        XCTAssertEqual(Set(plan.fences.map(\.id)), Set((1...19).map { "p\($0)" }))
        let refresh = try XCTUnwrap(plan.refreshFence)
        XCTAssertEqual(refresh.center, origin)
        let gap = origin.distance(to: LatLng(40, -74 + 0.20)) - 150
        XCTAssertLessThan(refresh.radius, gap)
    }

    func testDistanceIsAccurate() {
        let d = LatLng(40.7128, -74.0060).distance(to: LatLng(34.0522, -118.2437))
        XCTAssertEqual(d, 3_936_000, accuracy: 40_000)
    }

    // MARK: proximity

    func testArrivalNotifiesOnceAndReentryWhileInsideStaysQuiet() {
        var now: Int64 = 1_000_000_000
        XCTAssertEqual(ProximityPolicy.onEnter(nil, now: now, alertsEnabled: true), .notify)
        var state = ProximityPolicy.stateAfterEnter(nil, decision: .notify, shown: true, now: now)
        now += 60_000
        XCTAssertEqual(ProximityPolicy.onEnter(state, now: now, alertsEnabled: true), .alreadyInside)

        state = ProximityPolicy.stateAfterExit(state, now: now)
        now += 10 * 60_000 // back ten minutes later
        XCTAssertEqual(ProximityPolicy.onEnter(state, now: now, alertsEnabled: true), .cooldown)
        now += ProximityPolicy.cooldownMillis
        XCTAssertEqual(ProximityPolicy.onEnter(state, now: now, alertsEnabled: true), .notify)
        XCTAssertEqual(ProximityPolicy.onEnter(state, now: now, alertsEnabled: false), .disabled)
    }

    func testALostExitDoesNotSilenceAPlaceForever() {
        var now: Int64 = 1_000_000_000
        var state = ProximityPolicy.stateAfterEnter(nil, decision: .notify, shown: true, now: now)
        now += 24 * 3_600_000
        XCTAssertEqual(ProximityPolicy.onEnter(state, now: now, alertsEnabled: true), .alreadyInside)
        state = ProximityPolicy.stateAfterEnter(state, decision: .alreadyInside, shown: false, now: now)
        now += ProximityPolicy.insideStaleMillis + 1
        XCTAssertEqual(ProximityPolicy.onEnter(state, now: now, alertsEnabled: true), .notify)
    }

    func testNearbyAlertWording() {
        let p = place(id: "p1", name: "Target Downtown")
        let one = coupon(id: "c1", code: "SAVE20")
        XCTAssertEqual(NearbyAlert.make(place: p, coupons: [one], memberships: [])?.link, .coupon("c1"))
        XCTAssertEqual(NearbyAlert.make(place: p, coupons: [one], memberships: [])?.title, "You're near Target Downtown")
        let two = NearbyAlert.make(place: p, coupons: [one, coupon()], memberships: [])
        XCTAssertEqual(two?.body, "You have 2 coupons to use here")
        XCTAssertEqual(two?.link, .place("p1"))
        XCTAssertEqual(NearbyAlert.make(place: p, coupons: [], memberships: [membership(id: "m", organization: "City Gym")])?.body,
                       "Your City Gym membership is valid here")
        XCTAssertNil(NearbyAlert.make(place: p, coupons: [], memberships: []))
    }

    // MARK: reminders

    func testExpiryReminderIsScheduledForNineOnTheFirstDayOfTheWindow() throws {
        var settings = Settings(); settings.expiryLeadDays = 2
        let c = coupon(id: "c1", expiresOn: today.adding(days: 10))
        let plans = ReminderPlanner.plan(coupons: [c], memberships: [], settings: settings, delivered: [],
                                         now: today.date(hour: 12, in: utc), timeZone: utc)
        let r = try XCTUnwrap(plans.first)
        XCTAssertEqual(r.fireAt, today.adding(days: 8).date(hour: 9, in: utc))
        XCTAssertEqual(r.title, "Target coupon expires in 2 days")
        XCTAssertEqual(r.link, .coupon("c1"))
        XCTAssertEqual(r.id, "expiry-c1-2026-03-20")
    }

    func testReminderInsideTheWindowFiresSoonButOnlyOnce() throws {
        var settings = Settings(); settings.expiryLeadDays = 2
        let now = today.date(hour: 12, in: utc)
        let c = coupon(id: "c1", expiresOn: today.adding(days: 1))
        let r = try XCTUnwrap(ReminderPlanner.plan(coupons: [c], memberships: [], settings: settings, delivered: [], now: now, timeZone: utc).first)
        XCTAssertEqual(r.fireAt, now.addingTimeInterval(60))
        XCTAssertEqual(r.title, "Target coupon expires tomorrow")

        // Once it has fired it is never planned again for this date...
        var ledger = ReminderLedger()
        ledger.record([r])
        ledger.advance(to: now.addingTimeInterval(120))
        XCTAssertTrue(ReminderPlanner.plan(coupons: [c], memberships: [], settings: settings, delivered: ledger.delivered,
                                           now: now.addingTimeInterval(120), timeZone: utc).isEmpty)
        // ...but a new expiry date gets a fresh reminder.
        var moved = c; moved.expiresOn = today.adding(days: 9)
        XCTAssertEqual(ReminderPlanner.plan(coupons: [moved], memberships: [], settings: settings, delivered: ledger.delivered,
                                            now: now, timeZone: utc).count, 1)
    }

    func testNoRemindersForExpiredUsedOrDisabled() {
        var settings = Settings()
        let now = today.date(hour: 12, in: utc)
        let items = [coupon(expiresOn: today.adding(days: -1)), coupon(expiresOn: today.adding(days: 1), usedAt: 1), coupon()]
        XCTAssertTrue(ReminderPlanner.plan(coupons: items, memberships: [], settings: settings, delivered: [], now: now, timeZone: utc).isEmpty)
        settings.expiryReminders = false
        XCTAssertTrue(ReminderPlanner.plan(coupons: [coupon(expiresOn: today.adding(days: 1))], memberships: [], settings: settings,
                                           delivered: [], now: now, timeZone: utc).isEmpty)
    }

    func testRenewalRemindersAndPendingCap() {
        let now = today.date(hour: 12, in: utc)
        let monthly = Membership(id: "m", organization: "Gym", renewsOn: today.adding(days: 20), fee: 10, billingCycle: .MONTHLY, reminderDays: 7)
        let plans = ReminderPlanner.plan(coupons: [], memberships: [monthly], settings: Settings(), delivered: [], now: now, timeZone: utc)
        XCTAssertEqual(plans.first?.fireAt, today.adding(days: 13).date(hour: 9, in: utc))
        XCTAssertEqual(plans.first?.title, "Gym renews in 7 days")
        var off = monthly; off.reminderDays = 0
        XCTAssertTrue(ReminderPlanner.plan(coupons: [], memberships: [off], settings: Settings(), delivered: [], now: now, timeZone: utc).isEmpty)

        let many = (1...100).map { coupon(expiresOn: today.adding(days: 5 + $0)) }
        XCTAssertEqual(ReminderPlanner.plan(coupons: many, memberships: [], settings: Settings(), delivered: [], now: now, timeZone: utc).count,
                       ReminderPlanner.maxPending)
    }
}
