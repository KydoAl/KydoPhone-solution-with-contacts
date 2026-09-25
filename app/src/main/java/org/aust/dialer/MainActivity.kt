package org.aust.dialer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.aust.dialer.core.LocaleHelper
import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.core.Prefs
import org.aust.dialer.sms.SmsNotifications
import org.aust.dialer.telecom.CallNotifications
import org.aust.dialer.ui.AppRoot
import org.aust.dialer.ui.TAB_DIALPAD
import org.aust.dialer.ui.TAB_MESSAGES
import org.aust.dialer.ui.TAB_RECENTS
import org.aust.dialer.ui.theme.KydoPhoneTheme

/** One-shot instruction derived from an incoming Intent (tel: link, notification action, ...). */
data class IntentRequest(
    val number: String? = null,
    val tab: Int? = null,
    val callNumber: String? = null,
    val messageAddress: String? = null,
    val nonce: Long = System.nanoTime(),
)

class MainActivity : ComponentActivity() {
    private val vm: DialerViewModel by viewModels()
    private val smsVm: SmsViewModel by viewModels()
    private var request by mutableStateOf<IntentRequest?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = dialerApp.prefs
        if (savedInstanceState == null) request = parse(intent)

        setContent {
            val themeMode by prefs.themeMode.collectAsState()
            val dynamic by prefs.dynamicColor.collectAsState()
            val dark = when (themeMode) {
                Prefs.THEME_LIGHT -> false
                Prefs.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            KydoPhoneTheme(themeMode, dynamic) {
                AppRoot(vm, smsVm, prefs, request, onConsumed = { request = null })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
        smsVm.refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = parse(intent)
    }

    private fun parse(i: Intent?): IntentRequest? {
        i ?: return null
        return when (i.action) {
            Intent.ACTION_DIAL -> {
                val ssp = i.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
                IntentRequest(number = ssp?.let { PhoneUtils.sanitizeDialable(it) }, tab = TAB_DIALPAD)
            }
            Intent.ACTION_VIEW -> {
                val scheme = i.data?.scheme
                if (scheme == "sms" || scheme == "smsto") {
                    val ssp = i.data?.schemeSpecificPart
                    if (ssp != null) SmsNotifications.cancel(this, ssp)
                    IntentRequest(tab = TAB_MESSAGES, messageAddress = ssp)
                } else {
                    val ssp = i.data?.takeIf { scheme == "tel" }?.schemeSpecificPart
                    IntentRequest(number = ssp?.let { PhoneUtils.sanitizeDialable(it) }, tab = TAB_DIALPAD)
                }
            }
            Intent.ACTION_CALL_BUTTON -> IntentRequest(tab = TAB_DIALPAD)
            CallNotifications.ACTION_OPEN_RECENTS -> {
                CallNotifications.cancelMissed(this)
                IntentRequest(tab = TAB_RECENTS)
            }
            CallNotifications.ACTION_CALL_BACK ->
                IntentRequest(tab = TAB_RECENTS, callNumber = i.getStringExtra(CallNotifications.EXTRA_NUMBER))
            Intent.ACTION_SENDTO -> {
                val ssp = i.data?.takeIf { it.scheme == "sms" || it.scheme == "smsto" }?.schemeSpecificPart
                if (ssp != null) {
                    SmsNotifications.cancel(this, ssp)
                    IntentRequest(tab = TAB_MESSAGES, messageAddress = ssp)
                } else {
                    null
                }
            }
            Intent.ACTION_SEND -> {
                // "Share to KydoPhone" for plain text: opens Compose with no recipient chosen yet.
                if (i.type == "text/plain") IntentRequest(tab = TAB_MESSAGES) else null
            }
            else -> null
        }
    }
}
