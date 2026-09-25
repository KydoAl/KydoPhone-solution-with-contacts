package org.aust.dialer.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import org.aust.dialer.data.Contact

/** Launches other apps. Every call is guarded: a missing app must never crash the dialer. */
object Intents {

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** Opens the user's SMS app with the number filled in. No SMS permission is needed. */
    fun message(context: Context, number: String): Boolean {
        if (PhoneUtils.isUnknown(number)) return false
        return start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
    }

    fun addContact(context: Context, number: String): Boolean {
        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        return start(context, intent)
    }

    fun newContact(context: Context): Boolean =
        start(context, Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))

    fun editContact(context: Context, contact: Contact): Boolean {
        val uri = contact.lookupUri ?: return false
        return start(context, Intent(Intent.ACTION_EDIT).setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE))
    }

    fun openAppSettings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    fun copyNumber(context: Context, number: String) {
        try {
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("phone number", number))
        } catch (e: Exception) {
            // Clipboard unavailable: nothing else to do.
        }
    }

    fun clipboardText(context: Context): String? {
        return try {
            val cm = context.getSystemService(ClipboardManager::class.java)
            val clip = cm?.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0).coerceToText(context)?.toString()
        } catch (e: Exception) {
            null
        }
    }
}
