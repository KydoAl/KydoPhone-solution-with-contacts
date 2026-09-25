package org.aust.dialer.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import org.aust.dialer.InCallActivity
import org.aust.dialer.MainActivity
import org.aust.dialer.R
import org.aust.dialer.core.Format
import org.aust.dialer.core.LocaleHelper
import org.aust.dialer.core.PhoneUtils

/**
 * All call notifications. Uses the framework Notification.Builder (minSdk 30 => channels always exist).
 *
 * Every PendingIntent is FLAG_IMMUTABLE with a request code that is unique per number+action, so
 * "Call back" / "Message" never collide with each other and never hit the Android 12+ mutability crash.
 * Actions that need UI (call back, message, answer) are activity PendingIntents, launched directly by the
 * system, which avoids the Android 12+ "notification trampoline" restriction.
 */
object CallNotifications {
    const val CH_INCOMING = "incoming_calls"
    const val CH_ONGOING = "ongoing_call"
    const val CH_MISSED = "missed_calls"

    private const val ID_CALL = 1
    private const val ID_MISSED = 2

    const val ACTION_OPEN_RECENTS = "org.aust.dialer.action.OPEN_RECENTS"
    const val ACTION_CALL_BACK = "org.aust.dialer.action.CALL_BACK"
    const val ACTION_DECLINE = "org.aust.dialer.action.DECLINE"
    const val ACTION_HANGUP = "org.aust.dialer.action.HANGUP"
    const val EXTRA_NUMBER = "org.aust.dialer.extra.NUMBER"
    const val EXTRA_CALL_ID = "org.aust.dialer.extra.CALL_ID"
    const val EXTRA_ANSWER = "org.aust.dialer.extra.ANSWER"

    private const val PI_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    fun areEnabled(context: Context): Boolean = try {
        manager(context)?.areNotificationsEnabled() ?: false
    } catch (e: Exception) {
        false
    }

    fun createChannels(context: Context) {
        val nm = manager(context) ?: return
        val c = LocaleHelper.wrap(context)
        val incoming = NotificationChannel(
            CH_INCOMING,
            c.getString(R.string.notif_channel_incoming),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // The system (Telecom) plays the ringtone; the notification itself stays silent.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val ongoing = NotificationChannel(
            CH_ONGOING,
            c.getString(R.string.notif_channel_ongoing),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
        val missed = NotificationChannel(
            CH_MISSED,
            c.getString(R.string.notif_channel_missed),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        nm.createNotificationChannels(listOf(incoming, ongoing, missed))
    }

    private fun title(c: Context, info: CallInfo): String =
        info.name ?: if (PhoneUtils.isUnknown(info.number)) c.getString(R.string.recents_private) else Format.number(info.number)

    private fun inCallIntent(context: Context, requestCode: Int, answer: Boolean): PendingIntent {
        val intent = Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (answer) intent.putExtra(EXTRA_ANSWER, true)
        return PendingIntent.getActivity(context, requestCode, intent, PI_FLAGS)
    }

    private fun action(context: Context, textRes: Int, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification), context.getString(textRes), pi).build()

    fun showIncoming(context: Context, info: CallInfo, firstPost: Boolean) {
        val nm = manager(context) ?: return
        val c = LocaleHelper.wrap(context)
        val open = inCallIntent(context, 10, answer = false)
        val answer = inCallIntent(context, 11, answer = true)
        val decline = PendingIntent.getBroadcast(
            context,
            12,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_DECLINE).putExtra(EXTRA_CALL_ID, info.id),
            PI_FLAGS,
        )
        val builder = Notification.Builder(context, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(c, info))
            .setContentText(c.getString(R.string.notif_incoming_text))
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(action(c, R.string.notif_action_decline, decline))
            .addAction(action(c, R.string.notif_action_answer, answer))
        // Only the first post may launch the full-screen UI; later updates (e.g. name resolved) must not re-launch it.
        if (firstPost) builder.setFullScreenIntent(open, true)
        try {
            nm.notify(ID_CALL, builder.build())
        } catch (e: RuntimeException) {
            // Never crash the call service because of a notification problem.
        }
    }

    fun showOngoing(context: Context, info: CallInfo) {
        val nm = manager(context) ?: return
        val c = LocaleHelper.wrap(context)
        val open = inCallIntent(context, 10, answer = false)
        val hangup = PendingIntent.getBroadcast(
            context,
            13,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_HANGUP).putExtra(EXTRA_CALL_ID, info.id),
            PI_FLAGS,
        )
        val active = info.status == CallStatus.ACTIVE && info.connectTimeMillis > 0
        val builder = Notification.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(c, info))
            .setContentText(c.getString(info.statusRes()))
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(action(c, R.string.notif_action_hangup, hangup))
        if (active) {
            builder.setUsesChronometer(true).setWhen(info.connectTimeMillis).setShowWhen(true)
        }
        try {
            nm.notify(ID_CALL, builder.build())
        } catch (e: RuntimeException) {
            // ignore
        }
    }

    fun cancelCall(context: Context) {
        try {
            manager(context)?.cancel(ID_CALL)
        } catch (e: RuntimeException) {
            // ignore
        }
    }

    /** Missed-call notification with "Call back" and "Message" actions. */
    fun showMissed(context: Context, number: String, name: String?, whenMillis: Long) {
        val nm = manager(context) ?: return
        val c = LocaleHelper.wrap(context)
        val known = !PhoneUtils.isUnknown(number)
        val display = name ?: if (known) Format.number(number) else c.getString(R.string.recents_private)
        val tag = if (known) PhoneUtils.matchKey(number) else "unknown"
        val req = tag.hashCode() * 4

        val open = PendingIntent.getActivity(
            context,
            req + 1,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_RECENTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PI_FLAGS,
        )
        val builder = Notification.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.notif_missed_title))
            .setContentText(display)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setWhen(whenMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (known) {
            val callBack = PendingIntent.getActivity(
                context,
                req + 2,
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_CALL_BACK)
                    .putExtra(EXTRA_NUMBER, number)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PI_FLAGS,
            )
            val message = PendingIntent.getActivity(
                context,
                req + 3,
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PI_FLAGS,
            )
            builder.addAction(action(c, R.string.notif_action_call_back, callBack))
            builder.addAction(action(c, R.string.notif_action_message, message))
        }
        try {
            nm.notify(tag, ID_MISSED, builder.build())
        } catch (e: RuntimeException) {
            // ignore
        }
    }

    fun cancelMissed(context: Context) {
        val nm = manager(context) ?: return
        try {
            for (sbn in nm.activeNotifications) {
                if (sbn.id == ID_MISSED) nm.cancel(sbn.tag, sbn.id)
            }
        } catch (e: RuntimeException) {
            // ignore
        }
    }
}
