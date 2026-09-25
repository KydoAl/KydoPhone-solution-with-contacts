package org.aust.dialer.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Bound by Telecom for every call while KydoPhone holds the default-dialer role. It keeps working when
 * no Activity is open, on the lock screen and after a process restart (Telecom re-binds it).
 */
class DialerInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(this, call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioState(audioState)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        CallManager.launchUi(this)
    }

    override fun onDestroy() {
        CallManager.onServiceDestroyed(this)
        super.onDestroy()
    }
}
