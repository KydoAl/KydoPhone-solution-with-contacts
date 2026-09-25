package org.aust.dialer.ui

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.core.Format
import org.aust.dialer.core.Intents
import org.aust.dialer.data.Contact

@Composable
fun ContactsTab(vm: DialerViewModel, listState: LazyListState, onOpenContact: (Long) -> Unit, onNewContact: () -> Unit) {
    val context = LocalContext.current
    val index by vm.contactIndex.collectAsState()
    val perms by vm.perms.collectAsState()
    val callController = LocalCallController.current
    var query by rememberSaveable { mutableStateOf("") }
    val request = rememberPermissionRequester(vm, arrayOf(Manifest.permission.READ_CONTACTS))

    if (!perms.contacts) {
        MessageCard(stringResource(R.string.contacts_permission_text), stringResource(R.string.action_grant), request)
        return
    }

    val results = remember(index, query) { if (query.isBlank()) index.contacts else index.search(query) }
    val grouped = remember(results) { results.groupBy { it.initial } }

    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (index.contacts.isEmpty()) {
            EmptyState(Icons.Default.Contacts, stringResource(R.string.contacts_empty))
        } else {
            LazyColumn(state = listState) {
                item(key = "new") {
                    ListItem(
                        modifier = Modifier.clickable { onNewContact() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(stringResource(R.string.contact_new), color = MaterialTheme.colorScheme.primary) },
                    )
                }
                grouped.forEach { (letter, list) ->
                    item(key = "h$letter") {
                        Text(
                            letter,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.id }) { c ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenContact(c.id) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Avatar(c.name, c.thumbUri, 44.dp) },
                            headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                c.primaryNumber?.let { Text(Format.ltr(Format.number(it)), maxLines = 1) }
                            },
                            trailingContent = {
                                c.primaryNumber?.let { n ->
                                    IconButton(onClick = { callController.call(n) }) {
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesTab(vm: DialerViewModel, onOpenContact: (Long) -> Unit) {
    val index by vm.contactIndex.collectAsState()
    val perms by vm.perms.collectAsState()
    val callController = LocalCallController.current
    val request = rememberPermissionRequester(vm, arrayOf(Manifest.permission.READ_CONTACTS))

    if (!perms.contacts) {
        MessageCard(stringResource(R.string.contacts_permission_text), stringResource(R.string.action_grant), request)
        return
    }
    val favorites = index.favorites
    if (favorites.isEmpty()) {
        EmptyState(Icons.Default.Star, stringResource(R.string.favorites_empty), stringResource(R.string.favorites_empty_hint))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(favorites, key = { it.id }) { c ->
            FavoriteCell(
                c,
                onClick = { c.primaryNumber?.let { callController.call(it) } },
                onLongClick = { onOpenContact(c.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCell(c: Contact, onClick: () -> Unit, onLongClick: () -> Unit) {
    val callLabel = stringResource(R.string.action_call)
    val detailsLabel = stringResource(R.string.action_details)
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = callLabel,
                onLongClickLabel = detailsLabel,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(c.name, c.thumbUri ?: c.photoUri, 72.dp)
        Text(
            c.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
