package org.aust.dialer.ui

import android.Manifest
import android.provider.CallLog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.core.Format
import org.aust.dialer.core.Intents
import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.data.CallGroup
import org.aust.dialer.data.CallLogEntry
import org.aust.dialer.data.Contact
import org.aust.dialer.telecom.CallNotifications

private fun typeIcon(type: Int): ImageVector = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
    CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
    CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> Icons.Default.Block
    CallLog.Calls.VOICEMAIL_TYPE -> Icons.Default.Voicemail
    else -> Icons.AutoMirrored.Filled.CallReceived
}

private fun typeLabel(type: Int): Int = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> R.string.call_outgoing
    CallLog.Calls.MISSED_TYPE -> R.string.call_missed
    CallLog.Calls.REJECTED_TYPE -> R.string.call_rejected
    CallLog.Calls.BLOCKED_TYPE -> R.string.call_blocked
    CallLog.Calls.VOICEMAIL_TYPE -> R.string.call_voicemail
    else -> R.string.call_incoming
}

@Composable
fun RecentsTab(vm: DialerViewModel, listState: LazyListState, onOpenContact: (Long) -> Unit) {
    val context = LocalContext.current
    val recents by vm.recents.collectAsState()
    val index by vm.contactIndex.collectAsState()
    val perms by vm.perms.collectAsState()
    val callController = LocalCallController.current
    val messageController = LocalMessageController.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var sheetFor by remember { mutableStateOf<CallGroup?>(null) }
    var confirmDelete by remember { mutableStateOf<CallGroup?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var missedOnly by rememberSaveable { mutableStateOf(false) }

    val requestCallLog = rememberPermissionRequester(vm, arrayOf(Manifest.permission.READ_CALL_LOG))
    val requestWrite = rememberPermissionRequester(vm, arrayOf(Manifest.permission.WRITE_CALL_LOG))
    val deletedMsg = stringResource(R.string.recents_deleted)
    val deleteFailedMsg = stringResource(R.string.recents_delete_failed)
    val copiedMsg = stringResource(R.string.number_copied)

    LaunchedEffect(perms.callLog) {
        if (perms.callLog) {
            vm.markMissedRead()
            CallNotifications.cancelMissed(context)
        }
    }

    if (!perms.callLog) {
        MessageCard(stringResource(R.string.recents_permission_text), stringResource(R.string.action_grant), requestCallLog)
    } else if (recents.isEmpty()) {
        EmptyState(Icons.Default.History, stringResource(R.string.recents_empty))
    } else {
        val filtered = remember(recents, search, missedOnly, index) {
            val q = PhoneUtils.fold(search.trim())
            recents.mapNotNull { group ->
                val entries = if (missedOnly) group.entries.filter { it.type == CallLog.Calls.MISSED_TYPE } else group.entries
                if (entries.isEmpty()) return@mapNotNull null
                val shown = CallGroup(entries)
                val e = shown.first
                val contact = index.findByNumber(e.number)
                val title = contact?.name ?: e.cachedName.orEmpty()
                val matchesSearch = q.isEmpty() ||
                        PhoneUtils.fold(title).contains(q) ||
                        PhoneUtils.fold(Format.number(e.number)).contains(q) ||
                        PhoneUtils.fold(e.number).contains(q)
                shown.takeIf { matchesSearch }
            }
        }
        Column(Modifier.fillMaxSize()) {
            TextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.recents_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = missedOnly,
                    onClick = { missedOnly = !missedOnly },
                    label = { Text(stringResource(R.string.recents_missed_filter)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                )
            }
            if (filtered.isEmpty()) {
                EmptyState(Icons.Default.History, stringResource(R.string.recents_no_matches), modifier = Modifier.weight(1f))
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.key }) { group ->
                        val e = group.first
                        val contact = index.findByNumber(e.number)
                        val unknown = PhoneUtils.isUnknown(e.number)
                        val title = contact?.name ?: e.cachedName
                        ?: if (unknown) stringResource(R.string.recents_private) else Format.number(e.number)
                        val missed = e.type == CallLog.Calls.MISSED_TYPE

                        Card(
                            onClick = { if (!unknown) callController.call(e.number) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(contact?.name ?: e.cachedName, contact?.thumbUri ?: e.cachedPhoto, 48.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (group.count > 1) "$title (${group.count})" else title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            typeIcon(e.type),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(typeLabel(e.type)) + " • " + Format.callTime(context, e.date),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { sheetFor = group }) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = stringResource(R.string.action_details),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        sheetFor?.let { group ->
            val e = group.first
            val contact = index.findByNumber(e.number)
            CallDetailsSheet(
                group = group,
                contact = contact,
                onDismiss = { sheetFor = null },
                onCall = {
                    sheetFor = null
                    callController.call(e.number)
                },
                onMessage = {
                    sheetFor = null
                    messageController.open(e.number)
                },
                onContact = {
                    sheetFor = null
                    if (contact != null) onOpenContact(contact.id) else Intents.addContact(context, e.number)
                },
                onCopy = {
                    sheetFor = null
                    Intents.copyNumber(context, e.number)
                    scope.launch { snackbar.showSnackbar(copiedMsg) }
                },
                onDelete = {
                    sheetFor = null
                    if (perms.writeCallLog) confirmDelete = group else requestWrite()
                },
            )
        }

        confirmDelete?.let { group ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text(stringResource(R.string.recents_delete_title)) },
                text = { Text(stringResource(R.string.recents_delete_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = null
                        vm.deleteCalls(group) { ok ->
                            scope.launch { snackbar.showSnackbar(if (ok) deletedMsg else deleteFailedMsg) }
                        }
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallDetailsSheet(
    group: CallGroup,
    contact: Contact?,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onContact: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val e = group.first
    val unknown = PhoneUtils.isUnknown(e.number)
    val name = contact?.name ?: e.cachedName
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Avatar(name, contact?.thumbUri ?: e.cachedPhoto, 56.dp) },
                headlineContent = {
                    Text(
                        text = name ?: if (unknown) stringResource(R.string.recents_private) else Format.ltr(Format.number(e.number)),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                supportingContent = {
                    if (name != null && !unknown) Text(Format.ltr(Format.number(e.number)), style = MaterialTheme.typography.bodyMedium)
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            for (entry in group.entries.take(8)) {
                CallEntryRow(entry, context)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (!unknown) {
                SheetAction(Icons.Default.Call, R.string.action_call, onCall)
                SheetAction(Icons.AutoMirrored.Filled.Message, R.string.action_message, onMessage)
                if (contact != null) {
                    SheetAction(Icons.Default.Person, R.string.action_details, onContact)
                } else {
                    SheetAction(Icons.Default.PersonAdd, R.string.action_add_contact, onContact)
                }
                SheetAction(Icons.Default.ContentCopy, R.string.action_copy, onCopy)
            }
            SheetAction(Icons.Default.Delete, R.string.action_delete, onDelete)
        }
    }
}

@Composable
private fun CallEntryRow(entry: CallLogEntry, context: android.content.Context) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                typeIcon(entry.type),
                contentDescription = null,
                tint = if (entry.type == CallLog.Calls.MISSED_TYPE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = { Text(stringResource(typeLabel(entry.type)), style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(Format.callTime(context, entry.date), style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            if (entry.durationSec > 0) Text(Format.duration(entry.durationSec), style = MaterialTheme.typography.labelMedium)
        },
    )
}

@Composable
private fun SheetAction(icon: ImageVector, label: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) },
    )
}