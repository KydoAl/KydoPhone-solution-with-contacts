package org.aust.dialer.sms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads and writes Android's own SMS provider (content://sms). No separate message store is kept.
 * Only usable while the app holds the default-SMS role; write calls fail quietly otherwise.
 */
class SmsRepository(private val context: Context) {

    fun hasReadPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasSendPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * The conversation list, newest first. Built from the most recent [scanLimit] messages, so a
     * thread whose only messages are older than that window will not appear (documented limitation,
     * same trade-off the call log view makes).
     */
    fun loadConversations(scanLimit: Int = 2000): List<Conversation> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )
        data class Row(val threadId: Long, val address: String, val body: String, val date: Long, val type: Int, val read: Boolean)
        val rows = ArrayList<Row>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT $scanLimit",
            )?.use { c ->
                val iThread = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val iAddress = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val iType = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val iRead = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                while (c.moveToNext()) {
                    val address = c.getString(iAddress)?.trim().orEmpty()
                    if (address.isEmpty()) continue
                    rows.add(
                        Row(
                            threadId = c.getLong(iThread),
                            address = address,
                            body = c.getString(iBody).orEmpty(),
                            date = c.getLong(iDate),
                            type = c.getInt(iType),
                            read = c.getInt(iRead) != 0,
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: RuntimeException) {
            return emptyList()
        }
        val byThread = LinkedHashMap<Long, MutableList<Row>>()
        for (r in rows) byThread.getOrPut(r.threadId) { ArrayList() }.add(r)
        return byThread.map { (threadId, group) ->
            val latest = group.first() // rows arrived newest-first
            val unread = group.count { it.type == Telephony.Sms.MESSAGE_TYPE_INBOX && !it.read }
            Conversation(
                threadId = threadId,
                address = latest.address,
                snippet = latest.body,
                date = latest.date,
                unreadCount = unread,
                outgoing = latest.type != Telephony.Sms.MESSAGE_TYPE_INBOX,
            )
        }.sortedByDescending { it.date }
    }

    fun loadThread(threadId: Long, limit: Int = 1000): List<SmsMessage> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.ERROR_CODE,
        )
        val out = ArrayList<SmsMessage>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val iThread = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val iAddress = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val iType = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val iRead = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                val iError = c.getColumnIndexOrThrow(Telephony.Sms.ERROR_CODE)
                while (c.moveToNext()) {
                    out.add(
                        SmsMessage(
                            id = c.getLong(iId),
                            threadId = c.getLong(iThread),
                            address = c.getString(iAddress).orEmpty(),
                            body = c.getString(iBody).orEmpty(),
                            date = c.getLong(iDate),
                            type = c.getInt(iType),
                            read = c.getInt(iRead) != 0,
                            errorCode = c.getInt(iError),
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: RuntimeException) {
            return emptyList()
        }
        return out.sortedBy { it.date } // chronological for the thread view
    }

    fun threadIdFor(address: String): Long = try {
        Telephony.Threads.getOrCreateThreadId(context, address)
    } catch (e: RuntimeException) {
        0L
    }

    fun markThreadRead(threadId: Long) {
        try {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            )
        } catch (e: RuntimeException) {
            // Best effort only.
        }
    }

    fun deleteThread(threadId: Long): Boolean = try {
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        true
    } catch (e: SecurityException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    fun deleteMessage(id: Long): Boolean = try {
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms._ID} = ?", arrayOf(id.toString()))
        true
    } catch (e: SecurityException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    /** Inserts an inbox row for a message received while we hold the default-SMS role. */
    fun insertInbox(address: String, body: String, date: Long): Long {
        val threadId = threadIdFor(address)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, date)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        return try {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: -1L
        } catch (e: RuntimeException) {
            -1L
        }
    }

    /**
     * Writes an outgoing row (OUTBOX) and hands it to [SmsManager]. The status is corrected to SENT or
     * FAILED by [SmsStatusReceiver] once the radio replies. Returns the new row id, or -1 on failure.
     */
    fun send(address: String, body: String, subId: Int?): Long {
        if (!hasSendPermission() || address.isBlank() || body.isBlank()) return -1L
        val threadId = threadIdFor(address)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, now)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        val uri = try {
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
        } catch (e: RuntimeException) {
            null
        } ?: return -1L
        val id = uri.lastPathSegment?.toLongOrNull() ?: return -1L

        val manager = smsManager(subId) ?: run {
            markFailed(id)
            return id
        }
        try {
            val parts = manager.divideMessage(body)
            SmsStatusReceiver.trackSend(id, parts.size)
            val sentIntents = ArrayList<android.app.PendingIntent>(parts.size)
            for (i in parts.indices) sentIntents.add(SmsStatusReceiver.sentPendingIntent(context, id, i))
            manager.sendMultipartTextMessage(address, null, parts, sentIntents, null)
        } catch (e: Exception) {
            markFailed(id)
        }
        return id
    }

    fun resend(id: Long, address: String, body: String, subId: Int?): Boolean {
        if (!hasSendPermission()) return false
        try {
            val values = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX); put(Telephony.Sms.ERROR_CODE, 0) }
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms._ID} = ?", arrayOf(id.toString()))
        } catch (e: RuntimeException) {
            return false
        }
        val manager = smsManager(subId) ?: run {
            markFailed(id)
            return false
        }
        return try {
            val parts = manager.divideMessage(body)
            SmsStatusReceiver.trackSend(id, parts.size)
            val sentIntents = ArrayList<android.app.PendingIntent>(parts.size)
            for (i in parts.indices) sentIntents.add(SmsStatusReceiver.sentPendingIntent(context, id, i))
            manager.sendMultipartTextMessage(address, null, parts, sentIntents, null)
            true
        } catch (e: Exception) {
            markFailed(id)
            false
        }
    }

    private fun markFailed(id: Long) {
        try {
            val values = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms._ID} = ?", arrayOf(id.toString()))
        } catch (e: RuntimeException) {
            // ignore
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(subId: Int?): SmsManager? = try {
        if (subId != null && subId != -1) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Marks a row SENT once every part of a multipart message has been acknowledged, from the provider side. */
        fun applyResult(context: Context, id: Long, success: Boolean, errorCode: Int) {
            val repo = SmsRepository(context)
            try {
                val values = ContentValues()
                if (success) {
                    values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    values.put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
                } else {
                    values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                    values.put(Telephony.Sms.ERROR_CODE, errorCode)
                }
                context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms._ID} = ?", arrayOf(id.toString()))
            } catch (e: RuntimeException) {
                // ignore
            }
            repo.hashCode() // keep the local instance referenced (no-op)
        }
    }
}

/** In-process tracking of multipart sends: whether every part succeeded, since the provider row is one unit. */
private object PendingSendTracker {
    val remaining = ConcurrentHashMap<Long, AtomicInteger>()
    val failed = ConcurrentHashMap.newKeySet<Long>()
}

internal fun trackNewSend(id: Long, partCount: Int) {
    PendingSendTracker.remaining[id] = AtomicInteger(partCount)
    PendingSendTracker.failed.remove(id)
}

internal fun recordPartResult(context: Context, id: Long, success: Boolean, errorCode: Int) {
    if (!success) PendingSendTracker.failed.add(id)
    val left = PendingSendTracker.remaining[id]?.decrementAndGet() ?: 0
    if (left <= 0) {
        PendingSendTracker.remaining.remove(id)
        val ok = !PendingSendTracker.failed.remove(id)
        SmsRepository.applyResult(context, id, ok, errorCode)
    }
}
