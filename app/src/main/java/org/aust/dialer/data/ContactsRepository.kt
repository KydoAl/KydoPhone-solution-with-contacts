package org.aust.dialer.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.aust.dialer.core.PhoneUtils
import java.text.Collator

data class ContactDraft(
    val givenName: String,
    val middleName: String,
    val familyName: String,
    val suffix: String,
    val company: String,
    val numbers: List<String>,
) {
    fun displayName(): String = listOf(givenName, middleName, familyName, suffix).map(String::trim).filter(String::isNotEmpty).joinToString(" ").ifEmpty { company.trim() }
}

/** Reads the device's own Contacts Provider. No separate contacts database is kept. */
class ContactsRepository(private val context: Context) {

    fun hasReadPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private class Builder(
        val id: Long,
        val lookupKey: String?,
        val name: String,
        val photoUri: String?,
        val thumbUri: String?,
        val starred: Boolean,
    ) {
        var givenName: String = ""
        var middleName: String = ""
        var familyName: String = ""
        var suffix: String = ""
        var company: String = ""
        val numbers = ArrayList<PhoneEntry>()
        private val seen = HashSet<String>()

        fun add(e: PhoneEntry) {
            val key = PhoneUtils.matchKey(e.number).ifEmpty { e.number }
            if (seen.add(key)) numbers.add(e)
        }

        fun build() = Contact(id, lookupKey, name, photoUri, thumbUri, starred, numbers, givenName, middleName, familyName, suffix, company)
    }

