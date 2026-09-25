package org.aust.dialer.telecom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aust.dialer.InCallActivity
import java.util.concurrent.Executors

/**
 * Bridges Telecom's live [Call] objects to immutable [CallInfo] snapshots for the UI and notifications.
 * All Telecom callbacks arrive on the main thread, so this object is main-thread only (except the
 * contact lookup, which runs on a single background thread and posts its result back).
 */
@Suppress("DEPRECATION")
object CallManager {

    private class Tracked(val call: Call, val id: Int) {
        var number: String = ""
        var name: String? = null
        var photo: String? = null
        var incoming = false
        var everActive = false
        var endReason: EndReason? = null
        var removed = false
        var callback: Call.Callback? = null
        val addedAt = System.currentTimeMillis()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val tracked = ArrayList<Tracked>()
    private val lingering = ArrayList<CallInfo>()
    private var nextId = 1
    private var service: InCallService? = null
    private lateinit var appContext: Context

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow(AudioInfo())
    val audio: StateFlow<AudioInfo> = _audio.asStateFlow()

    private var postedKey: String? = null
    private var incomingPostedId = -1

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ---- Service callbacks -------------------------------------------------------------------------

    fun onCallAdded(svc: InCallService, call: Call) {
        service = svc
        val t = Tracked(call, nextId++)
        t.incoming = call.state == Call.STATE_RINGING
        t.number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = onCallChanged(t)
            override fun onDetailsChanged(call: Call, details: Call.Details) = onCallChanged(t)
            override fun onParentChanged(call: Call, parent: Call?) = publish()
            override fun onChildrenChanged(call: Call, children: List<Call>) = publish()
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) = publish()
        }
        t.callback = cb
        call.registerCallback(cb)
        tracked.add(t)
        svc.callAudioState?.let { onAudioState(it) }
        lookupAsync(t)
        publish()

