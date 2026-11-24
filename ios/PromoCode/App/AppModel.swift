import BackgroundTasks
import Foundation
import Observation
import PromoCodeCore
import UIKit

/// Owns the app's services and is the one place side effects are triggered:
/// every data change refreshes reminders and geofences and schedules a sync.
@MainActor
@Observable
final class AppModel {
    static let refreshTaskId = "com.promocodeapp.ios.refresh"

    let library: Library
    let prefs = Preferences()
    let accounts: AccountManager
    let router = Router()
    @ObservationIgnored let sync: SyncEngine
    @ObservationIgnored let regions = RegionMonitor()
    @ObservationIgnored let notifications = Notifications()
    @ObservationIgnored private let sessions = KeychainSessionStore()
    @ObservationIgnored private var syncTask: Task<Void, Never>?
    @ObservationIgnored private var refreshTask: Task<Void, Never>?

    /// Bumped on every change so views re-read data.
    private(set) var revision = 0
    private(set) var session: Session?
    private(set) var lastSyncAt: Date?
    private(set) var syncError: String?
    private(set) var isSyncing = false
    private(set) var permissionsRevision = 0

    var settings: Settings {
        didSet {
            prefs.settings = settings
            scheduleRefresh()
        }
    }

    init() {
        let store: RecordStore
        do {
            store = try SwiftDataStore()
        } catch {
            // A damaged database must not stop the app from opening.
            store = (try? SwiftDataStore(inMemory: true)) ?? InMemoryStore()
        }
        library = Library(store: store)
        let client = SupabaseClient(config: .fromBundle, sessions: sessions)
        sync = SyncEngine(client: client, store: store, keys: prefs)
        accounts = AccountManager(client: client, store: store, keys: prefs)
        settings = prefs.settings
        session = sessions.session
        lastSyncAt = sync.lastSyncAt

        library.onChange = { [weak self] in self?.dataChanged(syncSoon: true) }
        accounts.onSignedIn = { [weak self] in
            self?.session = self?.sessions.session
            self?.dataChanged(syncSoon: false)
            Task { await self?.syncNow() }
        }
        regions.onEnter = { [weak self] id in self?.arrived(at: id) }
        regions.onExit = { [weak self] id in self?.left(id) }
        regions.onAuthorizationChange = { [weak self] in self?.permissionsRevision += 1 }
        notifications.onOpen = { [weak self] link in self?.router.open(link) }
    }

    // MARK: data for views

    var coupons: [Coupon] { _ = revision; return library.coupons }
    var places: [Place] { _ = revision; return library.places }
    var memberships: [Membership] { _ = revision; return library.memberships }
    var today: Day { _ = revision; return Day.today() }

    func coupon(_ id: String) -> Coupon? { _ = revision; return library.coupon(id) }
    func place(_ id: String) -> Place? { _ = revision; return library.place(id) }
    func membership(_ id: String) -> Membership? { _ = revision; return library.membership(id) }

    var syncAvailable: Bool { accounts.isAvailable }

    /// Re-renders permission-dependent screens (CoreLocation state isn't observable).
    func permissionsChanged() { permissionsRevision += 1 }

    // MARK: lifecycle

    /// App launch, foregrounding, and background refresh all come through here.
    func refreshAll() {
        if settings.autoArchiveExpired { library.archiveExpired() }
        dataChanged(syncSoon: false)
        Task { await syncNow(quiet: true) }
    }

    private func dataChanged(syncSoon: Bool) {
        revision += 1
        session = sessions.session
        scheduleRefresh()
        if syncSoon { scheduleSync() }
    }

    /// Coalesces bursts of edits into one reminder and geofence update.
    private func scheduleRefresh() {
        refreshTask?.cancel()
        refreshTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled, let self else { return }
            await self.applySideEffects()
        }
    }

    private func applySideEffects() async {
        let today = Day.today()
        let watch = settings.nearbyAlerts
            ? GeofencePlanner.placesToWatch(places: library.places, coupons: library.coupons, memberships: library.memberships, today: today)
            : []
        regions.update(places: watch.map(GeofencePlanner.fence(for:)))

        var ledger = prefs.reminderLedger
        ledger.advance(to: Date())
        let plans = ReminderPlanner.plan(
            coupons: library.coupons, memberships: library.memberships, settings: settings,
            delivered: ledger.delivered, now: Date(), timeZone: .current)
        ledger.record(plans)
        prefs.reminderLedger = ledger
        await notifications.schedule(plans)
    }

    // MARK: sync

    /// Debounced upload a few seconds after an edit.
    private func scheduleSync() {
        guard syncAvailable, sessions.session != nil else { return }
        syncTask?.cancel()
        syncTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled else { return }
            await self?.syncNow(quiet: true)
        }
    }

    func syncNow(quiet: Bool = false) async {
        guard syncAvailable, sessions.session != nil else { return }
        isSyncing = true
        defer { isSyncing = false }
        do {
            try await sync.sync()
            syncError = nil
        } catch let e as SupabaseError {
            syncError = quiet && e.isTransient ? nil : e.errorDescription
        } catch {
            syncError = quiet ? nil : error.localizedDescription
        }
        lastSyncAt = sync.lastSyncAt
        session = sessions.session
        revision += 1
        scheduleRefresh()
    }

    func signOut() async {
        await accounts.signOut()
        session = nil
    }

    // MARK: nearby alerts

    private func arrived(at placeId: String) {
        let now = SystemClock().millis
        var states = prefs.alertStates
        let decision = ProximityPolicy.onEnter(states[placeId], now: now, alertsEnabled: settings.nearbyAlerts)
        var shown = false
        if decision == .notify, let place = library.place(placeId) {
            let today = Day.today()
            let usable = library.coupons.filter { $0.placeIds.contains(placeId) && $0.isRedeemable(today) }
                .sorted { ($0.expiresOn?.epochDay ?? .max) < ($1.expiresOn?.epochDay ?? .max) }
            let valid = library.memberships.filter { $0.placeIds.contains(placeId) && $0.isActive }
            if let alert = NearbyAlert.make(place: place, coupons: usable, memberships: valid) {
                notifications.postNearby(alert, placeId: placeId)
                shown = true
            }
        }
        states[placeId] = ProximityPolicy.stateAfterEnter(states[placeId], decision: decision, shown: shown, now: now)
        prefs.alertStates = states
    }

    private func left(_ placeId: String) {
        var states = prefs.alertStates
        states[placeId] = ProximityPolicy.stateAfterExit(states[placeId], now: SystemClock().millis)
        prefs.alertStates = states
        notifications.removeNearby(placeId: placeId)
    }

    // MARK: background refresh

    func registerBackgroundRefresh() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.refreshTaskId, using: nil) { task in
            Task { @MainActor in
                self.scheduleBackgroundRefresh()
                let work = Task { @MainActor in
                    self.refreshAll()
                    await self.syncNow(quiet: true)
                    await self.applySideEffects()
                }
                task.expirationHandler = { work.cancel() }
                await work.value
                task.setTaskCompleted(success: true)
            }
        }
    }

    /// Lets iOS wake the app occasionally to sync and roll reminders forward.
    func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: Self.refreshTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }
}
