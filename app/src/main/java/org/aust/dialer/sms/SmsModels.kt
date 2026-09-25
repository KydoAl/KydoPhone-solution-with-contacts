package org.aust.dialer.sms

import android.provider.Telephony

/** A single row from Android's SMS provider (content://sms). */
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
    val errorCode: Int,
) {
    val isOutgoing: Boolean
        get() = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
            type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
            type == Telephony.Sms.MESSAGE_TYPE_FAILED

    val isFailed: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val isSending: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}

/** One row of the Conversations (thread) list: the latest message plus a few aggregates. */
data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val outgoing: Boolean,
) {
    val hasUnread: Boolean get() = unreadCount > 0
}
