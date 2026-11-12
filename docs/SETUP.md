# Setup

Everything here is optional. Without any of it the app is fully functional,
stores data on the device, and is included in Android's own device backup.

Secrets go in `PromoCodeApp/local.properties` (git-ignored); see
`local.properties.example`. Environment variables with the same names work too,
which is handy for CI.

## Cloud backup & sync (Supabase)

1. Create a project at [supabase.com](https://supabase.com).
2. Open **SQL Editor**, paste the contents of `PromoCodeApp/supabase/schema.sql`
   and run it. It is safe to run again after updates.
3. Under **Project Settings → API**, copy the project URL and the `anon` public key:

   ```properties
   SUPABASE_URL=https://abcdefghijklmnop.supabase.co
   SUPABASE_ANON_KEY=eyJhbGciOi...
   ```

4. Under **Authentication → Providers → Email**, decide whether new accounts must
   confirm their e-mail. The app handles both: it asks the user to confirm and then sign in.
5. Rebuild. **Settings → Backup & sync** now offers sign in.

The `anon` key is meant to ship in apps: row-level security in the schema means
a user can only read and write their own rows, and anonymous requests see nothing.
Never put the `service_role` key in the app.

**Optional:** to purge deletion records older than 90 days, enable the `pg_cron`
extension and run the `cron.schedule` line at the bottom of the schema.

## Map in the place editor (Google Maps)

1. In Google Cloud Console, enable **Maps SDK for Android** and create an API key.
2. Restrict it to Android apps with package `com.promocodeapp` and your signing
   certificate's SHA-1 (`./gradlew signingReport`).
3. Add `MAPS_API_KEY=...` to `local.properties`.

Without a key the map is hidden; places can still be set from the current
location or by address search.

## Server pushes (Firebase Cloud Messaging)

Nearby alerts and reminders are generated on the device and do **not** need
Firebase. FCM is only for messages you send from a server (announcements etc.).

1. Create a Firebase project and add an Android app with package `com.promocodeapp`.
2. Download `google-services.json` into `PromoCodeApp/app/`. The build applies
   the Google services plugin automatically when this file is present.

Signed-in devices register their token in the `device_tokens` table. Send a data
message with `title`, `body` and optionally `coupon_id` or `membership_id`; tapping
the notification opens that item.

## Release builds

Release builds are minified with R8. Configure signing as usual in Android Studio
(**Build → Generate Signed Bundle**) or with a `signingConfigs` block.
