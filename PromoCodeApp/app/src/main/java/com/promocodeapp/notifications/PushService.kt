package com.promocodeapp.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.promocodeapp.data.repository.LocalChangeListener
import com.promocodeapp.data.settings.SettingsStore
import com.promocodeapp.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives server-sent pushes (optional; active only when the build includes a
 * google-services.json). Nearby and expiry alerts never pass through here:
 * they are produced on the device so location never leaves it.
 */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var changes: LocalChangeListener
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onMessageReceived(message: RemoteMessage) {
        // Messages with a `notification` block are displayed by the system while
        // the app is in the background; handle exactly one source to avoid doubles.
        val title = message.data["title"] ?: message.notification?.title ?: return
        val body = message.data["body"] ?: message.notification?.body.orEmpty()
        val link = message.data["coupon_id"]?.let(DeepLinks::coupon)
            ?: message.data["membership_id"]?.let(DeepLinks::membership)
        notifier.showGeneral(title, body, link)
    }

    override fun onNewToken(token: String) {
        scope.launch {
            settings.setFcmToken(token)
            // The next sync uploads the token for the signed-in account.
            changes.onLocalChange()
        }
    }
}
