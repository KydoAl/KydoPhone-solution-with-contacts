package org.aust.dialer.ui

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.core.Format
import org.aust.dialer.core.Intents

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactDetailScreen(vm: DialerViewModel, contactId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val index by vm.contactIndex.collectAsState()
    val perms by vm.perms.collectAsState()
    val callController = LocalCallController.current
    val messageController = LocalMessageController.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val contact = index.findById(contactId)

    var pendingStar by remember { mutableStateOf<Boolean?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    val requestWrite = rememberPermissionRequester(vm, arrayOf(Manifest.permission.WRITE_CONTACTS))
    val failedMsg = stringResource(R.string.error_generic)
    val copiedMsg = stringResource(R.string.number_copied)

    // Star / un-star needs WRITE_CONTACTS, requested only at the moment the user taps the star.
    LaunchedEffect(perms.writeContacts, pendingStar) {
        val target = pendingStar
        if (target != null && perms.writeContacts) {
            pendingStar = null
            vm.setStarred(contactId, target) { ok ->
                if (!ok) scope.launch { snackbar.showSnackbar(failedMsg) }
            }
        }
    }

    if (showDelete && contact != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.contact_delete_title)) },
            text = { Text(stringResource(R.string.contact_delete_text, contact.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDelete = false
                    vm.deleteContact(contact.id) { ok ->
                        if (ok) onBack() else scope.launch { snackbar.showSnackbar(failedMsg) }
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (contact != null) {
                        IconButton(onClick = {
                            val target = !contact.starred
                            if (perms.writeContacts) {
                                vm.setStarred(contact.id, target) { ok ->
                                    if (!ok) scope.launch { snackbar.showSnackbar(failedMsg) }
                                }
                            } else {
                                pendingStar = target
                                requestWrite()
                            }
                        }) {
                            Icon(
                                if (contact.starred) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(
                                    if (contact.starred) R.string.contacts_remove_favorite else R.string.contacts_add_favorite,
                                ),
                                tint = if (contact.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.contact_edit))
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.contact_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (contact == null) {
            EmptyState(Icons.Default.Star, stringResource(R.string.contact_not_found), modifier = Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(contact.name, contact.photoUri ?: contact.thumbUri, 112.dp)
                        Text(
                            contact.name,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                item {
                    val details = listOf(
                        R.string.contact_company to contact.company,
                        R.string.contact_suffix to contact.suffix,
                    ).filter { it.second.isNotBlank() }
                    if (details.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            details.forEach { (label, value) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        if (label == R.string.contact_company) Icons.Default.Business else Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 32.dp, bottom = 8.dp))
                            }
                        }
                    }
                    contact.primaryNumber?.let { number ->
                        val waIntent = remember(number) {
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/" + org.aust.dialer.core.PhoneUtils.digitsOnly(number))).apply { setPackage("com.whatsapp") }
                        }
                        val wabIntent = remember(number) {
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/" + org.aust.dialer.core.PhoneUtils.digitsOnly(number))).apply { setPackage("com.whatsapp.w4b") }
                        }
                        val pm = context.packageManager
                        val hasWhatsApp = remember(number) {
                            runCatching { pm.getPackageInfo("com.whatsapp", 0); true }.getOrElse { runCatching { pm.getPackageInfo("com.whatsapp.w4b", 0); true }.getOrDefault(false) }
                        }
                        if (hasWhatsApp) {
                            IconButton(onClick = {
                                try { context.startActivity(if (runCatching { pm.getPackageInfo("com.whatsapp", 0); true }.getOrDefault(false)) waIntent else wabIntent) } catch (_: Exception) { }
                            }) {
                                Icon(Icons.Default.Apps, contentDescription = stringResource(R.string.contact_whatsapp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                items(contact.numbers, key = { it.number }) { entry ->
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClickLabel = stringResource(R.string.action_call),
                            onLongClickLabel = stringResource(R.string.action_copy),
                            onClick = { callController.call(entry.number) },
                            onLongClick = {
                                Intents.copyNumber(context, entry.number)
                                scope.launch { snackbar.showSnackbar(copiedMsg) }
                            },
                        ),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(Format.ltr(Format.number(entry.number))) },
                        supportingContent = { if (entry.typeLabel.isNotEmpty()) Text(entry.typeLabel) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                IconButton(onClick = { messageController.open(entry.number) }) {
                                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = stringResource(R.string.action_message))
                                }
                                IconButton(onClick = { callController.call(entry.number) }) {
                                    Icon(Icons.Default.Call, contentDescription = stringResource(R.string.action_call))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
