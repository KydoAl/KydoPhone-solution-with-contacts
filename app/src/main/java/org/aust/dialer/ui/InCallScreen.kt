package org.aust.dialer.ui

import android.graphics.Bitmap
import android.telecom.CallAudioState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import org.aust.dialer.data.PhotoCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import org.aust.dialer.R
import org.aust.dialer.core.Format
import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.telecom.CallInfo
import org.aust.dialer.telecom.CallManager
import org.aust.dialer.telecom.CallPlacer
import org.aust.dialer.telecom.CallStatus
import org.aust.dialer.telecom.statusRes
import org.aust.dialer.ui.theme.CallGreen
import org.aust.dialer.ui.theme.CallRed

private fun pickPrimary(calls: List<CallInfo>): CallInfo? =
    calls.firstOrNull { it.status == CallStatus.RINGING }
        ?: calls.firstOrNull { it.status != CallStatus.HOLDING && it.status != CallStatus.DISCONNECTED }
        ?: calls.firstOrNull { it.status != CallStatus.DISCONNECTED }
        ?: calls.firstOrNull()

@Composable
private fun displayName(call: CallInfo): String = when {
    call.isConference -> stringResource(R.string.incall_conference)
    call.name != null -> call.name
    PhoneUtils.isUnknown(call.number) -> stringResource(R.string.recents_private)
    else -> Format.ltr(Format.number(call.number))
}

