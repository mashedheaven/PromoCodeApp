import Foundation
import UserNotifications
import PromoCodeCore

/// Local notifications: nearby alerts posted on arrival, and dated reminders
/// scheduled ahead of time (iOS has no daily background job to rely on).
@MainActor
final class Notifications: NSObject, UNUserNotificationCenterDelegate {
    private let center = UNUserNotificationCenter.current()
    var onOpen: (DeepLink) -> Void = { _ in }

    static let reminderPrefixes = ["expiry-", "renewal-"]

    override init() {
        super.init()
        center.delegate = self
    }

    func status() async -> UNAuthorizationStatus { await center.notificationSettings().authorizationStatus }

    @discardableResult
    func requestPermission() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    func postNearby(_ alert: NearbyAlert, placeId: String) {
        let content = UNMutableNotificationContent()
        content.title = alert.title
        content.body = alert.lines.count > 1 ? ([alert.body] + alert.lines.prefix(4)).joined(separator: "\n") : alert.body
        content.sound = .default
        content.threadIdentifier = "nearby"
        content.userInfo = ["link": alert.link.url.absoluteString]
        center.add(UNNotificationRequest(identifier: "nearby-\(placeId)", content: content, trigger: nil))
    }

    /// The reminder is only useful while the user is there.
    func removeNearby(placeId: String) {
        center.removeDeliveredNotifications(withIdentifiers: ["nearby-\(placeId)"])
    }

    /// Replaces every pending reminder with [plans].
    func schedule(_ plans: [PlannedReminder]) async {
        let pending = await center.pendingNotificationRequests().map(\.identifier)
        let stale = pending.filter { id in Self.reminderPrefixes.contains { id.hasPrefix($0) } }
        center.removePendingNotificationRequests(withIdentifiers: stale)
        let calendar = Calendar.current
        for plan in plans {
            let content = UNMutableNotificationContent()
            content.title = plan.title
            content.body = plan.body
            content.sound = .default
            content.threadIdentifier = plan.id.hasPrefix("expiry-") ? "expiring" : "renewals"
            content.userInfo = ["link": plan.link.url.absoluteString]
            let parts = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: plan.fireAt)
            let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: plan.id, content: content, trigger: trigger))
        }
    }

    // MARK: UNUserNotificationCenterDelegate

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter, willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let text = response.notification.request.content.userInfo["link"] as? String,
              let url = URL(string: text), let link = DeepLink(url: url) else { return }
        await MainActor.run { onOpen(link) }
    }
}
