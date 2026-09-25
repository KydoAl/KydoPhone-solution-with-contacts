package org.aust.dialer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.SmsViewModel
import org.aust.dialer.telecom.RoleUtils
import androidx.compose.material3.NavigationBarDefaults

const val TAB_FAVORITES = 0
const val TAB_RECENTS = 1
const val TAB_MESSAGES = 2
const val TAB_CONTACTS = 3
const val TAB_DIALPAD = 4

private data class TabItem(
    val id: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelRes: Int
)

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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val tabs = listOf(
        TabItem(TAB_FAVORITES, Icons.Default.Star, Icons.Outlined.Star, R.string.tab_favorites),
        TabItem(TAB_RECENTS, Icons.Default.History, Icons.Outlined.History, R.string.tab_recents),
        TabItem(TAB_MESSAGES, Icons.AutoMirrored.Filled.Message, Icons.AutoMirrored.Outlined.Message, R.string.tab_messages),
        TabItem(TAB_CONTACTS, Icons.Default.Contacts, Icons.Outlined.Contacts, R.string.tab_contacts),
        TabItem(TAB_DIALPAD, Icons.Default.Dialpad, Icons.Outlined.Dialpad, R.string.tab_dialpad),
    )
    val activeTab = tabs.first { it.id == tab }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(activeTab.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                for (item in tabs) {
                    val isSelected = tab == item.id
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onTabChange(item.id) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = stringResource(item.labelRes)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(item.labelRes),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == TAB_MESSAGES) {
                FloatingActionButton(
                    onClick = { nav.navigate("compose") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.sms_new))
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(100))
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