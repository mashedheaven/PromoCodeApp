# PromoCode for iOS

The iPhone version of PromoCode. It has the same features as the Android app
and uses the same optional Supabase backend, so one account syncs across
Android and iOS.

Requirements: a Mac with Xcode 16 or newer, iOS 17 or newer.

## Run it

```bash
brew install xcodegen
cd ios
xcodegen generate
open PromoCode.xcodeproj
```

Pick an iPhone simulator and press Run. To run on your own iPhone, set your
team in **Signing & Capabilities** (or `DEVELOPMENT_TEAM` in
`Config/Secrets.xcconfig`).

The Xcode project is generated from `project.yml`, so it isn't committed. Run
`xcodegen generate` again after pulling changes that add or remove files.

## Optional: cloud sync

Copy `Config/Secrets.example.xcconfig` to `Config/Secrets.xcconfig` and fill in
the host and anon key of the same Supabase project the Android app uses (see
[docs/SETUP.md](../docs/SETUP.md)). Enter the host only, without `https://`.

## Layout

```
ios/
  project.yml            XcodeGen spec (targets, Info.plist, permissions)
  Config/                Build settings; Secrets.xcconfig is git-ignored
  PromoCode/
    App/                 Entry point, AppModel (wires everything), Router
    Data/                SwiftData storage, Keychain session, preferences
    Services/            Region monitoring, notifications
    Views/               SwiftUI screens
  PromoCodeCore/         Swift package with all platform-independent logic
```

`PromoCodeCore` holds the rules and sync engine and has no UIKit, SwiftUI or
CoreLocation code, so it builds and tests anywhere Swift runs:

```bash
swift test --package-path ios/PromoCodeCore
```

## How it differs from Android

| | Android | iOS |
|---|---|---|
| Geofences | Up to 100 | Up to 20 conditions; the nearest 19 plus a refresh condition when you have more places |
| Reminders | Daily background job | Dated local notifications scheduled ahead (up to 60 pending) |
| Address search | Android Geocoder | MapKit search (no API key needed) |
| Map | Google Maps, needs a key | Apple Maps, always available |
| Barcode drawing | ZXing, all formats | QR, Aztec, PDF417, Code 128, EAN-13/8 and UPC-A; other formats show the code as text |
| Server pushes | Firebase (optional) | Not included (needs an Apple push certificate) |

Nearby alerts need **Always** location access with **Precise Location** on.
Settings walks through notifications, location, and then "Always", and sends
you to the system Settings app when iOS won't ask again.

Geofences use `CLMonitor`. The iOS 27 simulator can't monitor them at all
(every condition reports `unmonitored`), so test nearby alerts on a real
iPhone. Region events are logged under the `com.promocodeapp.ios` subsystem,
category `regions`, so you can follow them in Console.
