package org.aust.dialer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.telecom.CallAudioState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.aust.dialer.core.Format
import org.aust.dialer.telecom.AudioInfo
import org.aust.dialer.telecom.CallInfo
import org.aust.dialer.telecom.CallManager
import org.aust.dialer.telecom.CallStatus
import org.aust.dialer.telecom.statusRes
import kotlin.math.roundToInt

// --- Helpers ---

suspend fun loadPhotoBitmap(context: Context, photoUriStr: String?): Bitmap? {
    if (photoUriStr.isNullOrEmpty()) return null
    return withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(photoUriStr)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }
}

// --- Main Composables ---

@Composable
fun InCallScreen(
    onFinish: () -> Unit,
    onProximity: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val calls by CallManager.calls.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val primaryCall = calls.firstOrNull()

    if (primaryCall == null) {
        onFinish()
        return
    }

    DisposableEffect(primaryCall.status) {
        onProximity(true)
        onDispose {
            onProximity(false)
        }
    }

    BackHandler {
        // Prevent accidental closes during call
    }

    InCallScreen(
        primaryCall = primaryCall,
        audio = audio,
        onAnswer = { CallManager.answer(primaryCall.id) },
        onReject = { CallManager.reject(primaryCall.id) },
        onHangup = { CallManager.hangup(primaryCall.id) },
        onToggleMute = { CallManager.setMuted(!audio.muted) },
        onToggleSpeaker = {
            val targetRoute = if (audio.route == CallAudioState.ROUTE_SPEAKER) {
                CallAudioState.ROUTE_EARPIECE
            } else {
                CallAudioState.ROUTE_SPEAKER
            }
            CallManager.setAudioRoute(targetRoute)
        },
        onToggleHold = {
            if (primaryCall.status == CallStatus.HOLDING) {
                CallManager.unhold(primaryCall.id)
            } else {
                CallManager.hold(primaryCall.id)
            }
        },
        onDtmfDigit = { digit -> CallManager.sendDtmf(primaryCall.id, digit) },
        modifier = modifier
    )
}

@Composable
fun InCallScreen(
    primaryCall: CallInfo,
    audio: AudioInfo,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onDtmfDigit: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showKeypad by remember { mutableStateOf(false) }

    LaunchedEffect(primaryCall.photoUri) {
        avatarBitmap = loadPhotoBitmap(context, primaryCall.photoUri)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                AvatarView(bitmap = avatarBitmap, fallbackName = primaryCall.name ?: primaryCall.number)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = primaryCall.name ?: primaryCall.number,
                    style = MaterialTheme.typography.headlineMedium
                )

                if (primaryCall.name != null) {
                    Text(
                        text = primaryCall.number,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                StatusLine(call = primaryCall)
            }

            if (showKeypad) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    InCallKeypad(onDigit = onDtmfDigit)
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (primaryCall.status == CallStatus.RINGING && primaryCall.incoming) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    RingingButtons(
                        onAnswer = onAnswer,
                        onReject = onReject
                    )
                }
            } else {
                InCallControls(
                    call = primaryCall,
                    audio = audio,
                    showKeypad = showKeypad,
                    onToggleKeypad = { showKeypad = !showKeypad },
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onToggleHold = onToggleHold,
                    onHangup = onHangup
                )
            }
        }
    }
}

// --- Sub-components ---

@Composable
private fun StatusLine(call: CallInfo) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    if (call.status == CallStatus.ACTIVE && call.connectTimeMillis > 0) {
        LaunchedEffect(call.connectTimeMillis) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val text = if (call.status == CallStatus.ACTIVE && call.connectTimeMillis > 0) {
        Format.duration((now - call.connectTimeMillis) / 1000)
    } else {
        stringResource(id = call.statusRes())
    }

    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AvatarView(bitmap: Bitmap?, fallbackName: String) {
    val modifier = Modifier
        .size(110.dp)
        .clip(CircleShape)

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackName.take(1).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun InCallControls(
    call: CallInfo,
    audio: AudioInfo,
    showKeypad: Boolean,
    onToggleKeypad: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onHangup: () -> Unit
) {
    val isSpeakerOn = audio.route == CallAudioState.ROUTE_SPEAKER
    val isOnHold = call.status == CallStatus.HOLDING

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ControlButton(
                icon = if (audio.muted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (audio.muted) "Unmute" else "Mute",
                isActive = audio.muted,
                onClick = onToggleMute
            )
            ControlButton(
                icon = Icons.Default.Dialpad,
                label = "Keypad",
                isActive = showKeypad,
                onClick = onToggleKeypad
            )
            ControlButton(
                icon = Icons.Default.VolumeUp,
                label = "Speaker",
                isActive = isSpeakerOn,
                onClick = onToggleSpeaker
            )
        }

        if (call.supportsHold) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ControlButton(
                    icon = Icons.Default.Pause,
                    label = if (isOnHold) "Resume" else "Hold",
                    isActive = isOnHold,
                    onClick = onToggleHold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        IconButton(
            onClick = onHangup,
            modifier = Modifier
                .size(68.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "End Call",
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RingingButtons(
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SwipeCallButton(
            label = "Swipe Up to Answer",
            color = Color(0xFF4CAF50),
            icon = Icons.Default.PhoneInTalk,
            direction = -1f,
            onTrigger = onAnswer
        )
        SwipeCallButton(
            label = "Swipe Down to Decline",
            color = MaterialTheme.colorScheme.error,
            icon = Icons.Default.CallEnd,
            direction = 1f,
            onTrigger = onReject
        )
    }
}

@Composable
private fun SwipeCallButton(
    label: String,
    color: Color,
    icon: ImageVector,
    direction: Float,
    onTrigger: () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 100.dp.toPx() } }

    var rawOffsetY by remember { mutableFloatStateOf(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(72.dp)
            .offset { IntOffset(0, (if (animOffsetY.isRunning) animOffsetY.value else rawOffsetY).roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val reachedThreshold = if (direction < 0) {
                            rawOffsetY <= -thresholdPx * 0.75f
                        } else {
                            rawOffsetY >= thresholdPx * 0.75f
                        }

                        if (reachedThreshold) {
                            onTrigger()
                        } else {
                            scope.launch {
                                animOffsetY.snapTo(rawOffsetY)
                                animOffsetY.animateTo(0f, animationSpec = tween(200))
                                rawOffsetY = 0f
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            animOffsetY.snapTo(rawOffsetY)
                            animOffsetY.animateTo(0f, animationSpec = tween(200))
                            rawOffsetY = 0f
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val next = rawOffsetY + dragAmount
                        rawOffsetY = if (direction < 0) {
                            next.coerceIn(-thresholdPx, 0f)
                        } else {
                            next.coerceIn(0f, thresholdPx)
                        }
                    }
                )
            }
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun InCallKeypad(onDigit: (Char) -> Unit) {
    val digits = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        for (row in digits) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (digit in row) {
                    IconButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}