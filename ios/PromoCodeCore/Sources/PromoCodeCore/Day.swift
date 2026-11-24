import Foundation

/// A calendar date with no time or time zone, like `java.time.LocalDate`.
/// Coupons "expire on" a day and stay valid through the end of it.
public struct Day: Hashable, Comparable, Sendable, Codable, CustomStringConvertible {
    /// Days since 1970-01-01.
    public let epochDay: Int

    public init(epochDay: Int) { self.epochDay = epochDay }

    public init(year: Int, month: Int, day: Int) {
        // Howard Hinnant's days_from_civil.
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        epochDay = era * 146_097 + doe - 719_468
    }

    /// Parses `yyyy-MM-dd`. Returns nil for anything else or an impossible date.
    public init?(iso: String) {
        let parts = iso.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]),
              (1...12).contains(m), (1...31).contains(d) else { return nil }
        self.init(year: y, month: m, day: d)
        guard components == (y, m, d) else { return nil } // rejects e.g. 2026-02-30
    }

    public var components: (year: Int, month: Int, day: Int) {
        // Howard Hinnant's civil_from_days.
        let z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        return (yoe + era * 400 + (m <= 2 ? 1 : 0), m, d)
    }

    public var iso: String {
        let c = components
        return String(format: "%04d-%02d-%02d", c.year, c.month, c.day)
    }

    public var description: String { iso }

    public func adding(days: Int) -> Day { Day(epochDay: epochDay + days) }

    /// Calendar-month arithmetic, clamping to the month's last day (Jan 31 + 1 month = Feb 28/29).
    public func adding(months: Int) -> Day {
        let c = components
        let total = c.year * 12 + (c.month - 1) + months
        let year = Int((Double(total) / 12).rounded(.down))
        let month = total - year * 12 + 1
        let day = min(c.day, Day.daysIn(year: year, month: month))
        return Day(year: year, month: month, day: day)
    }

    public func adding(years: Int) -> Day { adding(months: years * 12) }

    public func days(until other: Day) -> Int { other.epochDay - epochDay }

    public static func daysIn(year: Int, month: Int) -> Int {
        let next = month == 12 ? Day(year: year + 1, month: 1, day: 1) : Day(year: year, month: month + 1, day: 1)
        return next.epochDay - Day(year: year, month: month, day: 1).epochDay
    }

    /// The day [date] falls on in [timeZone].
    public init(_ date: Date, in timeZone: TimeZone = .current) {
        let seconds = date.timeIntervalSince1970 + Double(timeZone.secondsFromGMT(for: date))
        epochDay = Int((seconds / 86_400).rounded(.down))
    }

    public static func today(_ clock: Clock = SystemClock()) -> Day { Day(clock.now, in: clock.timeZone) }

    /// The instant [hour]:[minute] happens on this day in [timeZone].
    public func date(hour: Int = 0, minute: Int = 0, in timeZone: TimeZone = .current) -> Date {
        let local = Double(epochDay) * 86_400 + Double(hour * 3600 + minute * 60)
        // Resolve the offset at that local time (correct across DST changes).
        let guess = Date(timeIntervalSince1970: local - Double(timeZone.secondsFromGMT(for: Date(timeIntervalSince1970: local))))
        return Date(timeIntervalSince1970: local - Double(timeZone.secondsFromGMT(for: guess)))
    }

    public static func < (lhs: Day, rhs: Day) -> Bool { lhs.epochDay < rhs.epochDay }

    // Encoded as "yyyy-MM-dd", the same as the Android app and Supabase `date` columns.
    public init(from decoder: Decoder) throws {
        let text = try decoder.singleValueContainer().decode(String.self)
        guard let day = Day(iso: text) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Bad date \(text)"))
        }
        self = day
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(iso)
    }
}

/// Injectable time source so rules can be tested at fixed dates.
public protocol Clock: Sendable {
    var now: Date { get }
    var timeZone: TimeZone { get }
}

public extension Clock {
    var millis: Int64 { Int64((now.timeIntervalSince1970 * 1000).rounded()) }
    var today: Day { Day(now, in: timeZone) }
}

public struct SystemClock: Clock {
    public init() {}
    public var now: Date { Date() }
    public var timeZone: TimeZone { .current }
}
