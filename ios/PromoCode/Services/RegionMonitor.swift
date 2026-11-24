import CoreLocation
import Foundation
import OSLog
import PromoCodeCore

let regionLog = Logger(subsystem: "com.promocodeapp.ios", category: "regions")

/// Watches store locations with CLMonitor. The system wakes the app on entry
/// and exit even when it isn't running, and conditions survive restarts.
///
/// CLMonitor replaces CLCircularRegion monitoring, which newer iOS versions
/// report as unavailable (`isMonitoringAvailable` returns false).
@MainActor
final class RegionMonitor: NSObject, CLLocationManagerDelegate {
    /// iOS hands relaunch events to the monitor with this name, so it must not change.
    static let monitorName = "PromoCodePlaces"

    private let manager = CLLocationManager()
    private var monitor: CLMonitor?
    private var monitorStarted = false
    private var desired: [Fence] = []
    private var applyTask: Task<Void, Never>?
    /// A CLServiceSession (iOS 18+). Without one requiring Always, CLMonitor
    /// events aren't delivered while the app isn't in use.
    private var alwaysSession: AnyObject?
    private var locationWaiters: [CheckedContinuation<CLLocation?, Never>] = []

    var onEnter: (String) -> Void = { _ in }
    var onExit: (String) -> Void = { _ in }
    var onAuthorizationChange: () -> Void = {}

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        // Attach at once: after a relaunch for a geofence event, the event is
        // delivered only once the monitor is recreated and observed. Creating
        // a monitor prompts for location, so wait until Always is granted.
        if canMonitor { startMonitorIfNeeded() }
    }

    var authorization: CLAuthorizationStatus { manager.authorizationStatus }
    var hasPreciseLocation: Bool { manager.accuracyAuthorization == .fullAccuracy }
    /// Background monitoring needs "Always" and precise location.
    var canMonitor: Bool { authorization == .authorizedAlways && hasPreciseLocation }

    func requestWhenInUse() { manager.requestWhenInUseAuthorization() }

    /// iOS shows this prompt only once; afterwards the user changes it in Settings.
    func requestAlways() { manager.requestAlwaysAuthorization() }

    /// Brings the monitored conditions in line with [places]; only changes are applied.
    func update(places: [Fence]) {
        desired = places
        guard canMonitor else {
            regionLog.info("Not monitoring \(places.count) places: auth=\(self.authorization.rawValue) precise=\(self.hasPreciseLocation)")
            return
        }
        holdAlwaysSession()
        startMonitorIfNeeded()
        let origin = manager.location.map { LatLng($0.coordinate.latitude, $0.coordinate.longitude) }
        let fences = GeofencePlanner.plan(places, origin: origin).all
        // Run updates one after another so overlapping edits can't interleave.
        let previous = applyTask
        applyTask = Task { [weak self] in
            await previous?.value
            await self?.apply(fences)
        }
    }

    /// Only created once Always is granted, so it never triggers a prompt by itself.
    private func holdAlwaysSession() {
        guard alwaysSession == nil, #available(iOS 18.0, *) else { return }
        alwaysSession = CLServiceSession(authorization: .always)
    }

    private func startMonitorIfNeeded() {
        guard !monitorStarted else { return }
        monitorStarted = true
        Task { await startMonitor() }
    }

    private func startMonitor() async {
        let monitor = await CLMonitor(Self.monitorName)
        self.monitor = monitor
        // Anything requested before the monitor existed is applied now.
        update(places: desired)
        do {
            for try await event in await monitor.events {
                handle(id: event.identifier, state: event.state)
            }
        } catch {
            regionLog.error("Monitor events ended: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func handle(id: String, state: CLMonitor.Event.State) {
        regionLog.info("State \(String(describing: state), privacy: .public) for \(id, privacy: .public)")
        let isRefresh = id == GeofencePlanner.refreshFenceId
        switch state {
        case .satisfied:
            // Like Android's INITIAL_TRIGGER_ENTER, a new condition the user is
            // already inside reports satisfied straight away.
            if !isRefresh { onEnter(id) }
        case .unsatisfied:
            if isRefresh { update(places: desired) } else { onExit(id) }
        default:
            break
        }
    }

    private func apply(_ fences: [Fence]) async {
        guard let monitor else { return }  // startMonitor re-applies once it's ready
        // -1 when the device reports no limit; don't let that shrink every fence.
        let reported = manager.maximumRegionMonitoringDistance
        let maxRadius = reported > 0 ? reported : .greatestFiniteMagnitude
        let wanted = Dictionary(fences.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })

        for id in await monitor.identifiers {
            if let fence = wanted[id],
               let condition = await monitor.record(for: id)?.condition as? CLMonitor.CircularGeographicCondition,
               abs(condition.radius - min(fence.radius, maxRadius)) < 1,
               abs(condition.center.latitude - fence.center.latitude) < 1e-7,
               abs(condition.center.longitude - fence.center.longitude) < 1e-7 {
                continue
            }
            await monitor.remove(id)
        }

        let current = Set(await monitor.identifiers)
        for fence in fences where !current.contains(fence.id) {
            let condition = CLMonitor.CircularGeographicCondition(
                center: CLLocationCoordinate2D(latitude: fence.center.latitude, longitude: fence.center.longitude),
                radius: min(fence.radius, maxRadius))
            await monitor.add(condition, identifier: fence.id)
            regionLog.info("Monitoring \(fence.id, privacy: .public) radius \(condition.radius)")
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

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let last = locations.last
        MainActor.assumeIsolated { finishLocation(last) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        MainActor.assumeIsolated { finishLocation(nil) }
    }
}
