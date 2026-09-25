package org.aust.dialer.ui

import android.Manifest
import android.net.Uri
import android.telecom.PhoneAccountHandle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.aust.dialer.DialerViewModel
import org.aust.dialer.IntentRequest
import org.aust.dialer.R
import org.aust.dialer.SmsViewModel
import org.aust.dialer.core.Prefs
import org.aust.dialer.telecom.CallPlacer
import org.aust.dialer.telecom.SimAccount
import org.aust.dialer.ui.sms.ComposeScreen
import org.aust.dialer.ui.sms.ThreadScreen

@Composable
fun AppRoot(
    vm: DialerViewModel,
    smsVm: SmsViewModel,
    prefs: Prefs,
    request: IntentRequest?,
    onConsumed: () -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val controller = remember { CallController() }
    val messageController = remember { MessageController() }
    val nav = rememberNavController()

    var tab by rememberSaveable { mutableIntStateOf(TAB_RECENTS) }
    var number by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    fun backToMessages() {
        tab = TAB_MESSAGES
        nav.navigate("home") {
            popUpTo("home") { inclusive = false }
            launchSingleTop = true
        }
    }

    var pendingNumber by remember { mutableStateOf<String?>(null) }
    var simChoice by remember { mutableStateOf<Pair<String, List<SimAccount>>?>(null) }

    val errCall = stringResource(R.string.error_call_failed)
    val errPerm = stringResource(R.string.error_no_call_permission)

    fun show(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun place(n: String, handle: PhoneAccountHandle?) {
        when (CallPlacer.place(context, n, handle)) {
            CallPlacer.Result.OK, CallPlacer.Result.INVALID -> Unit
            CallPlacer.Result.NO_PERMISSION -> show(errPerm)
            CallPlacer.Result.ERROR -> show(errCall)
        }
    }

    fun proceed(n: String) {
        val accounts = CallPlacer.simAccounts(context)
        val def = CallPlacer.defaultAccount(context)
        if (accounts.size > 1 && def == null) {
            simChoice = n to accounts
        } else {
            place(n, def)
        }
    }

    val callPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.refreshPermissions()
        val n = pendingNumber
        pendingNumber = null
        if (n != null) {
            if (CallPlacer.hasCallPermission(context)) {
                proceed(n)
            } else {
                show(errPerm)
            }
        }
    }

    fun startCall(n: String) {
        if (n.isBlank()) return
        if (CallPlacer.hasCallPermission(context) && CallPlacer.hasPhoneStatePermission(context)) {
            proceed(n)
        } else {
            pendingNumber = n
            callPermLauncher.launch(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            )
        }
    }

    SideEffect { controller.impl = { n -> startCall(n) } }
    SideEffect {
        messageController.impl = { address ->
            nav.navigate("thread/${Uri.encode(address)}")
        }
    }

    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        r.tab?.let { tab = it }
        r.number?.let { number = TextFieldValue(it, TextRange(it.length)) }
        val route = nav.currentDestination?.route
        if (route == "contact/{id}" || route == "contact/new" || route == "contact/{id}/edit" ||
            route == "speeddial" || route == "settings" || route == "thread/{address}" || route == "compose"
        ) {
            nav.popBackStack("home", false)
        }
        r.callNumber?.let { startCall(it) }
        r.messageAddress?.let { messageController.open(it) }
        onConsumed()
    }

    CompositionLocalProvider(
        LocalCallController provides controller,
        LocalMessageController provides messageController,
        LocalSnackbar provides snackbar,
    ) {
        val slide = AnimatedContentTransitionScope.SlideDirection.Start
        val slideBack = AnimatedContentTransitionScope.SlideDirection.End

        NavHost(
            navController = nav,
            startDestination = if (prefs.setupDone) "home" else "setup",
            enterTransition = { slideIntoContainer(slide, tween(180)) + fadeIn(tween(180)) },
            exitTransition = { slideOutOfContainer(slide, tween(180), targetOffset = { it / 4 }) + fadeOut(tween(180)) },
            popEnterTransition = { slideIntoContainer(slideBack, tween(180), initialOffset = { it / 4 }) + fadeIn(tween(180)) },
            popExitTransition = { slideOutOfContainer(slideBack, tween(180)) + fadeOut(tween(180)) },
        ) {
            composable("setup") {
                SetupScreen(
                    vm = vm,
                    smsVm = smsVm,
                    onDone = {
                        prefs.setupDone = true
                        nav.navigate("home") { popUpTo("setup") { inclusive = true } }
                    }
                )
            }
            composable("home") {
                HomeScreen(
                    vm = vm,
                    smsVm = smsVm,
                    tab = tab,
                    onTabChange = { tab = it },
                    number = number,
                    onNumberChange = { number = it },
                    nav = nav
                )
            }
            composable("contact/new") {
                ContactEditorScreen(
                    vm = vm,
                    contactId = null,
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.popBackStack()
                        id?.let { nav.navigate("contact/$it") }
                    },
                )
            }
            composable(
                route = "contact/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                ContactDetailScreen(
                    vm = vm,
                    contactId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("contact/$id/edit") },
                )
            }
            composable(
                route = "contact/{id}/edit",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                ContactEditorScreen(
                    vm = vm,
                    contactId = id,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable("speeddial") {
                SpeedDialScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    smsVm = smsVm,
                    onBack = { nav.popBackStack() },
                    onSpeedDial = { nav.navigate("speeddial") }
                )
            }
            composable(
                route = "thread/{address}",
                arguments = listOf(navArgument("address") { type = NavType.StringType }),
            ) { entry ->
                val address = Uri.decode(entry.arguments?.getString("address").orEmpty())
                ThreadScreen(
                    dialerVm = vm,
                    smsVm = smsVm,
                    address = address,
                    onBack = ::backToMessages
                )
            }
            composable("compose") {
                ComposeScreen(
                    dialerVm = vm,
                    onBack = ::backToMessages,
                    onOpenThread = { address ->
                        nav.popBackStack()
                        nav.navigate("thread/${Uri.encode(address)}")
                    },
                )
            }
        }

        simChoice?.let { (n, accounts) ->
            SimPickerDialog(
                accounts = accounts,
                onPick = { account ->
                    simChoice = null
                    place(n, account.handle)
                },
                onDismiss = { simChoice = null },
            )
        }
    }
}