package org.aust.dialer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.aust.dialer.sms.Conversation
import org.aust.dialer.sms.SmsMessage
import org.aust.dialer.sms.SmsRepository
import org.aust.dialer.sms.SmsNotifications
import org.aust.dialer.core.Prefs
import org.aust.dialer.telecom.RoleUtils

data class SmsPerms(
    val readSms: Boolean,
    val sendSms: Boolean,
    val isDefaultSms: Boolean,
) {
    val coreGranted: Boolean get() = readSms && sendSms
}

class SmsViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val repo = SmsRepository(ctx)
    private val prefs = Prefs(ctx)
    private val handler = Handler(Looper.getMainLooper())

    private val _perms = MutableStateFlow(readPerms())
    val perms: StateFlow<SmsPerms> = _perms.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    val pinnedThreads: StateFlow<Set<Long>> = prefs.pinnedSmsThreads
    val blockedSenders: StateFlow<Set<String>> = prefs.blockedSmsSenders

    private var openThreadId: Long? = null
    private val _threadMessages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val threadMessages: StateFlow<List<SmsMessage>> = _threadMessages.asStateFlow()

    private var loadedWith: Boolean? = null

    private val reload = Runnable {
        loadConversations()
        openThreadId?.let { loadThread(it) }
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(reload)
            handler.postDelayed(reload, 250)
        }
    }

    init {
        try {
            ctx.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: RuntimeException) {
            // The observer is an optimisation; data still loads on demand.
        }
        refreshPermissions()
    }

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun readPerms() = SmsPerms(
        readSms = granted(Manifest.permission.READ_SMS),
        sendSms = granted(Manifest.permission.SEND_SMS),
        isDefaultSms = RoleUtils.isDefaultSms(ctx),
    )

    fun refreshPermissions() {
        val p = readPerms()
        _perms.value = p
        if (loadedWith != p.readSms) loadConversations()
    }

    private fun loadConversations() {
        loadedWith = _perms.value.readSms
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadConversations() }
            _conversations.value = list
        }
    }

    fun openThread(threadId: Long) {
        openThreadId = threadId
        loadThread(threadId)
    }

    fun closeThread() {
        openThreadId = null
        _threadMessages.value = emptyList()
    }

    private fun loadThread(threadId: Long) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadThread(threadId) }
            if (openThreadId == threadId) _threadMessages.value = list
        }
    }

    fun threadIdFor(address: String): Long = repo.threadIdFor(address)

    fun setThreadPinned(threadId: Long, pinned: Boolean) = prefs.setSmsThreadPinned(threadId, pinned)

    fun isSenderBlocked(address: String): Boolean = prefs.isSmsSenderBlocked(address)

    fun senderKey(address: String): String = org.aust.dialer.core.PhoneUtils.senderKey(address)

    fun setSenderBlocked(address: String, blocked: Boolean) {
        prefs.setSmsSenderBlocked(address, blocked)
        if (blocked) SmsNotifications.cancel(ctx, address)
    }

    fun send(address: String, body: String, subId: Int?) {
        viewModelScope.launch(Dispatchers.IO) { repo.send(address, body, subId) }
    }

    fun resend(message: SmsMessage, subId: Int?) {
        viewModelScope.launch(Dispatchers.IO) { repo.resend(message.id, message.address, message.body, subId) }
    }

    fun markThreadRead(threadId: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.markThreadRead(threadId) }
    }

    fun deleteThread(threadId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.deleteThread(threadId) }
            if (ok) loadConversations()
            onResult(ok)
        }
    }

    fun deleteMessage(id: Long, threadId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.deleteMessage(id) }
            if (ok) {
                loadConversations()
                loadThread(threadId)
            }
            onResult(ok)
        }
    }

    override fun onCleared() {
        try {
            ctx.contentResolver.unregisterContentObserver(observer)
        } catch (e: RuntimeException) {
            // ignore
        }
        handler.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
