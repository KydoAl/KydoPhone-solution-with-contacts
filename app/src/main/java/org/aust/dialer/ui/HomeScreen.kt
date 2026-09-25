package org.aust.dialer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavHostController
import org.aust.dialer.DialerViewModel
import org.aust.dialer.SmsViewModel
import org.aust.dialer.R
import org.aust.dialer.telecom.RoleUtils

const val TAB_FAVORITES = 0
const val TAB_RECENTS = 1
const val TAB_MESSAGES = 2
const val TAB_CONTACTS = 3
const val TAB_DIALPAD = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: DialerViewModel,
    smsVm: SmsViewModel,
    tab: Int,
    onTabChange: (Int) -> Unit,
    number: TextFieldValue,
    onNumberChange: (TextFieldValue) -> Unit,
    nav: NavHostController,
) {
    val context = LocalContext.current
    val perms by vm.perms.collectAsState()
    val recentsState = rememberLazyListState()
    val contactsState = rememberLazyListState()
    val messagesState = rememberLazyListState()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshPermissions()
    }

    val tabs = listOf(
        Triple(TAB_FAVORITES, Icons.Default.Star, R.string.tab_favorites),
        Triple(TAB_RECENTS, Icons.Default.History, R.string.tab_recents),
        Triple(TAB_MESSAGES, Icons.AutoMirrored.Filled.Message, R.string.tab_messages),
        Triple(TAB_CONTACTS, Icons.Default.Contacts, R.string.tab_contacts),
        Triple(TAB_DIALPAD, Icons.Default.Dialpad, R.string.tab_dialpad),
    )
    val titleRes = tabs.first { it.first == tab }.third

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                actions = {
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                for ((id, icon, label) in tabs) {
                    NavigationBarItem(
                        selected = tab == id,
                        onClick = { onTabChange(id) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == TAB_MESSAGES) {
                FloatingActionButton(onClick = { nav.navigate("compose") }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.sms_new))
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!perms.isDefaultDialer) {
                MessageCard(
                    text = stringResource(R.string.banner_default_text),
                    actionLabel = stringResource(R.string.setup_default_button),
                    onAction = { RoleUtils.requestIntent(context)?.let { roleLauncher.launch(it) } },
                )
            }
            val openContact: (Long) -> Unit = { nav.navigate("contact/$it") }
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(120)) togetherWith fadeOut(animationSpec = tween(90))
                },
                label = "home_tab_transition",
            ) { targetTab ->
                when (targetTab) {
                    TAB_FAVORITES -> FavoritesTab(vm, openContact)
                    TAB_RECENTS -> RecentsTab(vm, recentsState, openContact)
                    TAB_MESSAGES -> org.aust.dialer.ui.sms.MessagesTab(vm, smsVm, messagesState) { address ->
                        nav.navigate("thread/" + android.net.Uri.encode(address))
                    }
                    TAB_CONTACTS -> ContactsTab(vm, contactsState, openContact, onNewContact = { nav.navigate("contact/new") })
                    else -> DialPadTab(vm, number, onNumberChange, onOpenSpeedDial = { nav.navigate("speeddial") })
                }
            }
        }
    }
}
