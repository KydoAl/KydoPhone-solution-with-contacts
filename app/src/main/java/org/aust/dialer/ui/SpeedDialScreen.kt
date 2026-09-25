package org.aust.dialer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.core.Format
import org.aust.dialer.core.PhoneUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDialScreen(vm: DialerViewModel, onBack: () -> Unit) {
    val prefs = vm.prefs
    val speedDials by prefs.speedDials.collectAsState()
    var assigning by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.speeddial_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.speeddial_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items((1..9).toList(), key = { it }) { digit ->
                val entry = speedDials[digit]
                ListItem(
                    modifier = Modifier.clickable { assigning = digit },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(digit.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    },
                    headlineContent = {
                        if (entry != null) {
                            Text(entry.name ?: Format.ltr(Format.number(entry.number)), maxLines = 1)
                        } else {
                            Text(stringResource(R.string.speeddial_unassigned), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    supportingContent = {
                        if (entry != null && entry.name != null) Text(Format.ltr(Format.number(entry.number)), maxLines = 1)
                    },
                    trailingContent = {
                        if (entry != null) {
                            IconButton(onClick = { prefs.removeSpeedDial(digit) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.speeddial_remove))
                            }
                        }
                    },
                )
            }
        }
    }

    assigning?.let { digit ->
        AssignSheet(vm, digit, onDismiss = { assigning = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignSheet(vm: DialerViewModel, digit: Int, onDismiss: () -> Unit) {
    val index by vm.contactIndex.collectAsState()
    var manual by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val results = remember(index, query) { if (query.isBlank()) index.contacts else index.search(query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.speeddial_assign_title, digit), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                singleLine = true,
                label = { Text(stringResource(R.string.speeddial_enter_number)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Button(
                onClick = {
                    val n = PhoneUtils.sanitizeDialable(manual)
                    if (n.isNotEmpty()) {
                        vm.prefs.setSpeedDial(digit, n, null)
                        onDismiss()
                    }
                },
                enabled = PhoneUtils.sanitizeDialable(manual).isNotEmpty(),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(stringResource(R.string.action_assign)) }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.speeddial_choose_contact)) },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(results, key = { it.id }) { c ->
                    ListItem(
                        modifier = Modifier.clickable {
                            c.primaryNumber?.let { n ->
                                vm.prefs.setSpeedDial(digit, n, c.name)
                                onDismiss()
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Avatar(c.name, c.thumbUri, 40.dp) },
                        headlineContent = { Text(c.name, maxLines = 1) },
                        supportingContent = { c.primaryNumber?.let { Text(Format.ltr(Format.number(it)), maxLines = 1) } },
                    )
                }
            }
        }
    }
}
