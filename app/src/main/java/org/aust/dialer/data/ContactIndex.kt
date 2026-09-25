package org.aust.dialer.data

import org.aust.dialer.core.PhoneUtils

data class DialMatch(val contact: Contact, val entry: PhoneEntry)

/** In-memory lookup structure built once per contacts load. */
class ContactIndex(val contacts: List<Contact>) {

    private val byKey: Map<String, Contact>

    init {
        val map = HashMap<String, Contact>()
        for (c in contacts) {
            for (n in c.numbers) {
                val key = PhoneUtils.matchKey(n.number)
                if (key.length >= 3) map.putIfAbsent(key, c)
            }
        }
        byKey = map
    }

    val favorites: List<Contact> get() = contacts.filter { it.starred }

    fun findByNumber(number: String?): Contact? {
        if (number == null || PhoneUtils.isUnknown(number)) return null
        return byKey[PhoneUtils.matchKey(number)]
    }

    fun findById(id: Long): Contact? = contacts.firstOrNull { it.id == id }

    /** Text search on the Contacts tab: name (accent-insensitive) or digits inside any number. */
    fun search(query: String): List<Contact> {
        val q = PhoneUtils.fold(query.trim())
        if (q.isEmpty()) return contacts
        val qDigits = PhoneUtils.digitsOnly(q)
        return contacts.filter { c ->
            c.searchName.contains(q) ||
                PhoneUtils.fold(c.company).contains(q) ||
                (qDigits.length >= 2 && c.numbers.any { PhoneUtils.digitsOnly(it.number).contains(qDigits) })
        }
    }

    /**
     * Matches while typing on the dial pad: T9 on names (a name whose letters map to the typed digits)
     * and plain digit matching inside stored numbers.
     */
    fun matchDial(typed: String, limit: Int = 5): List<DialMatch> {
        val digits = typed.filter { it in '0'..'9' }
        if (digits.length < 2) return emptyList()
        val out = ArrayList<DialMatch>()
        for (c in contacts) {
            if (out.size >= limit) break
            val nameHit = PhoneUtils.t9Matches(c.t9Words, digits)
            val entry = if (nameHit) {
                c.numbers.firstOrNull { it.isPrimary } ?: c.numbers.firstOrNull()
            } else {
                c.numbers.firstOrNull { PhoneUtils.digitsOnly(it.number).contains(digits) }
            }
            if (entry != null) out.add(DialMatch(c, entry))
        }
        return out
    }
}
