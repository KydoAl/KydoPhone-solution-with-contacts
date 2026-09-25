package org.aust.dialer.data

import android.net.Uri
import android.provider.ContactsContract
import org.aust.dialer.core.PhoneUtils

data class PhoneEntry(
    val number: String,
    val typeLabel: String,
    val isPrimary: Boolean,
    val dataId: Long = -1L,
)

data class Contact(
    val id: Long,
    val lookupKey: String?,
    val name: String,
    val photoUri: String?,
    val thumbUri: String?,
    val starred: Boolean,
    val numbers: List<PhoneEntry>,
    val givenName: String = "",
    val middleName: String = "",
    val familyName: String = "",
    val suffix: String = "",
    val company: String = "",
) {
    val searchName: String by lazy(LazyThreadSafetyMode.NONE) { PhoneUtils.fold(name) }
    val t9Words: List<String> by lazy(LazyThreadSafetyMode.NONE) { PhoneUtils.t9Words(name) }

    val lookupUri: Uri?
        get() = if (lookupKey != null) ContactsContract.Contacts.getLookupUri(id, lookupKey) else null

    val primaryNumber: String?
        get() = (numbers.firstOrNull { it.isPrimary } ?: numbers.firstOrNull())?.number

    /** First letter used for the alphabetical section header ("#" for anything that is not a letter). */
    val initial: String
        get() {
            val c = name.trim().firstOrNull() ?: return "#"
            return if (c.isLetter()) c.uppercaseChar().toString() else "#"
        }
}

data class CallLogEntry(
    val id: Long,
    val number: String,
    val type: Int,
    val date: Long,
    val durationSec: Long,
    val cachedName: String?,
    val cachedPhoto: String?,
)

/** All recent calls belonging to the same contact/number are shown as one row. */
data class CallGroup(val entries: List<CallLogEntry>) {
    val first: CallLogEntry get() = entries.first()
    val count: Int get() = entries.size
    val key: Long get() = first.id
}

object CallGrouping {
    /**
     * Groups the complete recent-call window, not merely consecutive rows.
     * When a number belongs to a contact, the contact id is used so calls to
     * different numbers of the same contact are combined as well.
     */
    fun group(list: List<CallLogEntry>, contacts: ContactIndex? = null): List<CallGroup> {
        val buckets = LinkedHashMap<String, MutableList<CallLogEntry>>()
        for (e in list) {
            val contact = contacts?.findByNumber(e.number)
            val numberKey = PhoneUtils.matchKey(e.number)
            val key = when {
                contact != null -> "contact:${contact.id}"
                !PhoneUtils.isUnknown(e.number) && numberKey.isNotEmpty() -> "number:$numberKey"
                else -> "row:${e.id}"
            }
            buckets.getOrPut(key) { ArrayList() }.add(e)
        }
        return buckets.values.map(::CallGroup)
    }
}
