package org.aust.dialer.ui.sms

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    if (!perms.readSms) {
        MessageCard(stringResource(R.string.sms_permission_text), stringResource(R.string.action_grant), request)
        return
    }

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

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.sms_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                        IconButton(onClick = { filterMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.sms_filter))
                        }
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (showBlocked) R.string.sms_hide_blocked else R.string.sms_show_blocked)) },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                onClick = {
                                    showBlocked = !showBlocked
                                    filterMenu = false
                                }
                            )
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
                modifier = Modifier.weight(1f)
            )
        }

        if (conversations.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.Message, stringResource(R.string.sms_empty), stringResource(R.string.sms_empty_hint))
        } else if (ordered.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.Message, stringResource(R.string.sms_no_matches))
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(ordered, key = { it.threadId }) { conv ->
                    val contact = index.findByNumber(conv.address)
                    val name = contact?.name ?: Format.number(conv.address)
                    var menuOpen by remember { mutableStateOf(false) }
                    val isPinned = conv.threadId in pinned
                    val isBlocked = PhoneUtils.senderKey(conv.address) in blocked

                    Card(
                        onClick = { onOpenThread(conv.address) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (conv.hasUnread)
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Avatar(contact?.name ?: conv.address, contact?.thumbUri, 48.dp)
                                if (conv.hasUnread) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (conv.hasUnread) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isBlocked) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Block,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (isPinned) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.PushPin,
                                            contentDescription = stringResource(R.string.sms_unpin),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                val prefix = if (conv.outgoing) stringResource(R.string.sms_you_prefix) else ""
                                Text(
                                    text = prefix + conv.snippet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (conv.hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (conv.hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Format.callTime(context, conv.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box {
                                    IconButton(
                                        onClick = { menuOpen = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.action_more),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (isPinned) R.string.sms_unpin else R.string.sms_pin)) },
                                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                smsVm.setThreadPinned(conv.threadId, !isPinned)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (isBlocked) R.string.sms_unblock_sender else R.string.sms_block_sender)) },
                                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                smsVm.setSenderBlocked(conv.address, !isBlocked)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sms_notification_settings)) },
                                            leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                org.aust.dialer.sms.SmsNotifications.openConversationNotificationSettings(context, conv.threadId, name)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}