package com.promocodeapp.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.promocodeapp.core.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val nearbyAlerts: Boolean = true,
    val expiryReminders: Boolean = true,
    val membershipReminders: Boolean = true,
    /** Radius pre-filled for new places. */
    val defaultRadiusMeters: Int = Place.DEFAULT_RADIUS_METERS,
    /** Days before expiry to send the "expiring soon" reminder. */
    val expiryLeadDays: Int = 2,
    /** Move coupons to the archive once they have been expired for a week. */
    val autoArchiveExpired: Boolean = true,
)

@Singleton
class SettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            nearbyAlerts = p[NEARBY] ?: true,
            expiryReminders = p[EXPIRY] ?: true,
            membershipReminders = p[MEMBERSHIP] ?: true,
            defaultRadiusMeters = p[RADIUS] ?: Place.DEFAULT_RADIUS_METERS,
            expiryLeadDays = p[LEAD_DAYS] ?: 2,
            autoArchiveExpired = p[AUTO_ARCHIVE] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { p ->
            val s = transform(
                Settings(
                    p[NEARBY] ?: true, p[EXPIRY] ?: true, p[MEMBERSHIP] ?: true,
                    p[RADIUS] ?: Place.DEFAULT_RADIUS_METERS, p[LEAD_DAYS] ?: 2, p[AUTO_ARCHIVE] ?: true,
                )
            )
            p[NEARBY] = s.nearbyAlerts
            p[EXPIRY] = s.expiryReminders
            p[MEMBERSHIP] = s.membershipReminders
            p[RADIUS] = s.defaultRadiusMeters.coerceIn(Place.MIN_RADIUS_METERS, Place.MAX_RADIUS_METERS)
            p[LEAD_DAYS] = s.expiryLeadDays.coerceIn(0, 14)
            p[AUTO_ARCHIVE] = s.autoArchiveExpired
        }
    }

    // --- geofence registration bookkeeping: request id -> signature of what was registered ---

    suspend fun registeredGeofences(): Map<String, String> =
        dataStore.data.first()[GEOFENCES]?.let {
            runCatching { Json.decodeFromString(mapSerializer, it) }.getOrNull()
        } ?: emptyMap()

    suspend fun setRegisteredGeofences(value: Map<String, String>) {
        dataStore.edit { it[GEOFENCES] = Json.encodeToString(mapSerializer, value) }
    }

    // --- sync cursors: table -> last seen server_updated_at ---

    suspend fun syncCursor(table: String): String? = dataStore.data.first()[cursorKey(table)]

    suspend fun setSyncCursor(table: String, cursor: String) {
        dataStore.edit { it[cursorKey(table)] = cursor }
    }

    val lastSyncAt: Flow<Long?> = dataStore.data.map { it[LAST_SYNC]?.toLongOrNull() }.distinctUntilChanged()

    suspend fun setLastSyncAt(millis: Long) {
        dataStore.edit { it[LAST_SYNC] = millis.toString() }
    }

    /** Forget everything tied to an account so the next sign-in starts clean. */
    suspend fun clearSyncState() {
        dataStore.edit { p ->
            p.asMap().keys.filter { it.name.startsWith(CURSOR_PREFIX) }.forEach { p.remove(it) }
            p.remove(LAST_SYNC)
        }
    }

    suspend fun lastAccountId(): String? = dataStore.data.first()[LAST_ACCOUNT]

    suspend fun setLastAccountId(id: String) {
        dataStore.edit { it[LAST_ACCOUNT] = id }
    }

    suspend fun fcmToken(): String? = dataStore.data.first()[FCM_TOKEN]

    suspend fun setFcmToken(token: String) {
        dataStore.edit { it[FCM_TOKEN] = token }
    }

    private fun cursorKey(table: String) = stringPreferencesKey(CURSOR_PREFIX + table)

    private companion object {
        val NEARBY = booleanPreferencesKey("nearby_alerts")
        val EXPIRY = booleanPreferencesKey("expiry_reminders")
        val MEMBERSHIP = booleanPreferencesKey("membership_reminders")
        val RADIUS = intPreferencesKey("default_radius_m")
        val LEAD_DAYS = intPreferencesKey("expiry_lead_days")
        val AUTO_ARCHIVE = booleanPreferencesKey("auto_archive_expired")
        val GEOFENCES = stringPreferencesKey("registered_geofences")
        val LAST_SYNC = stringPreferencesKey("last_sync_at")
        val LAST_ACCOUNT = stringPreferencesKey("last_account_id")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        const val CURSOR_PREFIX = "sync_cursor_"
        val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
