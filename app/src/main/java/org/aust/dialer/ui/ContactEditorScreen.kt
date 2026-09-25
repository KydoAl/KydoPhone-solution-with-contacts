package org.aust.dialer.ui

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.data.ContactDraft

@Composable
private fun ContactField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = iconTint) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditorScreen(vm: DialerViewModel, contactId: Long?, onBack: () -> Unit, onSaved: (Long?) -> Unit) {
    val index by vm.contactIndex.collectAsState()
    val perms by vm.perms.collectAsState()
    val contact = contactId?.let(index::findById)
    val editing = contactId != null
    val requestWrite = rememberPermissionRequester(vm, arrayOf(Manifest.permission.WRITE_CONTACTS))
    val snackbar = LocalSnackbar.current
    val nameRequired = stringResource(R.string.contact_name_required)
    val numberRequired = stringResource(R.string.contact_number_required)
    val saveFailed = stringResource(R.string.contact_save_failed)

    var given by rememberSaveable(contactId) { mutableStateOf(contact?.givenName.orEmpty().ifEmpty { contact?.name.orEmpty() }) }
    var middle by rememberSaveable(contactId) { mutableStateOf(contact?.middleName.orEmpty()) }
    var family by rememberSaveable(contactId) { mutableStateOf(contact?.familyName.orEmpty()) }
    var suffix by rememberSaveable(contactId) { mutableStateOf(contact?.suffix.orEmpty()) }
    var company by rememberSaveable(contactId) { mutableStateOf(contact?.company.orEmpty()) }
    val numbers = remember(contactId, contact?.id) { mutableStateListOf<String>().apply { if (contact != null) addAll(contact.numbers.map { it.number }); if (isEmpty()) add("") } }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(contact?.id) {
        if (contact != null && editing) {
            given = contact.givenName.ifEmpty { contact.name }
            middle = contact.middleName
            family = contact.familyName
            suffix = contact.suffix
            company = contact.company
            numbers.clear(); numbers.addAll(contact.numbers.map { it.number }); if (numbers.isEmpty()) numbers.add("")
        }
    }

    fun save() {
        val clean = numbers.map(String::trim).filter(String::isNotEmpty).distinct()
        val draft = ContactDraft(given, middle, family, suffix, company, clean)
        when {
            !perms.writeContacts -> requestWrite()
            draft.displayName().isBlank() -> error = nameRequired
            clean.isEmpty() -> error = numberRequired
            saving -> Unit
            else -> {
                saving = true; error = null
                if (contact != null) vm.updateContact(contact, draft) { ok -> saving = false; if (ok) onSaved(contact.id) else error = saveFailed }
                else vm.createContact(draft) { id -> saving = false; if (id != null) onSaved(id) else error = saveFailed }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(if (editing) R.string.contact_edit else R.string.contact_new)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            actions = { TextButton(onClick = ::save, enabled = !saving) { Text(stringResource(R.string.action_save)) } },
        ) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!perms.writeContacts) MessageCard(stringResource(R.string.contacts_write_permission_text), stringResource(R.string.action_grant), requestWrite)
                    ContactField(
                        value = given,
                        onValueChange = { given = it },
                        label = stringResource(R.string.contact_first_name),
                        icon = Icons.Default.Person,
                        iconTint = MaterialTheme.colorScheme.primary,
                    )
                    ContactField(
                        value = middle,
                        onValueChange = { middle = it },
                        label = stringResource(R.string.contact_middle_name),
                        icon = Icons.Default.Person,
                        iconTint = MaterialTheme.colorScheme.secondary,
                    )
                    ContactField(
                        value = family,
                        onValueChange = { family = it },
                        label = stringResource(R.string.contact_last_name),
                        icon = Icons.Default.Person,
                        iconTint = MaterialTheme.colorScheme.primary,
                    )
                    ContactField(
                        value = suffix,
                        onValueChange = { suffix = it },
                        label = stringResource(R.string.contact_suffix),
                        icon = Icons.Default.Badge,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                    )
                    ContactField(
                        value = company,
                        onValueChange = { company = it },
                        label = stringResource(R.string.contact_company),
                        icon = Icons.Default.Business,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                    )
                    AnimatedVisibility(visible = error != null) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.contact_numbers), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            itemsIndexed(numbers) { i, number ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        number,
                        { numbers[i] = it },
                        Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.contact_phone, i + 1)) },
                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    IconButton(onClick = { if (numbers.size > 1) numbers.removeAt(i) }, enabled = numbers.size > 1) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.contact_remove_number)) }
                }
            }
            item {
                OutlinedButton(onClick = { numbers.add("") }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Icon(Icons.Default.Add, contentDescription = null); Text(stringResource(R.string.contact_add_number), Modifier.padding(start = 8.dp)) }
            }
        }
    }
}
