package org.aust.dialer

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.aust.dialer.core.LocaleHelper
import org.aust.dialer.telecom.CallManager
import org.aust.dialer.telecom.CallNotifications
import org.aust.dialer.ui.InCallScreen
import org.aust.dialer.ui.theme.KydoPhoneTheme

/**
 * The in-call screen. It is shown over the lock screen, started by DialerInCallService (outgoing calls)
 * or by the incoming-call notification (full-screen intent).
 */
class InCallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure window flags turn screen on and show over keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Keep screen awake during call setup
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        handleIntent(intent)

        val prefs = dialerApp.prefs
        setContent {
            val themeMode by prefs.themeMode.collectAsState()
            val dynamic by prefs.dynamicColor.collectAsState()

            KydoPhoneTheme(
                themeMode,
                dynamicColor = dynamic
            ) {
                InCallScreen(
                    onFinish = { finish() },
                    onProximity = { isNear ->
                        setProximity(isNear)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(CallNotifications.EXTRA_ANSWER, false) == true) {
            i.putExtra(CallNotifications.EXTRA_ANSWER, false)
            i.removeExtra(CallNotifications.EXTRA_ANSWER)
            CallManager.answerRinging()
        }
    }

    private fun setProximity(on: Boolean) {
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            if (on) {
                if (proximity == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    proximity = pm.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "KydoPhone:proximity"
                    ).apply {
                        acquire(2 * 60 * 60 * 1000L) // 2 hours max timeout safety
                    }
                }
            } else {
                proximity?.let { if (it.isHeld) it.release() }
                proximity = null
            }
        } catch (e: Exception) {
            proximity = null
        }
    }

    override fun onStop() {
        // Release wake lock when activity goes to background to avoid hanging proximity sensors
        setProximity(false)
        super.onStop()
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }
}