package org.aust.dialer.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.concurrent.Executors

/**
 * Delivers incoming texts while KydoPhone holds the default-SMS role. Telecom/Telephony only
 * sends this to the default app, so the provider is not populated automatically: we insert the row
 * ourselves, then notify. Runs off the main thread via goAsync(), since receivers get a short time budget.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            null
        }
        if (messages.isNullOrEmpty()) return

        val address = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val date = messages[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        val pending = goAsync()
        val appContext = context.applicationContext
        EXECUTOR.execute {
            try {
                val prefs = org.aust.dialer.core.Prefs(appContext)
                val repo = SmsRepository(appContext)
                val id = repo.insertInbox(address, body, date)
                if (id >= 0 && !prefs.isSmsSenderBlocked(address)) {
                    SmsNotifications.showIncoming(appContext, address, body, date)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
