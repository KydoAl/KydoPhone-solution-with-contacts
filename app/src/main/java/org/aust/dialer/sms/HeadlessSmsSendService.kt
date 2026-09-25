package org.aust.dialer.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executors

/**
 * Required for the default-SMS role: handles ACTION_RESPOND_VIA_MESSAGE (e.g. "quick response" declining
 * a call with a text) without opening any UI.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = intent?.data
        val body = intent?.getStringExtra(android.content.Intent.EXTRA_TEXT)
        val address = data?.schemeSpecificPart
        if (!address.isNullOrBlank() && !body.isNullOrBlank()) {
            val appContext = applicationContext
            EXECUTOR.execute {
                try {
                    SmsRepository(appContext).send(address, body, subId = null)
                } finally {
                    stopSelf(startId)
                }
            }
            return START_NOT_STICKY
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
