# PromoCode

An Android app that remembers your promo codes, coupons and memberships, and
**reminds you when you arrive somewhere you can use them**.

You enter a coupon once (type it or scan its barcode), attach the stores where
it works, and forget about it. When you walk into one of those stores your phone
shows the code. Coupons about to expire and memberships about to renew get a
reminder too.

The app works fully offline. Signing in to an optional Supabase backend adds
backup and sync between devices.

## Features

| | |
|---|---|
| **Coupons** | Store, code, discount (percent, amount, buy-one-get-one, free shipping), expiry date, minimum spend, category, notes. Search, filters (active, favorites, expiring soon, used, expired, archived) and sorting. Copy or share a code in one tap. |
| **Barcodes** | Scan a barcode or QR code with Google's code scanner (no camera permission needed). The coupon screen redraws it so the cashier can scan your phone. |
| **Places & nearby alerts** | Save stores by current location, address search or a tap on the map (map optional). Each place gets a geofence; arriving shows the coupons and memberships you can use there. |
| **Memberships** | Gyms, subscriptions, loyalty cards: member number (scannable), fee, billing cycle, renewal date. Shows your total yearly spend and reminds you before renewals. |
| **Reminders** | "Expiring soon" for coupons, renewal reminders for memberships, each sent once per date. Expired coupons are archived after a week. |
| **Backup & sync** | Optional e-mail account. Edits made offline sync when back online; changes on one device appear on the others. |

## How the location alerts stay reliable and cheap

* Geofencing is done by Google Play services, which batches location checks;
  the app never polls GPS and never uploads your location.
* Only places with a usable coupon or an active membership are watched.
  Using or archiving the last coupon for a store stops its geofence.
* Android allows 100 geofences per app. With more places than that, the
  nearest 99 are watched plus one "refresh" fence; leaving it re-picks the nearest set.
* A two-hour cooldown per place and "already inside" tracking stop repeat
  alerts from GPS jitter or from living next door to a store.
* Geofences are restored after a reboot or app update, and re-checked daily.

## Project layout

```
PromoCodeApp/                 Android project (open this folder in Android Studio)
  app/src/main/java/com/promocodeapp/
    core/                     Domain models and rules (no Android dependencies)
    data/local/               Room database
    data/remote/              Supabase auth + REST client, encrypted session store
    data/repository/          Repositories used by the UI
    sync/                     Two-way sync engine
    location/                 Geofence planning, registration, alerts
    notifications/            Channels, notifications, reminder rules, FCM
    work/                     WorkManager jobs (sync, daily reminders)
    ui/                       Jetpack Compose screens
  app/src/test/               Unit, database, sync and UI tests (Robolectric)
  supabase/schema.sql         Backend schema with row-level security
docs/                         Setup guide, architecture notes, original concept
```

## Getting started

Requirements: Android Studio (recent stable), JDK 17+, a device or emulator
with Google Play services, Android 8.0 (API 26) or newer.

```bash
cd PromoCodeApp
./gradlew installDebug
```

That's all you need for the full offline app. To enable cloud sync, the map,
or server pushes, see [docs/SETUP.md](docs/SETUP.md).

## Tests

```bash
cd PromoCodeApp
./gradlew testDebugUnitTest
```

The suite runs on the JVM (no device needed) and covers the domain rules,
the database, nearby-alert decisions, a two-device sync against a fake
Supabase server (conflicts, deletions, token refresh, account switching), and
a UI smoke test of the main flow.

More: [architecture](docs/ARCHITECTURE.md) · [requirements and roadmap](docs/concept/requirements.txt)
