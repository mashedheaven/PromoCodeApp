import Foundation

/// User preferences; same options as the Android app.
public struct Settings: Codable, Equatable, Sendable {
    public var nearbyAlerts = true
    public var expiryReminders = true
    public var membershipReminders = true
    public var defaultRadiusMeters = Place.defaultRadius
    /// Days before expiry for the "expiring soon" reminder.
    public var expiryLeadDays = 2
    /// Archive coupons a week after they expire.
    public var autoArchiveExpired = true

    public init() {}

    public func clamped() -> Settings {
        var s = self
        s.defaultRadiusMeters = min(max(s.defaultRadiusMeters, Place.minRadius), Place.maxRadius)
        s.expiryLeadDays = min(max(s.expiryLeadDays, 0), 14)
        return s
    }
}

/// A local notification to schedule for a calendar date.
public struct PlannedReminder: Hashable, Sendable {
    /// Stable per item and date: rescheduling replaces it, and a changed date gets a new one.
    public var id: String
    public var fireAt: Date
    public var title: String
    public var body: String
    public var link: DeepLink
}

/// iOS has no daily background job to rely on, so reminders are scheduled
/// ahead as dated local notifications. This works out which ones.
public enum ReminderPlanner {
    /// iOS keeps at most 64 pending local notifications per app.
    public static let maxPending = 60
    public static let reminderHour = 9

    /// - Parameter delivered: ids of reminders already shown; they are not planned again.
    public static func plan(
        coupons: [Coupon], memberships: [Membership], settings: Settings,
        delivered: Set<String>, now: Date, timeZone: TimeZone
    ) -> [PlannedReminder] {
        let today = Day(now, in: timeZone)
        var out: [PlannedReminder] = []

        if settings.expiryReminders {
            for c in coupons where c.isRedeemable(today) {
                guard let expires = c.expiresOn else { continue }
                let id = "expiry-\(c.id)-\(expires.iso)"
                guard !delivered.contains(id),
                      let fireAt = fireDate(window: expires.adding(days: -settings.expiryLeadDays), last: expires, now: now, timeZone: timeZone)
                else { continue }
                let fireDay = Day(fireAt, in: timeZone)
                out.append(PlannedReminder(
                    id: id, fireAt: fireAt,
                    title: "\(c.merchant) coupon \(whenText(from: fireDay, to: expires, verb: "expires"))",
                    body: c.headline, link: .coupon(c.id)))
            }
        }

        if settings.membershipReminders {
            for m in memberships where m.isActive && m.reminderDays > 0 {
                guard let renews = m.nextRenewal(today) else { continue }
                let id = "renewal-\(m.id)-\(renews.iso)"
                guard !delivered.contains(id),
                      let fireAt = fireDate(window: renews.adding(days: -m.reminderDays), last: renews, now: now, timeZone: timeZone)
                else { continue }
                let fireDay = Day(fireAt, in: timeZone)
                let cost = m.fee.flatMap { $0 > 0 ? " · \(Money.format($0, currency: m.currency))" : nil } ?? ""
                out.append(PlannedReminder(
                    id: id, fireAt: fireAt,
                    title: "\(m.organization) \(whenText(from: fireDay, to: renews, verb: "renews"))",
                    body: "Renewal date: \(renews.iso)\(cost)", link: .membership(m.id)))
            }
        }

        return Array(out.sorted { $0.fireAt < $1.fireAt }.prefix(maxPending))
    }

    /// 09:00 on the first day of the window; if that has passed but the item
    /// is still valid, a minute from now. Nil once the last valid day is over.
    static func fireDate(window: Day, last: Day, now: Date, timeZone: TimeZone) -> Date? {
        let planned = window.date(hour: reminderHour, in: timeZone)
        if planned > now { return planned }
        let endOfLastDay = last.adding(days: 1).date(in: timeZone)
        return now < endOfLastDay ? now.addingTimeInterval(60) : nil
    }

    static func whenText(from fireDay: Day, to target: Day, verb: String) -> String {
        switch fireDay.days(until: target) {
        case ...0: return "\(verb) today"
        case 1: return "\(verb) tomorrow"
        case let d: return "\(verb) in \(d) days"
        }
    }
}

/// Remembers which reminders were scheduled and which have already fired,
/// so a reminder whose window has started fires once rather than on every reschedule.
public struct ReminderLedger: Codable, Equatable, Sendable {
    public var scheduled: [String: Date] = [:]
    public var delivered: Set<String> = []

    public init() {}

    /// Anything scheduled for before [now] has been shown by the system.
    public mutating func advance(to now: Date) {
        for (id, date) in scheduled where date <= now { delivered.insert(id) }
        scheduled = scheduled.filter { $0.value > now }
        // Ids embed the date, so old ones can never be planned again; keep the set small.
        if delivered.count > 1000 { delivered = Set(delivered.sorted().suffix(500)) }
    }

    public mutating func record(_ plans: [PlannedReminder]) {
        scheduled = Dictionary(plans.map { ($0.id, $0.fireAt) }, uniquingKeysWith: { a, _ in a })
    }
}
