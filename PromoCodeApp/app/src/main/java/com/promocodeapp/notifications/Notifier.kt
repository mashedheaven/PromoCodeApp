package com.promocodeapp.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.promocodeapp.MainActivity
import com.promocodeapp.R
import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.Expiry
import com.promocodeapp.core.model.Membership
import com.promocodeapp.core.model.Place
import com.promocodeapp.location.Permissions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** In-app destinations that notifications can open. */
object DeepLinks {
    const val SCHEME = "promocode"
    fun coupon(id: String) = "$SCHEME://coupon/$id"
    fun place(id: String) = "$SCHEME://place/$id"
    fun membership(id: String) = "$SCHEME://membership/$id"
    const val EXPIRING = "$SCHEME://coupons?filter=EXPIRING_SOON"
}

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Idempotent; call at every app start so channels exist before any alert. */
    fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_NEARBY, "Nearby deals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when you arrive somewhere you have a coupon or membership"
            },
            NotificationChannel(CHANNEL_EXPIRING, "Expiring soon", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders before your coupons expire"
            },
            NotificationChannel(CHANNEL_MEMBERSHIPS, "Membership reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders before a membership renews"
            },
            NotificationChannel(CHANNEL_GENERAL, "Updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Other messages from PromoCode"
            },
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    fun showNearby(place: Place, coupons: List<Coupon>, memberships: List<Membership>): Boolean {
        val lines = coupons.map { it.headline } +
            memberships.map { "${it.organization} membership" }
        val text = when {
            coupons.size == 1 && memberships.isEmpty() -> coupons[0].headline
            coupons.isEmpty() && memberships.size == 1 -> "Your ${memberships[0].organization} membership is valid here"
            coupons.isEmpty() -> "${memberships.size} memberships are valid here"
            else -> "You have ${coupons.size} coupons to use here"
        }
        // One coupon opens straight to its code; several open the list for this place.
        val link = if (coupons.size == 1) DeepLinks.coupon(coupons[0].id)
        else if (coupons.isEmpty() && memberships.size == 1) DeepLinks.membership(memberships[0].id)
        else DeepLinks.place(place.id)

        val builder = base(CHANNEL_NEARBY, "You're near ${place.name}", text, link)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        if (lines.size > 1) {
            builder.setStyle(NotificationCompat.InboxStyle().also { style -> lines.take(6).forEach(style::addLine) })
        }
        return post(TAG_NEARBY + place.id, builder)
    }

    fun cancelNearby(placeId: String) = manager.cancel(TAG_NEARBY + placeId, ID)

    fun showExpiring(coupons: List<Coupon>, today: LocalDate): Boolean {
        if (coupons.isEmpty()) return false
        fun whenText(c: Coupon) = when (val e = c.expiry(today)) {
            Expiry.Today -> "today"
            is Expiry.InDays -> if (e.days == 1L) "tomorrow" else "in ${e.days} days"
            else -> "soon"
        }
        val builder = if (coupons.size == 1) {
            val c = coupons[0]
            base(CHANNEL_EXPIRING, "${c.merchant} coupon expires ${whenText(c)}", c.headline, DeepLinks.coupon(c.id))
        } else {
            base(CHANNEL_EXPIRING, "${coupons.size} coupons expire soon", coupons.joinToString { it.merchant }, DeepLinks.EXPIRING)
                .setStyle(NotificationCompat.InboxStyle().also { s -> coupons.take(6).forEach { s.addLine("${it.merchant} · ${whenText(it)}") } })
        }
        return post(TAG_EXPIRING, builder)
    }

    fun showRenewal(membership: Membership, renewsOn: LocalDate, daysLeft: Long): Boolean {
        val whenText = when (daysLeft) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in $daysLeft days"
        }
        val cost = membership.fee?.takeIf { it > 0 }
            ?.let { " · ${com.promocodeapp.core.model.Money.format(it, membership.currency)}" }.orEmpty()
        val builder = base(
            CHANNEL_MEMBERSHIPS, "${membership.organization} renews $whenText",
            "Renewal date: $renewsOn$cost", DeepLinks.membership(membership.id),
        )
        return post(TAG_RENEWAL + membership.id, builder)
    }

    fun showGeneral(title: String, body: String, link: String?): Boolean =
        post(TAG_GENERAL + (title + body).hashCode(), base(CHANNEL_GENERAL, title, body, link))

    private fun base(channel: String, title: String, text: String, link: String?): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (link != null) {
                action = Intent.ACTION_VIEW
                data = link.toUri()
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Distinct request codes keep each notification's destination separate.
        val pending = PendingIntent.getActivity(
            context, (link ?: channel).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
    }

    @SuppressLint("MissingPermission")
    private fun post(tag: String, builder: NotificationCompat.Builder): Boolean {
        if (!Permissions.canNotify(context)) return false
        manager.notify(tag, ID, builder.build())
        return true
    }

    companion object {
        const val CHANNEL_NEARBY = "nearby_deals"
        const val CHANNEL_EXPIRING = "expiring_soon"
        const val CHANNEL_MEMBERSHIPS = "membership_reminders"
        const val CHANNEL_GENERAL = "general"
        private const val ID = 1
        private const val TAG_NEARBY = "nearby:"
        private const val TAG_EXPIRING = "expiring"
        private const val TAG_RENEWAL = "renewal:"
        private const val TAG_GENERAL = "general:"
    }
}
