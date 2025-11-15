package com.promocodeapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.promocodeapp.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PromoCodeMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationManager: NotificationManager

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Handle data message
        remoteMessage.data.let { data ->
            val title = data["title"] ?: "PromoCode Alert"
            val body = data["body"] ?: ""
            val couponId = data["coupon_id"]
            val merchantName = data["merchant_name"]
            val notificationType = data["type"] ?: "proximity" // proximity, expiration, membership

            sendNotification(
                title = title,
                body = body,
                couponId = couponId,
                merchantName = merchantName,
                notificationType = notificationType
            )
        }

        // Handle notification message
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "PromoCode Alert"
            val body = notification.body ?: ""
            sendNotification(title, body)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to server for this device
        sendTokenToServer(token)
    }

    private fun sendNotification(
        title: String,
        body: String,
        couponId: String? = null,
        merchantName: String? = null,
        notificationType: String = "proximity"
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("coupon_id", couponId)
            putExtra("notification_type", notificationType)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = when (notificationType) {
            "proximity" -> "proximity_alerts"
            "expiration" -> "expiration_warnings"
            "membership" -> "membership_reminders"
            else -> "general_notifications"
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        createNotificationChannels()

        val notificationId = (System.currentTimeMillis() % Integer.MAX_VALUE).toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Proximity Alerts Channel
            val proximityChannel = NotificationChannel(
                "proximity_alerts",
                "Proximity Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when you're near a store with available coupons"
                enableVibration(true)
            }

            // Expiration Warnings Channel
            val expirationChannel = NotificationChannel(
                "expiration_warnings",
                "Expiration Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders about coupons expiring soon"
            }

            // Membership Reminders Channel
            val membershipChannel = NotificationChannel(
                "membership_reminders",
                "Membership Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders about upcoming membership renewals"
            }

            // General Notifications Channel
            val generalChannel = NotificationChannel(
                "general_notifications",
                "General Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General app notifications"
            }

            notificationManager.apply {
                createNotificationChannel(proximityChannel)
                createNotificationChannel(expirationChannel)
                createNotificationChannel(membershipChannel)
                createNotificationChannel(generalChannel)
            }
        }
    }

    private fun sendTokenToServer(token: String) {
        // This would typically send the token to your backend (Supabase)
        // For now, we'll just save it locally
        val sharedPref = getSharedPreferences("promo_code_app", Context.MODE_PRIVATE)
        sharedPref.edit().putString("fcm_token", token).apply()
    }

    companion object {
        fun subscribeToTopics() {
            FirebaseMessaging.getInstance().apply {
                subscribeToTopic("proximity_alerts")
                subscribeToTopic("expiration_warnings")
                subscribeToTopic("membership_reminders")
                subscribeToTopic("general_notifications")
            }
        }

        fun unsubscribeFromTopics() {
            FirebaseMessaging.getInstance().apply {
                unsubscribeFromTopic("proximity_alerts")
                unsubscribeFromTopic("expiration_warnings")
                unsubscribeFromTopic("membership_reminders")
                unsubscribeFromTopic("general_notifications")
            }
        }
    }
}
