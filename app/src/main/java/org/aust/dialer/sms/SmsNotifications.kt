package org.aust.dialer.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.app.Person
import android.net.Uri
import org.aust.dialer.MainActivity
import org.aust.dialer.R
import org.aust.dialer.core.LocaleHelper
import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.data.ContactsRepository

/**
 * Notifications for incoming texts. Uses the framework Notification.Builder (minSdk 30 => channels
 * always exist) with an inline-reply RemoteInput so a text can be answered without opening the app.
 */
object SmsNotifications {
    const val CH_MESSAGES = "messages"

    private fun conversationChannelId(threadId: Long): String = "sms_thread_$threadId"

    const val ACTION_REPLY = "org.aust.dialer.action.SMS_REPLY"
    const val ACTION_MARK_READ = "org.aust.dialer.action.SMS_MARK_READ"
    const val EXTRA_ADDRESS = "org.aust.dialer.extra.ADDRESS"
    const val EXTRA_THREAD_ID = "org.aust.dialer.extra.THREAD_ID"
    const val KEY_REPLY_TEXT = "reply_text"

    private const val PI_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    fun areEnabled(context: Context): Boolean = try {
        manager(context)?.areNotificationsEnabled() ?: false
    } catch (e: Exception) {
        false
    }

    fun createChannel(context: Context) {
        val nm = manager(context) ?: return
        val c = LocaleHelper.wrap(context)
        val channel = NotificationChannel(CH_MESSAGES, c.getString(R.string.notif_channel_messages), NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)
    }

    private fun notificationId(address: String): Int = ("sms:" + PhoneUtils.senderKey(address)).hashCode()

    fun ensureConversationChannel(context: Context, threadId: Long, displayName: String) {
        if (threadId <= 0) return
        val nm = manager(context) ?: return
        val c = LocaleHelper.wrap(context)
        val channel = NotificationChannel(
            conversationChannelId(threadId),
            displayName.ifBlank { c.getString(R.string.notif_channel_messages) },
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = c.getString(R.string.sms_notification_channel_description)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun openConversationNotificationSettings(context: Context, threadId: Long, displayName: String) {
        if (threadId <= 0) return
        ensureConversationChannel(context, threadId, displayName)
        val intent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, conversationChannelId(threadId))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (_: Exception) { }
    }

    fun showIncoming(context: Context, address: String, body: String, whenMillis: Long) {
        val nm = manager(context) ?: return
        val c = LocaleHelper.wrap(context)
        val name = try {
            val key = PhoneUtils.matchKey(address)
            if (key.isBlank()) null else ContactsRepository(c).loadAll().firstOrNull { c2 -> c2.numbers.any { PhoneUtils.matchKey(it.number) == key } }?.name
        } catch (e: Exception) {
            null
        }
        val display = name ?: address
        val id = notificationId(address)

        val threadId = SmsRepository(context).threadIdFor(address)
        ensureConversationChannel(context, threadId, display)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.fromParts("smsto", address, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PI_FLAGS,
        )
        val markRead = PendingIntent.getBroadcast(
            context,
            id + 1,
            Intent(context, SmsNotificationActionReceiver::class.java)
                .setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_THREAD_ID, threadId),
            PI_FLAGS,
        )
        val replyInput = RemoteInput.Builder(KEY_REPLY_TEXT).setLabel(c.getString(R.string.sms_reply_hint)).build()
        val replyIntent = PendingIntent.getBroadcast(
            context,
            id + 2,
            Intent(context, SmsNotificationActionReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_THREAD_ID, threadId),
            PI_FLAGS,
        )
        val replyAction = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            c.getString(R.string.action_reply),
            replyIntent,
        ).addRemoteInput(replyInput).build()
        val markReadAction = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            c.getString(R.string.sms_mark_read),
            markRead,
        ).build()

        val person = Person.Builder().setName(display).build()
        val style = Notification.MessagingStyle(person).addMessage(body, whenMillis, person)

        val builder = Notification.Builder(context, conversationChannelId(threadId))
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setWhen(whenMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(markReadAction)
            .addAction(replyAction)
        try {
            nm.notify(id, builder.build())
        } catch (e: RuntimeException) {
            // Never crash the receiver because of a notification problem.
        }
    }

    fun cancel(context: Context, address: String) {
        try {
            manager(context)?.cancel(notificationId(address))
        } catch (e: RuntimeException) {
            // ignore
        }
    }
}
