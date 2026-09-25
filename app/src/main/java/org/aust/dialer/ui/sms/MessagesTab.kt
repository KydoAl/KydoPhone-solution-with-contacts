package org.aust.dialer.ui.sms

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.SmsViewModel
import org.aust.dialer.core.Format
import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.ui.Avatar
import org.aust.dialer.ui.EmptyState
import org.aust.dialer.ui.MessageCard
import org.aust.dialer.ui.rememberPermissionRequester

@Composable
fun MessagesTab(dialerVm: DialerViewModel, smsVm: SmsViewModel, listState: LazyListState, onOpenThread: (String) -> Unit) {
    val context = LocalContext.current
    val perms by smsVm.perms.collectAsState()
    val conversations by smsVm.conversations.collectAsState()
    val pinned by smsVm.pinnedThreads.collectAsState()
    val blocked by smsVm.blockedSenders.collectAsState()
    val index by dialerVm.contactIndex.collectAsState()
    val request = rememberPermissionRequester(smsVm::refreshPermissions, arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS))
    var search by rememberSaveable { mutableStateOf("") }
    var showBlocked by rememberSaveable { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }

    if (!perms.readSms) { MessageCard(stringResource(R.string.sms_permission_text), stringResource(R.string.action_grant), request); return }

    val ordered = remember(conversations, pinned, blocked, search, showBlocked, index) {
        val q = PhoneUtils.fold(search.trim())
        conversations.asSequence()
            .filter { showBlocked || PhoneUtils.senderKey(it.address) !in blocked }
            .filter {
                if (q.isEmpty()) true else {
                    val c = index.findByNumber(it.address)
                    val name = c?.name.orEmpty()
                    PhoneUtils.fold(name).contains(q) || PhoneUtils.fold(it.address).contains(q) || PhoneUtils.fold(it.snippet).contains(q)
                }
            }
            .sortedWith(compareByDescending<org.aust.dialer.sms.Conversation> { it.threadId in pinned }.thenByDescending { it.date })
            .toList()
    }

    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = search, onValueChange = { search = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.sms_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { filterMenu = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.sms_filter))
                    }
                    DropdownMenu(filterMenu, { filterMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (showBlocked) R.string.sms_hide_blocked else R.string.sms_show_blocked)) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                            onClick = { showBlocked = !showBlocked; filterMenu = false },
                        )
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                modifier = Modifier.weight(1f),
            )
        }
        if (conversations.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.Message, stringResource(R.string.sms_empty), stringResource(R.string.sms_empty_hint))
        } else if (ordered.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.Message, stringResource(R.string.sms_no_matches))
        } else {
            LazyColumn(state = listState) {
                items(ordered, key = { it.threadId }) { conv ->
                    val contact = index.findByNumber(conv.address)
                    val name = contact?.name ?: conv.address
                    var menuOpen by remember { mutableStateOf(false) }
                    val isPinned = conv.threadId in pinned
                    val isBlocked = PhoneUtils.senderKey(conv.address) in blocked
                    ListItem(
                        modifier = Modifier.clickable { onOpenThread(conv.address) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Avatar(contact?.name ?: conv.address, contact?.thumbUri, 44.dp) },
                        headlineContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text(name, maxLines = 1, fontWeight = if (conv.hasUnread) FontWeight.Bold else FontWeight.Normal); if (isBlocked) { Icon(Icons.Default.Block, null, Modifier.padding(start = 6.dp).size(15.dp), tint = MaterialTheme.colorScheme.error) } } },
                        supportingContent = { val prefix = if (conv.outgoing) stringResource(R.string.sms_you_prefix) else ""; Text(prefix + conv.snippet, maxLines = 1, fontWeight = if (conv.hasUnread) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isPinned) Icon(Icons.Default.PushPin, stringResource(R.string.sms_unpin), Modifier.size(16.dp))
                                Text(Format.callTime(context, conv.date), style = MaterialTheme.typography.labelSmall)
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                                DropdownMenu(menuOpen, { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(if (isPinned) R.string.sms_unpin else R.string.sms_pin)) }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menuOpen = false; smsVm.setThreadPinned(conv.threadId, !isPinned) })
                                    DropdownMenuItem(text = { Text(stringResource(if (isBlocked) R.string.sms_unblock_sender else R.string.sms_block_sender)) }, leadingIcon = { Icon(Icons.Default.Block, null) }, onClick = { menuOpen = false; smsVm.setSenderBlocked(conv.address, !isBlocked) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sms_notification_settings)) }, leadingIcon = { Icon(Icons.Default.Notifications, null) }, onClick = { menuOpen = false; org.aust.dialer.sms.SmsNotifications.openConversationNotificationSettings(context, conv.threadId, name) })
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
