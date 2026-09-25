package org.aust.dialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the notification actions that need no UI: decline an incoming call, hang up an ongoing one. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(CallNotifications.EXTRA_CALL_ID, -1)
        when (intent.action) {
            CallNotifications.ACTION_DECLINE -> CallManager.reject(id)
            CallNotifications.ACTION_HANGUP -> CallManager.hangup(id)
        }
    }
}
