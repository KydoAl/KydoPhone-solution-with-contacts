package org.aust.dialer

import android.app.Application
import android.content.Context
import org.aust.dialer.core.Prefs
import org.aust.dialer.sms.SmsNotifications
import org.aust.dialer.telecom.CallManager
import org.aust.dialer.telecom.CallNotifications

class DialerApp : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        CallNotifications.createChannels(this)
        SmsNotifications.createChannel(this)
        CallManager.init(this)
    }
}

val Context.dialerApp: DialerApp get() = applicationContext as DialerApp
