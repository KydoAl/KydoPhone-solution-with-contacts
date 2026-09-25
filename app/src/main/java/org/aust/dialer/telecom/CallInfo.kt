package org.aust.dialer.telecom

import org.aust.dialer.R

enum class CallStatus { NEW, DIALING, CONNECTING, RINGING, ACTIVE, HOLDING, DISCONNECTING, DISCONNECTED, SELECT_ACCOUNT }

enum class EndReason { LOCAL, REMOTE, MISSED, REJECTED, BUSY, ERROR, CANCELED, OTHER }

/** Immutable snapshot of one call, produced from the live android.telecom.Call for the UI. */
data class CallInfo(
    val id: Int,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val status: CallStatus,
    val incoming: Boolean,
    val supportsHold: Boolean,
    val canHoldNow: Boolean,
    val canMerge: Boolean,
    val isConference: Boolean,
    val participants: Int,
    val connectTimeMillis: Long,
    val endReason: EndReason?,
)

data class AudioInfo(
    val muted: Boolean = false,
    val route: Int = 0,
    val supportedMask: Int = 0,
)

/** String resource describing the call state (the timer replaces this text while the call is active). */
fun CallInfo.statusRes(): Int = when (status) {
    CallStatus.NEW, CallStatus.CONNECTING, CallStatus.SELECT_ACCOUNT -> R.string.incall_status_connecting
    CallStatus.DIALING -> R.string.incall_status_dialing
    CallStatus.RINGING -> R.string.incall_status_ringing
    CallStatus.ACTIVE -> R.string.incall_status_active
    CallStatus.HOLDING -> R.string.incall_status_holding
    CallStatus.DISCONNECTING -> R.string.incall_status_disconnecting
    CallStatus.DISCONNECTED -> when (endReason) {
        EndReason.BUSY -> R.string.incall_end_busy
        EndReason.REJECTED -> R.string.incall_end_rejected
        EndReason.ERROR -> R.string.incall_end_failed
        EndReason.MISSED -> R.string.incall_end_missed
        else -> R.string.incall_end_ended
    }
}
