package org.aust.dialer.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** Default-app role helpers (phone + SMS). The system UI grants each role; the app never bypasses it. */
object RoleUtils {
    private fun manager(context: Context): RoleManager? = context.getSystemService(RoleManager::class.java)

    private fun isHeld(context: Context, role: String): Boolean {
        val rm = manager(context) ?: return false
        return try {
            rm.isRoleAvailable(role) && rm.isRoleHeld(role)
        } catch (e: Exception) {
            false
        }
    }

    private fun request(context: Context, role: String): Intent? {
        val rm = manager(context) ?: return null
        return try {
            if (rm.isRoleAvailable(role)) rm.createRequestRoleIntent(role) else null
        } catch (e: Exception) {
            null
        }
    }

    fun isDefaultDialer(context: Context): Boolean = isHeld(context, RoleManager.ROLE_DIALER)

    /** The system role-request dialog, or null when the device does not offer the dialer role. */
    fun requestIntent(context: Context): Intent? = request(context, RoleManager.ROLE_DIALER)

    fun isDefaultSms(context: Context): Boolean = isHeld(context, RoleManager.ROLE_SMS)

    /** The system role-request dialog, or null when the device does not offer the SMS role. */
    fun requestSmsIntent(context: Context): Intent? = request(context, RoleManager.ROLE_SMS)
}
