package org.aust.dialer.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required for the default-SMS role (a WAP-push / MMS receiver must exist), but KydoPhone does not
 * implement MMS: picture messages and group texts are neither shown nor sent. This receiver
 * acknowledges the broadcast and otherwise does nothing.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally no-op: MMS is out of scope.
    }
}
