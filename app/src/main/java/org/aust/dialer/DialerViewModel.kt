package org.aust.dialer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.aust.dialer.core.Prefs
import org.aust.dialer.data.CallGroup
import org.aust.dialer.data.CallGrouping
import org.aust.dialer.data.CallLogRepository
import org.aust.dialer.data.Contact
import org.aust.dialer.data.ContactIndex
import org.aust.dialer.data.ContactsRepository
import org.aust.dialer.data.ContactDraft
import org.aust.dialer.telecom.RoleUtils

data class Perms(
    val contacts: Boolean,
    val callLog: Boolean,
    val phoneState: Boolean,
    val callPhone: Boolean,
    val writeContacts: Boolean,
    val writeCallLog: Boolean,
    val notifications: Boolean,
    val isDefaultDialer: Boolean,
) {
    val coreGranted: Boolean get() = contacts && callLog && phoneState && callPhone
}

class DialerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val contactsRepo = ContactsRepository(ctx)
    private val callLogRepo = CallLogRepository(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private var suppressCallReload = false

    val prefs: Prefs = (app as DialerApp).prefs

    private val _perms = MutableStateFlow(readPerms())
    val perms: StateFlow<Perms> = _perms.asStateFlow()

    private val _index = MutableStateFlow(ContactIndex(emptyList()))
    val contactIndex: StateFlow<ContactIndex> = _index.asStateFlow()

    private val _recents = MutableStateFlow<List<CallGroup>>(emptyList())
    val recents: StateFlow<List<CallGroup>> = _recents.asStateFlow()

    private var contactsLoadedWith: Boolean? = null
    private var callsLoadedWith: Boolean? = null

    private val reloadContacts = Runnable { loadContacts() }
    private val reloadCalls = Runnable { loadCalls() }

    private val contactsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(reloadContacts)
            handler.postDelayed(reloadContacts, 400)
        }
    }
    private val callsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (suppressCallReload) return
            handler.removeCallbacks(reloadCalls)
            handler.postDelayed(reloadCalls, 250)
        }
    }

    init {
        try {
            ctx.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
            ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callsObserver)
        } catch (e: RuntimeException) {
            // Observers are an optimisation; the lists still load on demand.
        }
        refreshPermissions()
    }

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun readPerms() = Perms(
        contacts = granted(Manifest.permission.READ_CONTACTS),
        callLog = granted(Manifest.permission.READ_CALL_LOG),
        phoneState = granted(Manifest.permission.READ_PHONE_STATE),
        callPhone = granted(Manifest.permission.CALL_PHONE),
        writeContacts = granted(Manifest.permission.WRITE_CONTACTS),
        writeCallLog = granted(Manifest.permission.WRITE_CALL_LOG),
        notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
        isDefaultDialer = RoleUtils.isDefaultDialer(ctx),
    )

    /** Call on resume and after every permission / role result. Reloads data whose permission just changed. */
    fun refreshPermissions() {
        val p = readPerms()
        _perms.value = p
        if (contactsLoadedWith != p.contacts) loadContacts()
        if (callsLoadedWith != p.callLog) loadCalls()
    }

    private fun loadContacts() {
        contactsLoadedWith = _perms.value.contacts
        viewModelScope.launch {
            val index = withContext(Dispatchers.IO) { ContactIndex(contactsRepo.loadAll()) }
            _index.value = index
            if (_perms.value.callLog) loadCalls()
        }
    }

    private fun loadCalls() {
        callsLoadedWith = _perms.value.callLog
        viewModelScope.launch {
            val index = _index.value
            val list = withContext(Dispatchers.IO) {
                CallGrouping.group(callLogRepo.load(), index)
            }
            _recents.value = list
        }
    }

    fun createContact(draft: ContactDraft, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { contactsRepo.createContact(draft) }
            if (id != null) loadContacts()
            onResult(id)
        }
    }

    fun updateContact(contact: Contact, draft: ContactDraft, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { contactsRepo.updateContact(contact, draft) }
            if (ok) loadContacts()
            onResult(ok)
        }
    }

    fun deleteContact(contactId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { contactsRepo.deleteContact(contactId) }
            if (ok) loadContacts()
            onResult(ok)
        }
    }

    fun setStarred(contactId: Long, starred: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { contactsRepo.setStarred(contactId, starred) }
            if (ok) loadContacts()
            onResult(ok)
        }
    }

    fun deleteCalls(group: CallGroup, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { callLogRepo.delete(group.entries.map { it.id }) }
            if (ok) loadCalls()
            onResult(ok)
        }
    }

    fun markMissedRead() {
        viewModelScope.launch(Dispatchers.IO) {
            suppressCallReload = true
            callLogRepo.markMissedRead()
            handler.postDelayed({ suppressCallReload = false }, 600)
        }
    }

    fun contactById(id: Long): Contact? = _index.value.findById(id)

    override fun onCleared() {
        try {
            ctx.contentResolver.unregisterContentObserver(contactsObserver)
            ctx.contentResolver.unregisterContentObserver(callsObserver)
        } catch (e: RuntimeException) {
            // ignore
        }
        handler.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
