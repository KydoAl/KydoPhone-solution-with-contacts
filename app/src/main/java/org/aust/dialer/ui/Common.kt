package org.aust.dialer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.core.Intents
import org.aust.dialer.data.PhotoCache
import org.aust.dialer.telecom.SimAccount

/** Screens ask for a call through this; AppRoot implements permissions, SIM choice and placing the call. */
class CallController {
    var impl: (String) -> Unit = {}
    fun call(number: String) = impl(number)
}

val LocalCallController = staticCompositionLocalOf { CallController() }

/** Screens ask to open a conversation through this; AppRoot implements the in-app thread navigation. */
class MessageController {
    var impl: (String) -> Unit = {}
    fun open(address: String) = impl(address)
}

val LocalMessageController = staticCompositionLocalOf { MessageController() }
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState not provided") }

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun Avatar(name: String?, photoUri: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = photoUri?.let { PhotoCache.peek(it, px) }, photoUri) {
        value = if (photoUri == null) null else withContext(Dispatchers.IO) { PhotoCache.load(context, photoUri, px) }
    }
    val cs = MaterialTheme.colorScheme
    val palette = listOf(
        cs.primaryContainer to cs.onPrimaryContainer,
        cs.secondaryContainer to cs.onSecondaryContainer,
        cs.tertiaryContainer to cs.onTertiaryContainer,
    )
    val (bg, fg) = palette[(name ?: "").hashCode().mod(palette.size)]
    Box(
        modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        val letter = name?.trim()?.firstOrNull()?.takeIf { it.isLetter() }
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (letter != null) {
            Text(letter.uppercaseChar().toString(), color = fg, fontSize = (size.value * 0.42f).sp)
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
fun ContactOrRecentsCard(
    title: String,
    subtitle: String?,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MessageCard(text: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Returns a function that requests [permissions], then calls [onResult] (typically a ViewModel's
 * refreshPermissions). When the system will no longer show the dialog (permanently denied) it opens
 * the app's settings page instead.
 */
@Composable
fun rememberPermissionRequester(onResult: () -> Unit, permissions: Array<String>): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onResult()
        val activity = context.findActivity()
        val blocked = result.any { (perm, granted) ->
            !granted && activity != null && !activity.shouldShowRequestPermissionRationale(perm)
        }
        if (blocked) Intents.openAppSettings(context)
    }
    return remember(launcher) { { launcher.launch(permissions) } }
}

/** Convenience overload for the common case of refreshing a single ViewModel's permission state. */
@Composable
fun rememberPermissionRequester(vm: DialerViewModel, permissions: Array<String>): () -> Unit =
    rememberPermissionRequester(vm::refreshPermissions, permissions)

@Composable
fun simLabel(account: SimAccount, index: Int): String {
    val fallback = stringResource(R.string.sim_label, index + 1)
    return if (account.label.isBlank()) fallback else "$fallback · ${account.label}"
}

@Composable
fun SimPickerDialog(accounts: List<SimAccount>, onPick: (SimAccount) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sim_choose_title)) },
        text = {
            Column {
                accounts.forEachIndexed { i, account ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(account) }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(simLabel(account, i), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
