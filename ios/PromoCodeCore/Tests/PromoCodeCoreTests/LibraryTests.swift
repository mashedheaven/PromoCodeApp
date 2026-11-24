import XCTest
@testable import PromoCodeCore

@MainActor
final class LibraryTests: XCTestCase {
    var clock: TestClock!
    var store: InMemoryStore!
    var library: Library!
    var changes = 0

    override func setUp() async throws {
        clock = TestClock()
        store = InMemoryStore()
        library = Library(store: store, clock: clock)
        changes = 0
        library.onChange = { [unowned self] in changes += 1 }
    }

    func testSavingStampsTimeMarksDirtyAndNotifies() async {
        library.save(coupon(id: "c1"))
        let row = store.coupon("c1")!
        XCTAssertTrue(row.dirty)
        XCTAssertEqual(row.value.updatedAt, clock.millis)
        XCTAssertEqual(row.value.createdAt, clock.millis)
        XCTAssertEqual(changes, 1)
        XCTAssertEqual(library.coupons.map(\.id), ["c1"])
    }

    func testDeleteLeavesAHiddenTombstone() async {
        library.save(coupon(id: "c1"))
        library.deleteCoupon("c1")
        XCTAssertTrue(library.coupons.isEmpty)
        XCTAssertNil(library.coupon("c1"))
        XCTAssertNotNil(store.coupon("c1")?.deletedAt)
        XCTAssertEqual(store.coupon("c1")?.dirty, true)
    }

    func testFlagsPersist() async {
        library.save(coupon(id: "c1"))
        library.setUsed("c1", true)
        library.setFavorite("c1", true)
        XCTAssertTrue(library.coupon("c1")!.isUsed)
        XCTAssertTrue(library.coupon("c1")!.isFavorite)
        library.setUsed("c1", false)
        XCTAssertFalse(library.coupon("c1")!.isUsed)
    }

    func testDeletingAPlaceUnlinksIt() async {
        library.save(place(id: "p1")); library.save(place(id: "p2"))
        library.save(coupon(id: "c1", placeIds: ["p1", "p2"]))
        library.save(membership(id: "m1", placeIds: ["p1"]))
        library.deletePlace("p1")
        XCTAssertEqual(library.places.map(\.id), ["p2"])
        XCTAssertEqual(library.coupon("c1")?.placeIds, ["p2"])
        XCTAssertEqual(library.membership("m1")?.placeIds, [])
        XCTAssertEqual(store.coupon("c1")?.dirty, true)
    }

    func testAutoArchiveRespectsGracePeriod() async {
        let today = clock.today
        library.save(coupon(id: "old", expiresOn: today.adding(days: -10)))
        library.save(coupon(id: "recent", expiresOn: today.adding(days: -2)))
        library.save(coupon(id: "live", expiresOn: today.adding(days: 2)))
        XCTAssertEqual(library.archiveExpired(graceDays: 7), 1)
        XCTAssertTrue(library.coupon("old")!.isArchived)
        XCTAssertFalse(library.coupon("recent")!.isArchived)
        XCTAssertFalse(library.coupon("live")!.isArchived)
    }

    func testPlaceRadiusIsClamped() async {
        XCTAssertEqual(Place(name: "x", latitude: 1, longitude: 1, radiusMeters: 5).radiusMeters, Place.minRadius)
        XCTAssertEqual(Settings().clamped(), Settings())
        var s = Settings(); s.expiryLeadDays = 99; s.defaultRadiusMeters = 1
        XCTAssertEqual(s.clamped().expiryLeadDays, 14)
        XCTAssertEqual(s.clamped().defaultRadiusMeters, 100)
    }
}
