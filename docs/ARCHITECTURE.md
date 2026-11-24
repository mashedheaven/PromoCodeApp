# Architecture

Single-module Android app: Kotlin, Jetpack Compose (Material 3), Hilt, Room,
WorkManager, DataStore, Retrofit + kotlinx.serialization, Play services
location. Min SDK 26, target SDK 36.

## Principles

* **The device is the source of truth.** Every screen reads from Room through
  `Flow`s, so the UI updates itself when data changes from any source
  (an edit, a sync, a background job). Nothing waits on the network.
* **Location stays on the device.** Geofence transitions are evaluated locally;
  the backend never learns where the user is.
* **Pure rules are separate from Android.** Filtering, expiry, reminder timing
  and geofence selection live in plain Kotlin (`core/`, `ReminderPlanner`,
  `GeofencePlanner`) and are unit-tested directly.

## Data model

| Table | Purpose |
|---|---|
| `coupons` | One row per coupon. `placeIds` lists where it can be used. Expiry is a calendar date: a coupon "expiring 10 March" is valid all of that day. |
| `places` | A named location with a geofence radius (100–2000 m). Shared by any number of coupons and memberships. |
| `memberships` | Organization, number, fee and billing cycle, renewal date, reminder lead time, `placeIds`. |
| `place_alert_state` | Device-only: whether the user is inside a place and when it last alerted. |

Coupons and memberships reference places by id rather than embedding
coordinates, so a store is saved once and one geofence serves everything
redeemable there.

## Sync

Ids are UUIDs generated on the device, so a row has the same identity
everywhere and creating offline needs no server round trip.

Each synced row carries:

* `updatedAt`: set on every local edit, the basis for conflict resolution;
* `dirty`: has local changes not yet uploaded;
* `deletedAt`: a tombstone, so deletions reach other devices.

A sync run (`SyncEngine`) processes places, then coupons, then memberships:

1. **Push** dirty rows as upserts. The database keeps whichever version has the
   newer `updated_at` (last write wins, enforced by a trigger). A row's dirty flag
   is cleared only if it was not edited again while the upload was in flight.
2. **Pull** rows whose `server_updated_at` is past the saved cursor, page by page.
   A pulled row is skipped if a newer local edit is still waiting to be pushed.
3. **Purge** tombstones that have been uploaded.

Runs are triggered five seconds after any local edit (debounced, deferred until
online), at app start, every six hours, and from Settings. The Supabase
side is in `supabase/schema.sql`; row-level security ties every row to the
JWT's user, so the client never sends a user id.

Sessions are stored encrypted with an Android Keystore key and excluded from
backups. Expired access tokens are refreshed transparently once; a rejected
refresh token signs the user out without losing local data. Signing in to a
different account than last time clears the device first so data never leaks
between accounts.

## Nearby alerts

```
DB change ─▶ GeofenceRegistrar.desiredFences()  (places with redeemable items)
                │
                ▼
          GeofencePlanner.plan()  ── ≤100 fences; nearest-99 + refresh fence if more
                │
                ▼
          Play services geofencing ──(enter/exit)──▶ GeofenceBroadcastReceiver
                                                         │
                                                         ▼
                                                   ProximityAlerter
                                    (inside? cooldown? anything usable?) ─▶ Notifier
```

`GeofenceRegistrar` diffs against what it last registered and only adds or
removes the difference. Registrations are re-applied in full at app start, after
boot or update (`RestoreGeofencesReceiver`), and daily, because the system can
drop them (location toggled off, Play services data cleared).

Geofencing requires precise location with "Allow all the time". Settings walks
the user through notifications, then location, then background location, and
links to system settings when a request is permanently denied.

## Background work

| Job | When | Does |
|---|---|---|
| `SyncWorker` | after edits; every 6 h; network required | `SyncEngine.sync()`, with backoff |
| `DailyWorker` | daily, first run ~09:00 | auto-archive, expiry and renewal reminders, geofence health check |

Reminders record the date they were sent for (`reminderSentFor`), so each fires
once per expiry or renewal date and fires again if the date changes.

## Notifications

Four channels: Nearby deals (high), Expiring soon, Membership reminders,
Updates (low). Every notification deep-links (`promocode://coupon/<id>`,
`…/place/<id>`, `…/membership/<id>`, `…/expiring`) into the matching screen.

## Testing

`app/src/test` runs on the JVM with Robolectric:

* `DomainTest`: expiry, filters, search, sorting, geofence planning, reminder rules, barcodes;
* `DataTest`: repositories on a real Room database, settings, alert decisions and the notifications they post;
* `SyncTest`: two simulated devices against a fake Supabase (MockWebServer) that
  enforces the same contract as the SQL schema: upload, download, deletions,
  conflicts both ways, token refresh, revoked sessions, account switching;
* `AppSmokeTest`: the real app (Hilt graph, database, navigation) driven through
  adding, using and deleting a coupon, plus every tab.

`supabase/schema.sql` was validated against PostgreSQL 16 with a stand-in for
Supabase's `auth` schema: idempotent re-runs, row ownership, stale writes
ignored, cursor advancing, cross-user writes rejected, anonymous access denied.

## iOS app

`ios/` contains a SwiftUI app (iOS 17+) with the same features, backend and
sync rules. All platform-independent logic is in the `PromoCodeCore` Swift
package, a port of the Android rules: models, `CouponQuery`,
`ReminderPlanner`, `GeofencePlanner`, `ProximityPolicy`, retail barcode
encoding, the `Library` of edit operations, the Supabase client and
`SyncEngine`. It builds and tests on Linux, and one of its tests decodes a row
exactly as the Android app uploads it to keep the two wire formats identical.

The app layer is thin: SwiftData implements PromoCodeCore's `RecordStore`,
the session lives in the Keychain (this device only, readable after first
unlock so background region events can sync), and `AppModel` triggers side
effects after every change: geofence update, reminder rescheduling, and a
debounced sync.

Platform differences:

* **Regions:** iOS monitors at most 20, so the planner keeps the nearest 19
  plus a refresh region. Newly added regions are checked with `requestState`,
  matching Android's "alert if already inside".
* **Reminders:** iOS has no reliable daily job, so reminders are scheduled
  ahead as dated local notifications (iOS keeps up to 64). A `ReminderLedger`
  records which have fired so a reminder whose window has already started
  fires once. A background refresh task (`BGAppRefreshTask`) and every app
  launch roll the schedule forward and sync.
* **Background launch:** `AppModel` is built in the app delegate at launch, so
  the location manager exists when iOS wakes the app for a region event.
