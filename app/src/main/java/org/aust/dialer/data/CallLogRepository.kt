package org.aust.dialer.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog.Calls

/** Reads and edits Android's own call log. No separate call history is kept. */
class CallLogRepository(private val context: Context) {

    fun hasReadPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun load(limit: Int = 500): List<CallLogEntry> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.TYPE,
            Calls.DATE,
            Calls.DURATION,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
        )
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        val out = ArrayList<CallLogEntry>()
        try {
            context.contentResolver.query(Calls.CONTENT_URI, projection, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(Calls._ID)
                val iNumber = c.getColumnIndexOrThrow(Calls.NUMBER)
                val iType = c.getColumnIndexOrThrow(Calls.TYPE)
                val iDate = c.getColumnIndexOrThrow(Calls.DATE)
                val iDuration = c.getColumnIndexOrThrow(Calls.DURATION)
                val iName = c.getColumnIndexOrThrow(Calls.CACHED_NAME)
                val iPhoto = c.getColumnIndexOrThrow(Calls.CACHED_PHOTO_URI)
                while (c.moveToNext()) {
                    out.add(
                        CallLogEntry(
                            id = c.getLong(iId),
                            number = c.getString(iNumber).orEmpty(),
                            type = c.getInt(iType),
                            date = c.getLong(iDate),
                            durationSec = c.getLong(iDuration),
                            cachedName = c.getString(iName)?.takeIf { it.isNotBlank() },
                            cachedPhoto = c.getString(iPhoto)?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: RuntimeException) {
            return emptyList()
        }
        return out
    }

    /** Deletes the given call-log rows. Returns false if the OS refused (permission / not default dialer). */
    fun delete(ids: List<Long>): Boolean {
        if (ids.isEmpty()) return true
        return try {
            val where = "${Calls._ID} IN (${ids.joinToString(",")})"
            context.contentResolver.delete(Calls.CONTENT_URI, where, null)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }

    /** Clears the "new" flag on missed calls once the user has seen the Recents tab. */
    fun markMissedRead() {
        if (!hasWritePermission()) return
        try {
            val values = ContentValues().apply {
                put(Calls.NEW, 0)
                put(Calls.IS_READ, 1)
            }
            context.contentResolver.update(
                Calls.CONTENT_URI,
                values,
                "${Calls.TYPE} = ${Calls.MISSED_TYPE} AND ${Calls.IS_READ} = 0",
                null,
            )
        } catch (e: RuntimeException) {
            // Best effort only.
        }
    }
}
