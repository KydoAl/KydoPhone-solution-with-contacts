package org.aust.dialer.ui.sms

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.SmsViewModel
import org.aust.dialer.core.Format
import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.sms.SmsMessage
import org.aust.dialer.sms.SmsNotifications
import org.aust.dialer.telecom.CallPlacer
import org.aust.dialer.telecom.SimAccount
import org.aust.dialer.ui.Avatar
import org.aust.dialer.ui.LocalCallController
import org.aust.dialer.ui.LocalSnackbar
import org.aust.dialer.ui.SimPickerDialog
import org.aust.dialer.ui.rememberPermissionRequester

/** [address] is the raw phone number / sender id for this conversation (SMS threads are per-address). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(dialerVm: DialerViewModel, smsVm: SmsViewModel, address: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val index by dialerVm.contactIndex.collectAsState()
    val smsPerms by smsVm.perms.collectAsState()
    val messages by smsVm.threadMessages.collectAsState()
    val callController = LocalCallController.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var draft by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteThread by remember { mutableStateOf(false) }
    var confirmDeleteMessage by remember { mutableStateOf<SmsMessage?>(null) }
    var simChoice by remember { mutableStateOf<List<SimAccount>?>(null) }
    val contact = index.findByNumber(address)
    val title = contact?.name ?: if (PhoneUtils.isUnknown(address)) address else Format.ltr(Format.number(address))
    val threadId = remember(address) { smsVm.threadIdFor(address) }
    val pinned by smsVm.pinnedThreads.collectAsState()
    val blocked by smsVm.blockedSenders.collectAsState()
    val isPinned = threadId in pinned
    val isBlocked = PhoneUtils.senderKey(address) in blocked

    val requestSend = rememberPermissionRequester(smsVm::refreshPermissions, arrayOf(Manifest.permission.SEND_SMS))
    val deletedMsg = stringResource(R.string.recents_deleted)

    LaunchedEffect(threadId) {
        smsVm.openThread(threadId)
        smsVm.markThreadRead(threadId)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    fun sendWith(body: String, subId: Int?) {
        smsVm.send(address, body, subId)
        draft = ""
    }

    fun doSend() {
        val body = draft.trim()
        if (body.isEmpty()) return
        if (!smsPerms.sendSms) {
            requestSend()
            return
        }
        val accounts = CallPlacer.simAccounts(context)
        val defaultAccount = CallPlacer.defaultAccount(context)
        if (accounts.size > 1 && defaultAccount == null) {
            simChoice = accounts
        } else {
            val subId = defaultAccount?.let { CallPlacer.subIdFor(context, it) }
            sendWith(body, subId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(contact?.name ?: address, contact?.thumbUri, 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                title,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (contact != null) {
                                Text(
                                    Format.ltr(Format.number(address)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { callController.call(address) }) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = stringResource(R.string.action_call),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (isPinned) R.string.sms_unpin else R.string.sms_pin)) },
                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                            onClick = { menuOpen = false; smsVm.setThreadPinned(threadId, !isPinned) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (isBlocked) R.string.sms_unblock_sender else R.string.sms_block_sender)) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                            onClick = { menuOpen = false; smsVm.setSenderBlocked(address, !isBlocked) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sms_notification_settings)) },
                            leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                SmsNotifications.openConversationNotificationSettings(context, threadId, title)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sms_delete_conversation), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmDeleteThread = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(stringResource(R.string.sms_type_message)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                    FilledIconButton(
                        onClick = { doSend() },
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.action_send)
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    msg,
                    onLongClick = { confirmDeleteMessage = msg },
                    onRetry = { smsVm.resend(msg, null) },
                )
            }
        }
    }

    if (confirmDeleteThread) {
        AlertDialog(
            onDismissRequest = { confirmDeleteThread = false },
            title = { Text(stringResource(R.string.sms_delete_conversation)) },
            text = { Text(stringResource(R.string.sms_delete_conversation_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteThread = false
                    smsVm.deleteThread(threadId) { ok -> if (ok) onBack() }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteThread = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    confirmDeleteMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { confirmDeleteMessage = null },
            title = { Text(stringResource(R.string.sms_delete_message_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteMessage = null
                    smsVm.deleteMessage(msg.id, threadId) { ok ->
                        scope.launch { snackbar.showSnackbar(if (ok) deletedMsg else "") }
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteMessage = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    simChoice?.let { accounts ->
        val body = draft.trim()
        SimPickerDialog(
            accounts = accounts,
            onPick = { account ->
                simChoice = null
                if (body.isNotEmpty()) sendWith(body, CallPlacer.subIdFor(context, account.handle))
            },
            onDismiss = { simChoice = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(msg: SmsMessage, onLongClick: () -> Unit, onRetry: () -> Unit) {
    val outgoing = msg.isOutgoing
    val bg = when {
        msg.isFailed -> MaterialTheme.colorScheme.errorContainer
        outgoing -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        msg.isFailed -> MaterialTheme.colorScheme.onErrorContainer
        outgoing -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val bubbleShape = if (outgoing) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .background(bg, bubbleShape)
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = msg.body,
                color = fg,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = Format.callTime(LocalContext.current, msg.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.7f),
                )
                if (msg.isFailed) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.height(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (msg.isFailed) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .height(32.dp)
                        .align(Alignment.End)
                ) {
                    Text(
                        stringResource(R.string.sms_retry),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}