    /** Every contact that has at least one phone number, sorted by name. Empty when permission is missing. */
    fun loadAll(): List<Contact> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.PHOTO_URI,
            Phone.PHOTO_THUMBNAIL_URI,
            Phone.STARRED,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            Phone.IS_PRIMARY,
            Phone._ID,
        )
        val builders = LinkedHashMap<Long, Builder>()
        try {
            context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
                val iLookup = c.getColumnIndexOrThrow(Phone.LOOKUP_KEY)
                val iName = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
                val iPhoto = c.getColumnIndexOrThrow(Phone.PHOTO_URI)
                val iThumb = c.getColumnIndexOrThrow(Phone.PHOTO_THUMBNAIL_URI)
                val iStar = c.getColumnIndexOrThrow(Phone.STARRED)
                val iNumber = c.getColumnIndexOrThrow(Phone.NUMBER)
                val iType = c.getColumnIndexOrThrow(Phone.TYPE)
                val iLabel = c.getColumnIndexOrThrow(Phone.LABEL)
                val iPrimary = c.getColumnIndexOrThrow(Phone.IS_PRIMARY)
                val iDataId = c.getColumnIndexOrThrow(Phone._ID)
                while (c.moveToNext()) {
                    val number = c.getString(iNumber)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val id = c.getLong(iId)
                    val b = builders.getOrPut(id) {
                        val name = c.getString(iName)?.trim().orEmpty().ifEmpty { number }
                        Builder(id, c.getString(iLookup), name, c.getString(iPhoto), c.getString(iThumb), c.getInt(iStar) == 1)
                    }
                    val typeLabel = try {
                        Phone.getTypeLabel(context.resources, c.getInt(iType), c.getString(iLabel)).toString()
                    } catch (e: Exception) {
                        ""
                    }
                    b.add(PhoneEntry(number, typeLabel, c.getInt(iPrimary) != 0, c.getLong(iDataId)))
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: RuntimeException) {
            return emptyList()
        }

        // Load structured-name and organization fields in one additional provider query.
        // This avoids one query per contact and keeps contact loading off the UI thread.
        if (builders.isNotEmpty()) {
            try {
                val ids = builders.keys.joinToString(",")
                val projection2 = arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
                    ContactsContract.CommonDataKinds.Organization.COMPANY,
                )
                context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    projection2,
                    "${ContactsContract.Data.CONTACT_ID} IN ($ids) AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)",
                    arrayOf(
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                    ),
                    null,
                )?.use { c ->
                    val iId = c.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                    val iMime = c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                    val iGiven = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
                    val iMiddle = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME)
                    val iFamily = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
                    val iSuffix = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.SUFFIX)
                    val iCompany = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.COMPANY)
                    while (c.moveToNext()) {
                        val b = builders[c.getLong(iId)] ?: continue
                        if (c.getString(iMime) == ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) {
                            b.givenName = c.getString(iGiven).orEmpty()
                            b.middleName = c.getString(iMiddle).orEmpty()
                            b.familyName = c.getString(iFamily).orEmpty()
                            b.suffix = c.getString(iSuffix).orEmpty()
                        } else {
                            b.company = c.getString(iCompany).orEmpty()
                        }
                    }
                }
            } catch (_: Exception) {
                // Structured fields are optional; phone/name data remains usable.
            }
        }
        val collator = Collator.getInstance()
        return builders.values.map { it.build() }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** Creates a local/raw contact with a name and one or more phone numbers. */
    fun createContact(draft: ContactDraft): Long? {
        if (!hasWritePermission()) return null
        val cleanNumbers = draft.numbers.map(String::trim).filter(String::isNotEmpty).distinct()
        if (cleanNumbers.isEmpty()) return null
        val displayName = draft.displayName()
        if (displayName.isBlank()) return null
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .build()
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, draft.givenName.trim().ifEmpty { displayName })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, draft.middleName.trim().ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, draft.familyName.trim().ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, draft.suffix.trim().ifEmpty { null })
                .build()
            if (draft.company.trim().isNotEmpty()) {
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, draft.company.trim())
                    .build()
            }
            cleanNumbers.forEachIndexed { i, number ->
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, number)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .withValue(Phone.IS_PRIMARY, if (i == 0) 1 else 0)
                    .build()
            }
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val rawUri = results.firstOrNull()?.uri ?: return null
            val rawId = ContentUris.parseId(rawUri)
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                "${ContactsContract.RawContacts._ID}=?",
                arrayOf(rawId.toString()),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Updates the writable raw contact behind an aggregate contact. */
    fun updateContact(contact: Contact, draft: ContactDraft): Boolean {
        if (!hasWritePermission()) return false
        val clean = draft.numbers.map(String::trim).filter(String::isNotEmpty).distinct()
        if (clean.isEmpty() || draft.displayName().isBlank()) return false
        return try {
            val rawId = findWritableRawContactId(contact.id) ?: return false
            val phoneRows = loadRawPhoneRows(rawId)
            val byNumber = phoneRows.associateBy { PhoneUtils.matchKey(it.number).ifEmpty { it.number } }
            val usedIds = HashSet<Long>()
            val ops = ArrayList<ContentProviderOperation>()

            val nameRows = queryDataIds(rawId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            if (nameRows.isNotEmpty()) {
                ops += ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection("${ContactsContract.Data._ID}=?", arrayOf(nameRows.first().toString()))
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, draft.displayName())
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, draft.givenName.trim().ifEmpty { draft.displayName() })
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, draft.middleName.trim().ifEmpty { null })
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, draft.familyName.trim().ifEmpty { null })
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, draft.suffix.trim().ifEmpty { null })
                    .build()
            } else {
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, draft.displayName())
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, draft.givenName.trim().ifEmpty { draft.displayName() })
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, draft.middleName.trim().ifEmpty { null })
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, draft.familyName.trim().ifEmpty { null })
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, draft.suffix.trim().ifEmpty { null })
                    .build()
            }

            // Replace the writable raw contact's organization row.
            queryDataIds(rawId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE).forEach { dataId ->
                ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection("${ContactsContract.Data._ID}=?", arrayOf(dataId.toString()))
                    .build()
            }
            if (draft.company.trim().isNotEmpty()) {
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, draft.company.trim())
                    .build()
            }

            clean.forEachIndexed { index, number ->
                val key = PhoneUtils.matchKey(number).ifEmpty { number }
                val old = byNumber[key]
                if (old != null) {
                    usedIds += old.dataId
                    ops += ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection("${ContactsContract.Data._ID}=?", arrayOf(old.dataId.toString()))
                        .withValue(Phone.NUMBER, number)
                        .withValue(Phone.IS_PRIMARY, if (index == 0) 1 else 0)
                        .build()
                } else {
                    ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                        .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, number)
                        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                        .withValue(Phone.IS_PRIMARY, if (index == 0) 1 else 0)
                        .build()
                }
            }

            phoneRows.filterNot { it.dataId in usedIds }.forEach { row ->
                ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection("${ContactsContract.Data._ID}=?", arrayOf(row.dataId.toString()))
                    .build()
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private data class RawPhoneRow(val dataId: Long, val number: String)

    private fun loadRawPhoneRows(rawId: Long): List<RawPhoneRow> {
        val result = ArrayList<RawPhoneRow>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, Phone.NUMBER),
            "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(rawId.toString(), Phone.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1)?.trim().orEmpty()
                if (number.isNotEmpty()) result += RawPhoneRow(c.getLong(0), number)
            }
        }
        return result
    }

    private fun queryDataIds(rawId: Long, mimeType: String): List<Long> {
        val result = ArrayList<Long>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(rawId.toString(), mimeType),
            null,
        )?.use { c -> while (c.moveToNext()) result += c.getLong(0) }
        return result
    }

    fun deleteContact(contactId: Long): Boolean {
        if (!hasWritePermission()) return false
        return try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: SecurityException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }

    private fun hasWritePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun findWritableRawContactId(contactId: Long): Long? {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.CONTACT_ID}=? AND ${ContactsContract.RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            var fallback: Long? = null
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val accountType = c.getString(1)
                if (accountType == null) return id
                if (fallback == null) fallback = id
            }
            return fallback
        }
        return null
    }

    /** Stars / un-stars a contact (Android's own "favorite" flag). Needs WRITE_CONTACTS. */
    fun setStarred(contactId: Long, starred: Boolean): Boolean {
        return try {
            val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: SecurityException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }
}