        if (t.incoming) {
            // The full-screen-intent notification shows the UI. If notifications are blocked, start it directly.
            if (!CallNotifications.areEnabled(appContext)) launchUi(svc)
        } else {
            launchUi(svc)
        }
    }

    fun onCallRemoved(call: Call) {
        val t = tracked.firstOrNull { it.call === call } ?: return
        t.removed = true
        t.callback?.let {
            try {
                call.unregisterCallback(it)
            } catch (e: RuntimeException) {
                // already gone
            }
        }
        val reason = t.endReason ?: readEndReason(call)
        val info = toInfo(t).copy(status = CallStatus.DISCONNECTED, endReason = reason ?: EndReason.OTHER)
        val wasChild = try {
            call.parent != null
        } catch (e: RuntimeException) {
            false
        }
        tracked.remove(t)

        if (t.incoming && !t.everActive && reason == EndReason.MISSED) {
            CallNotifications.showMissed(appContext, t.number, t.name, t.addedAt)
        }
        if (!wasChild && (!t.incoming || t.everActive)) {
            lingering.add(info)
            handler.postDelayed({
                lingering.remove(info)
                publish()
            }, LINGER_MS)
        }
        publish()
    }

    fun onAudioState(state: CallAudioState?) {
        if (state == null) return
        _audio.value = AudioInfo(state.isMuted, state.route, state.supportedRouteMask)
    }

    fun onServiceDestroyed(svc: InCallService) {
        if (service !== svc) return
        service = null
        for (t in tracked) {
            t.callback?.let {
                try {
                    t.call.unregisterCallback(it)
                } catch (e: RuntimeException) {
                    // ignore
                }
            }
        }
        tracked.clear()
        lingering.clear()
        publish()
    }

    fun launchUi(context: Context) {
        try {
            context.startActivity(Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            // The notification remains available as the entry point.
        }
    }

    // ---- Actions (called from the UI / notification receiver) ------------------------------------

    private fun find(id: Int): Call? = tracked.firstOrNull { it.id == id }?.call

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            // The call may have ended between the tap and the request; ignore.
        }
    }

    fun answer(id: Int) = safely { find(id)?.answer(VideoProfile.STATE_AUDIO_ONLY) }

    fun answerRinging() = safely {
        tracked.firstOrNull { it.call.state == Call.STATE_RINGING }?.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Declines a ringing call; for any other state it hangs up. */
    fun reject(id: Int) = safely {
        val c = find(id) ?: return@safely
        if (c.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }

    fun hangup(id: Int) = safely { find(id)?.disconnect() }

    fun hold(id: Int) = safely { find(id)?.hold() }

    fun unhold(id: Int) = safely { find(id)?.unhold() }

    fun merge(id: Int) = safely {
        val c = find(id) ?: return@safely
        val caps = c.details.callCapabilities
        if ((caps and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
            c.mergeConference()
        } else {
            c.conferenceableCalls.firstOrNull()?.let { c.conference(it) }
        }
    }

    fun sendDtmf(id: Int, digit: Char) = safely {
        val c = find(id) ?: return@safely
        c.playDtmfTone(digit)
        handler.postDelayed({ safely { c.stopDtmfTone() } }, DTMF_MS)
    }

    fun selectAccount(id: Int, handle: PhoneAccountHandle) = safely { find(id)?.phoneAccountSelected(handle, false) }

    fun setMuted(muted: Boolean) = safely { service?.setMuted(muted) }

    fun setAudioRoute(route: Int) = safely { service?.setAudioRoute(route) }

    // ---- Internals ---------------------------------------------------------------------------------

    private fun onCallChanged(t: Tracked) {
        if (t.removed) return
        val state = t.call.state
        if (state == Call.STATE_ACTIVE) t.everActive = true
        if (state == Call.STATE_DISCONNECTED) t.endReason = readEndReason(t.call)
        // Number can appear late for some outgoing calls.
        if (t.number.isEmpty()) {
            val n = t.call.details?.handle?.schemeSpecificPart.orEmpty()
            if (n.isNotEmpty()) {
                t.number = n
                lookupAsync(t)
            }
        }
        publish()
    }

    private fun readEndReason(call: Call): EndReason? {
        val cause = try {
            call.details?.disconnectCause
        } catch (e: RuntimeException) {
            null
        } ?: return null
        return when (cause.code) {
            DisconnectCause.LOCAL -> EndReason.LOCAL
            DisconnectCause.REMOTE -> EndReason.REMOTE
            DisconnectCause.MISSED -> EndReason.MISSED
            DisconnectCause.REJECTED -> EndReason.REJECTED
            DisconnectCause.BUSY -> EndReason.BUSY
            DisconnectCause.ERROR, DisconnectCause.RESTRICTED -> EndReason.ERROR
            DisconnectCause.CANCELED -> EndReason.CANCELED
            else -> EndReason.OTHER
        }
    }

    private fun mapState(state: Int): CallStatus = when (state) {
        Call.STATE_NEW -> CallStatus.NEW
        Call.STATE_DIALING -> CallStatus.DIALING
        Call.STATE_RINGING -> CallStatus.RINGING
        Call.STATE_HOLDING -> CallStatus.HOLDING
        Call.STATE_ACTIVE -> CallStatus.ACTIVE
        Call.STATE_DISCONNECTED -> CallStatus.DISCONNECTED
        Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> CallStatus.CONNECTING
        Call.STATE_DISCONNECTING -> CallStatus.DISCONNECTING
        Call.STATE_SELECT_PHONE_ACCOUNT -> CallStatus.SELECT_ACCOUNT
        else -> CallStatus.NEW
    }

    private fun toInfo(t: Tracked): CallInfo {
        val call = t.call
        val d = call.details
        val caps = d?.callCapabilities ?: 0
        val conferenceable = try {
            call.conferenceableCalls.isNotEmpty()
        } catch (e: RuntimeException) {
            false
        }
        return CallInfo(
            id = t.id,
            number = t.number,
            name = t.name ?: d?.callerDisplayName?.takeIf { it.isNotBlank() },
            photoUri = t.photo,
            status = mapState(call.state),
            incoming = t.incoming,
            supportsHold = (caps and Call.Details.CAPABILITY_SUPPORT_HOLD) != 0,
            canHoldNow = (caps and Call.Details.CAPABILITY_HOLD) != 0,
            canMerge = (caps and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0 || conferenceable,
            isConference = d?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true,
            participants = try {
                call.children.size
            } catch (e: RuntimeException) {
                0
            },
            connectTimeMillis = d?.connectTimeMillis ?: 0L,
            endReason = t.endReason,
        )
    }

    private fun publish() {
        val live = tracked.filter {
            try {
                it.call.parent == null
            } catch (e: RuntimeException) {
                true
            }
        }.map { toInfo(it) }
        val all = live + lingering
        _calls.value = all
        updateNotifications(live)
    }

    private fun updateNotifications(live: List<CallInfo>) {
        val ringing = live.firstOrNull { it.status == CallStatus.RINGING }
        if (ringing != null) {
            val key = "in:${ringing.id}:${ringing.name}"
            if (key != postedKey) {
                CallNotifications.showIncoming(appContext, ringing, firstPost = incomingPostedId != ringing.id)
                incomingPostedId = ringing.id
                postedKey = key
            }
            return
        }
        incomingPostedId = -1
        val primary = live.firstOrNull { it.status != CallStatus.DISCONNECTED }
        if (primary == null) {
            if (postedKey != null) {
                CallNotifications.cancelCall(appContext)
                postedKey = null
            }
        } else {
            val key = "on:${primary.id}:${primary.status}:${primary.name}:${primary.connectTimeMillis}"
            if (key != postedKey) {
                CallNotifications.showOngoing(appContext, primary)
                postedKey = key
            }
        }
    }

    private fun lookupAsync(t: Tracked) {
        val number = t.number
        if (number.isEmpty()) return
        io.execute {
            var name: String? = null
            var photo: String? = null
            try {
                val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                appContext.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0)
                        photo = c.getString(1)
                    }
                }
            } catch (e: Exception) {
                // No permission / provider unavailable: show the bare number.
            }
            if (name != null || photo != null) {
                handler.post {
                    if (!t.removed) {
                        t.name = name
                        t.photo = photo
                        publish()
                    }
                }
            }
        }
    }

    private const val LINGER_MS = 2500L
    private const val DTMF_MS = 150L
}
