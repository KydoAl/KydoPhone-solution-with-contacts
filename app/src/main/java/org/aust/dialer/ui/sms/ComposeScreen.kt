package org.aust.dialer.ui.sms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.core.Format
import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.ui.Avatar

/** Start a new conversation: type a number or pick a contact, then jump to its thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(dialerVm: DialerViewModel, onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val index by dialerVm.contactIndex.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(index, query) { if (query.isBlank()) index.contacts else index.search(query) }
    val manualNumber = remember(query) { PhoneUtils.sanitizeDialable(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.sms_to_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (manualNumber.length >= 3) {
                item(key = "manual") {
                    ListItem(
                        modifier = Modifier.clickable { onOpenThread(manualNumber) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                        headlineContent = { Text(Format.ltr(Format.number(manualNumber))) },
                        supportingContent = { Text(stringResource(R.string.sms_send_to_number)) },
                    )
                }
            }
            items(results, key = { it.id }) { c ->
                val number = c.primaryNumber
                if (number != null) {
                    ListItem(
                        modifier = Modifier.clickable { onOpenThread(number) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Avatar(c.name, c.thumbUri, 44.dp) },
                        headlineContent = { Text(c.name, maxLines = 1) },
                        supportingContent = { Text(Format.ltr(Format.number(number)), maxLines = 1) },
                    )
                }
            }
        }
    }
}