@Composable
fun InCallScreen(onFinish: () -> Unit, onAddCall: () -> Unit, onProximity: (Boolean) -> Unit) {
    val calls by CallManager.calls.collectAsState()
    val audio by CallManager.audio.collectAsState()
    var showKeypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var routeMenu by remember { mutableStateOf(false) }

    val primary = pickPrimary(calls)
    val secondary = calls.firstOrNull { it.id != primary?.id && it.status != CallStatus.DISCONNECTED }

    // Close when every call (including the short "call ended" display) is gone.
    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) {
            delay(400)
            if (CallManager.calls.value.isEmpty()) onFinish()
        }
    }

    // Screen off at the ear: only while a call is connecting/active and audio is not on speaker / Bluetooth.
    val wantProximity = primary != null &&
        (primary.status == CallStatus.ACTIVE || primary.status == CallStatus.DIALING || primary.status == CallStatus.CONNECTING) &&
        (audio.route == CallAudioState.ROUTE_EARPIECE || audio.route == CallAudioState.ROUTE_WIRED_HEADSET)
    LaunchedEffect(wantProximity) { onProximity(wantProximity) }
    DisposableEffect(Unit) { onDispose { onProximity(false) } }

    // Lets the user pick a SIM if Telecom could not decide by itself.
    if (primary != null && primary.status == CallStatus.SELECT_ACCOUNT) {
        val context = LocalContext.current
        val accounts = remember { CallPlacer.simAccounts(context) }
        AlertDialog(
            onDismissRequest = { CallManager.hangup(primary.id) },
            title = { Text(stringResource(R.string.incall_choose_sim)) },
            text = {
                Column {
                    accounts.forEachIndexed { i, a ->
                        Text(
                            simLabel(a, i),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { CallManager.selectAccount(primary.id, a.handle) }.padding(vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { CallManager.hangup(primary.id) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (primary == null) return@Surface
        val ctx = LocalContext.current
        val photo by produceState<Bitmap?>(initialValue = null, primary.photoUri) {
            val uri = primary.photoUri
            value = if (uri == null) null else withContext(Dispatchers.IO) { PhotoCache.load(ctx, uri, 1080) }
        }
        val hasPhoto = photo != null
        val base = MaterialTheme.colorScheme
        // Over a photo the text must be light and the control surfaces translucent.
        val scheme = if (hasPhoto) {
            base.copy(
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFFE6E6E6),
                surfaceContainerHigh = Color(0x66000000),
            )
        } else {
            base
        }
        MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize()) {
            val bmp = photo
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x22000000), Color(0x55000000), Color(0xE6000000))),
                    ),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(base.primaryContainer.copy(alpha = 0.55f), base.surface)),
                    ),
                )
            }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(top = 32.dp))
            if (!hasPhoto) Avatar(primary.name, primary.photoUri, 168.dp)
            Text(
                displayName(primary),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (primary.name != null && !PhoneUtils.isUnknown(primary.number)) {
                Text(
                    Format.ltr(Format.number(primary.number)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusLine(primary)

            if (secondary != null) {
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                        .clickable { CallManager.unhold(secondary.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(secondary.name, secondary.photoUri, 36.dp)
                    Text(
                        stringResource(R.string.incall_other_call, displayName(secondary)),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Box(Modifier.weight(1f))

            if (primary.status == CallStatus.RINGING) {
                RingingButtons(primary)
            } else {
                if (showKeypad) {
                    Text(
                        typed,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        InCallKeypad(onKey = { ch ->
                            typed += ch
                            CallManager.sendDtmf(primary.id, ch)
                        })
                    }
                    TextButton(onClick = { showKeypad = false }) { Text(stringResource(R.string.incall_hide_keypad)) }
                } else {
                    val controls = mutableListOf<@Composable () -> Unit>()
                    controls += {
                        CallControl(
                            icon = if (audio.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = stringResource(R.string.incall_mute),
                            active = audio.muted,
                        ) { CallManager.setMuted(!audio.muted) }
                    }
                    controls += {
                        CallControl(Icons.Default.Dialpad, stringResource(R.string.incall_keypad), false) { showKeypad = true }
                    }
                    controls += {
                        CallControl(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = stringResource(R.string.incall_speaker),
                            active = audio.route == CallAudioState.ROUTE_SPEAKER,
                        ) {
                            CallManager.setAudioRoute(
                                if (audio.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER,
                            )
                        }
                    }
                    val hasBluetooth = (audio.supportedMask and CallAudioState.ROUTE_BLUETOOTH) != 0
                    val hasHeadset = (audio.supportedMask and CallAudioState.ROUTE_WIRED_HEADSET) != 0
                    if (hasBluetooth || hasHeadset) {
                        controls += {
                            Box {
                                CallControl(
                                    icon = if (audio.route == CallAudioState.ROUTE_BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Headset,
                                    label = stringResource(R.string.incall_audio),
                                    active = audio.route == CallAudioState.ROUTE_BLUETOOTH || audio.route == CallAudioState.ROUTE_WIRED_HEADSET,
                                ) { routeMenu = true }
                                DropdownMenu(expanded = routeMenu, onDismissRequest = { routeMenu = false }) {
                                    RouteItem(Icons.Default.PhoneAndroid, R.string.route_earpiece, audio.supportedMask, CallAudioState.ROUTE_EARPIECE) {
                                        routeMenu = false
                                        CallManager.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                                    }
                                    RouteItem(Icons.Default.Headset, R.string.route_wired, audio.supportedMask, CallAudioState.ROUTE_WIRED_HEADSET) {
                                        routeMenu = false
                                        CallManager.setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET)
                                    }
                                    RouteItem(Icons.Default.Bluetooth, R.string.route_bluetooth, audio.supportedMask, CallAudioState.ROUTE_BLUETOOTH) {
                                        routeMenu = false
                                        CallManager.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                                    }
                                    RouteItem(Icons.AutoMirrored.Filled.VolumeUp, R.string.route_speaker, audio.supportedMask, CallAudioState.ROUTE_SPEAKER) {
                                        routeMenu = false
                                        CallManager.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                                    }
                                }
                            }
                        }
                    }
                    // Only offer what the device / network actually supports for this call.
                    if (primary.supportsHold) {
                        val holding = primary.status == CallStatus.HOLDING
                        controls += {
                            CallControl(
                                icon = if (holding) Icons.Default.PlayArrow else Icons.Default.Pause,
                                label = stringResource(if (holding) R.string.incall_resume else R.string.incall_hold),
                                active = holding,
                                enabled = holding || primary.canHoldNow,
                            ) { if (holding) CallManager.unhold(primary.id) else CallManager.hold(primary.id) }
                        }
                    }
                    if (primary.supportsHold && secondary == null &&
                        (primary.status == CallStatus.ACTIVE || primary.status == CallStatus.HOLDING)
                    ) {
                        controls += { CallControl(Icons.Default.Add, stringResource(R.string.incall_add), false) { onAddCall() } }
                    }
                    if (primary.canMerge) {
                        controls += { CallControl(Icons.Default.CallMerge, stringResource(R.string.incall_merge), false) { CallManager.merge(primary.id) } }
                    }
                    for (row in controls.chunked(3)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { it() }
                        }
                    }
                }
                EndCallButton(enabled = primary.status != CallStatus.DISCONNECTED) { CallManager.hangup(primary.id) }
            }
        }
        }
        }
    }
}

@Composable
private fun StatusLine(call: CallInfo) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.status) {
        while (call.status == CallStatus.ACTIVE) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text = if (call.status == CallStatus.ACTIVE && call.connectTimeMillis > 0) {
        Format.duration((now - call.connectTimeMillis) / 1000)
    } else {
        stringResource(call.statusRes())
    }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CallControl(icon: ImageVector, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconToggleButton(
            checked = active,
            onCheckedChange = { onClick() },
            enabled = enabled,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RouteItem(icon: ImageVector, label: Int, mask: Int, route: Int, onClick: () -> Unit) {
    if ((mask and route) == 0) return
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun EndCallButton(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.incall_end)
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(72.dp).semantics { contentDescription = label },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = CallRed, contentColor = Color.White),
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun RingingButtons(call: CallInfo) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SwipeCallButton(
                color = CallRed,
                icon = Icons.Default.CallEnd,
                label = stringResource(R.string.incall_decline),
                hint = stringResource(R.string.incall_swipe_down),
                direction = 1,
                onTrigger = { CallManager.reject(call.id) },
            )
            SwipeCallButton(
                color = CallGreen,
                icon = Icons.Default.Call,
                label = stringResource(R.string.incall_answer),
                hint = stringResource(R.string.incall_swipe_up),
                direction = -1,
                onTrigger = { CallManager.answer(call.id) },
            )
        }
    }
}

/**
 * Round call button that can be tapped OR dragged: up to answer, down to decline. Releasing before the
 * threshold springs it back. Tapping still works (and is what TalkBack uses).
 */
@Composable
private fun SwipeCallButton(
    color: Color,
    icon: ImageVector,
    label: String,
    hint: String,
    direction: Int,
    onTrigger: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .pointerInput(direction) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(offsetY.value) >= threshold * 0.85f) {
                                onTrigger()
                            } else {
                                scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                        onDragCancel = { scope.launch { offsetY.animateTo(0f) } },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val next = offsetY.value + dragAmount
                            val bounded = if (direction < 0) next.coerceIn(-threshold, 0f) else next.coerceIn(0f, threshold)
                            scope.launch { offsetY.snapTo(bounded) }
                        },
                    )
                }
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .size(80.dp)
                .clip(CircleShape)
                .background(color)
                .semantics { contentDescription = label }
                .clickable(onClickLabel = label) { onTrigger() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Text(label, modifier = Modifier.padding(top = 8.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val KEYPAD = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
    listOf('*', '0', '#'),
)

@Composable
private fun InCallKeypad(onKey: (Char) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        for (row in KEYPAD) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (ch in row) {
                    Box(
                        Modifier
                            .padding(4.dp)
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .clickable { onKey(ch) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(ch.toString(), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
