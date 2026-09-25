package org.aust.dialer.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import org.aust.dialer.core.PhoneUtils

/** A SIM (call-capable phone account). [label] may be empty; the UI then falls back to "SIM n". */
data class SimAccount(val handle: PhoneAccountHandle, val label: String)

object CallPlacer {
    enum class Result { OK, NO_PERMISSION, INVALID, ERROR }

    fun hasCallPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun hasPhoneStatePermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun telecom(context: Context): TelecomManager? = context.getSystemService(TelecomManager::class.java)

    /** SIM accounts that can place calls. Empty when READ_PHONE_STATE is denied or there is no SIM. */
    fun simAccounts(context: Context): List<SimAccount> {
        if (!hasPhoneStatePermission(context)) return emptyList()
        val tm = telecom(context) ?: return emptyList()
        return try {
            tm.callCapablePhoneAccounts.mapNotNull { handle ->
                val account = tm.getPhoneAccount(handle)
                if (account != null && !account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                    null
                } else {
                    SimAccount(handle, account?.label?.toString().orEmpty())
                }
            }
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    /** The SIM the user chose as "always use for calls", or null when the system is set to "ask every time". */
    fun defaultAccount(context: Context): PhoneAccountHandle? {
        if (!hasPhoneStatePermission(context)) return null
        return try {
            telecom(context)?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
        } catch (e: SecurityException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    /** The SIM's subscription id for [handle], for SmsManager.getSmsManagerForSubscriptionId(). Requires API 30+. */
    fun subIdFor(context: Context, handle: PhoneAccountHandle): Int? {
        if (!hasPhoneStatePermission(context)) return null
        return try {
            context.getSystemService(TelephonyManager::class.java)
                ?.createForPhoneAccountHandle(handle)
                ?.subscriptionId
                ?.takeIf { it != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } catch (e: SecurityException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    fun place(context: Context, number: String, handle: PhoneAccountHandle?): Result {
        val dialable = PhoneUtils.sanitizeDialable(number)
        if (dialable.isEmpty()) return Result.INVALID
        if (!hasCallPermission(context)) return Result.NO_PERMISSION
        val tm = telecom(context) ?: return Result.ERROR
        val extras = Bundle()
        if (handle != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        return try {
            tm.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, dialable, null), extras)
            Result.OK
        } catch (e: SecurityException) {
            Result.NO_PERMISSION
        } catch (e: RuntimeException) {
            Result.ERROR
        }
    }
}
