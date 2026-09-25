package org.aust.dialer.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the per-part "sent" result from SmsManager and reconciles the provider row once every part
 * of a (possibly multipart) message has reported in. Not exported: only our own PendingIntents reach it.
 */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ROW_ID, -1L)
        if (id < 0) return
        val success = resultCode == Activity.RESULT_OK
        recordPartResult(context.applicationContext, id, success, resultCode)
    }

    companion object {
        private const val EXTRA_ROW_ID = "row_id"
        private const val ACTION_SENT = "org.aust.dialer.action.SMS_SENT"

        fun trackSend(id: Long, partCount: Int) = trackNewSend(id, partCount)

        fun sentPendingIntent(context: Context, id: Long, partIndex: Int): PendingIntent {
            val intent = Intent(context, SmsStatusReceiver::class.java).setAction(ACTION_SENT).putExtra(EXTRA_ROW_ID, id)
            // Unique request code per row+part so parts of different messages never collide.
            val requestCode = (id.hashCode() * 31) + partIndex
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
