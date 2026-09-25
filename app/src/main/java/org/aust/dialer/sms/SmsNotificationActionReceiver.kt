package org.aust.dialer.sms

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

/** Handles the notification actions that need no UI: mark a thread read, and inline reply. */
class SmsNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            SmsNotifications.ACTION_MARK_READ -> {
                val threadId = intent.getLongExtra(SmsNotifications.EXTRA_THREAD_ID, -1L)
                if (threadId >= 0) {
                    val address = intent.getStringExtra(SmsNotifications.EXTRA_ADDRESS)
                    EXECUTOR.execute {
                        SmsRepository(appContext).markThreadRead(threadId)
                        if (address != null) SmsNotifications.cancel(appContext, address)
                    }
                }
            }
            SmsNotifications.ACTION_REPLY -> {
                val address = intent.getStringExtra(SmsNotifications.EXTRA_ADDRESS) ?: return
                val threadId = intent.getLongExtra(SmsNotifications.EXTRA_THREAD_ID, -1L)
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(SmsNotifications.KEY_REPLY_TEXT)?.toString()
                if (!text.isNullOrBlank()) {
                    EXECUTOR.execute {
                        SmsRepository(appContext).send(address, text, subId = null)
                        if (threadId >= 0) SmsRepository(appContext).markThreadRead(threadId)
                        SmsNotifications.cancel(appContext, address)
                    }
                }
            }
        }
    }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
