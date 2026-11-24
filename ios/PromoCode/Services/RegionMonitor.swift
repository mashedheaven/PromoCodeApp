import CoreLocation
import Foundation
import PromoCodeCore

/// Watches store locations with iOS region monitoring. The system wakes the
/// app on entry and exit even when it isn't running; regions survive restarts.
@MainActor
final class RegionMonitor: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var desired: [Fence] = []
    private var locationWaiters: [CheckedContinuation<CLLocation?, Never>] = []

    var onEnter: (String) -> Void = { _ in }
    var onExit: (String) -> Void = { _ in }
    var onAuthorizationChange: () -> Void = {}

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    var authorization: CLAuthorizationStatus { manager.authorizationStatus }
    var hasPreciseLocation: Bool { manager.accuracyAuthorization == .fullAccuracy }
    /// Region monitoring only works with "Always" and precise location.
    var canMonitor: Bool {
        authorization == .authorizedAlways && hasPreciseLocation && CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self)
    }

    func requestWhenInUse() { manager.requestWhenInUseAuthorization() }

    /// iOS shows this prompt only once; afterwards the user changes it in Settings.
    func requestAlways() { manager.requestAlwaysAuthorization() }

    /// Brings the monitored regions in line with [places]; only changes are applied.
    func update(places: [Fence]) {
        desired = places
        guard canMonitor else { return }
        let origin = manager.location.map { LatLng($0.coordinate.latitude, $0.coordinate.longitude) }
        let plan = GeofencePlanner.plan(places, origin: origin)
        apply(plan.all)
    }

    private func apply(_ fences: [Fence]) {
        let maxRadius = manager.maximumRegionMonitoringDistance
        let wanted = Dictionary(fences.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        for region in manager.monitoredRegions {
            guard let circle = region as? CLCircularRegion, let fence = wanted[region.identifier],
                  abs(circle.radius - min(fence.radius, maxRadius)) < 1,
                  abs(circle.center.latitude - fence.center.latitude) < 1e-7,
                  abs(circle.center.longitude - fence.center.longitude) < 1e-7
            else {
                manager.stopMonitoring(for: region)
                continue
            }
        }
        let current = Set(manager.monitoredRegions.map(\.identifier))
        for fence in fences where !current.contains(fence.id) {
            let region = CLCircularRegion(
                center: CLLocationCoordinate2D(latitude: fence.center.latitude, longitude: fence.center.longitude),
                radius: min(fence.radius, maxRadius), identifier: fence.id)
            let isRefresh = fence.id == GeofencePlanner.refreshFenceId
            region.notifyOnEntry = !isRefresh
            region.notifyOnExit = true
            manager.startMonitoring(for: region)
        }
    }

    /// One fresh position, e.g. to pin a store. Nil if unavailable.
    func currentLocation() async -> CLLocation? {
        guard authorization == .authorizedAlways || authorization == .authorizedWhenInUse else { return nil }
        return await withCheckedContinuation { continuation in
            locationWaiters.append(continuation)
            if locationWaiters.count == 1 { manager.requestLocation() }
        }
    }

    private func finishLocation(_ location: CLLocation?) {
        let waiters = locationWaiters
        locationWaiters.removeAll()
        waiters.forEach { $0.resume(returning: location) }
    }

    // MARK: CLLocationManagerDelegate (called on the main thread, where the manager was made)

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        MainActor.assumeIsolated {
            update(places: desired)
            onAuthorizationChange()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didStartMonitoringFor region: CLRegion) {
        // Like Android's INITIAL_TRIGGER_ENTER: alert if already inside a new region.
        manager.requestState(for: region)
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        let id = region.identifier
        MainActor.assumeIsolated {
            if state == .inside && id != GeofencePlanner.refreshFenceId { onEnter(id) }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        let id = region.identifier
        MainActor.assumeIsolated { if id != GeofencePlanner.refreshFenceId { onEnter(id) } }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        let id = region.identifier
        MainActor.assumeIsolated {
            if id == GeofencePlanner.refreshFenceId { update(places: desired) } else { onExit(id) }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?, withError error: Error) {
        // Usually a transient system condition; the next update retries.
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let last = locations.last
        MainActor.assumeIsolated { finishLocation(last) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        MainActor.assumeIsolated { finishLocation(nil) }
    }
}
