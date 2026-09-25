package org.aust.dialer.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.core.Format
import org.aust.dialer.core.Intents
import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.dialerApp
import org.aust.dialer.ui.theme.CallGreen

private val KEY_ROWS = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to ""),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialPadTab(
    vm: DialerViewModel,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOpenSpeedDial: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = context.dialerApp.prefs
    val index by vm.contactIndex.collectAsState()
    val recents by vm.recents.collectAsState()
    val speedDials by prefs.speedDials.collectAsState()
    val tonesOn by prefs.dtmfTones.collectAsState()
    val callController = LocalCallController.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val tone = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 100) }.getOrNull()
            ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }

    val copiedMsg = stringResource(R.string.number_copied)
    val emptyClipMsg = stringResource(R.string.clipboard_empty)
    val setUpLabel = stringResource(R.string.speeddial_set_up)

    fun playTone(key: String) {
        if (!tonesOn || tone == null) return
        val t = when (key) {
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            else -> key.toIntOrNull()?.let { ToneGenerator.TONE_DTMF_0 + it } ?: return
        }
        try {
            tone.startTone(t, 120)
        } catch (e: RuntimeException) {
            // ignore
        }
    }

    fun insert(text: String) {
        val start = value.selection.min
        val end = value.selection.max
        val newText = value.text.replaceRange(start, end, text)
        onValueChange(TextFieldValue(newText, TextRange(start + text.length)))
    }

    fun backspace() {
        val sel = value.selection
        if (!sel.collapsed) {
            val newText = value.text.removeRange(sel.min, sel.max)
            onValueChange(TextFieldValue(newText, TextRange(sel.min)))
        } else if (sel.start > 0) {
            val newText = value.text.removeRange(sel.start - 1, sel.start)
            onValueChange(TextFieldValue(newText, TextRange(sel.start - 1)))
        }
    }

    fun setNumber(n: String) = onValueChange(TextFieldValue(n, TextRange(n.length)))

    val lastDialed = recents.firstOrNull { it.first.type == android.provider.CallLog.Calls.OUTGOING_TYPE }?.first?.number
    val matches = remember(index, value.text) { index.matchDial(value.text, 4) }

    Column(Modifier.fillMaxSize()) {
        // Contact suggestions (T9 on names, digit match on numbers)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            items(matches, key = { it.contact.id }) { m ->
                ListItem(
                    modifier = Modifier.clickable { setNumber(PhoneUtils.sanitizeDialable(m.entry.number)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Avatar(m.contact.name, m.contact.thumbUri, 40.dp) },
                    headlineContent = { Text(m.contact.name, maxLines = 1) },
                    supportingContent = { Text(Format.ltr(Format.number(m.entry.number)), maxLines = 1) },
                    trailingContent = {
                        IconButton(onClick = { callController.call(m.entry.number) }) {
                            Icon(Icons.Default.Call, contentDescription = stringResource(R.string.action_call))
                        }
                    },
                )
            }
        }

        // Number display. Read-only so the system keyboard never opens; the keypad edits at the cursor.
        val len = value.text.length
        val fontSize = when {
            len <= 12 -> 36.sp
            len <= 18 -> 30.sp
            else -> 24.sp
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 72.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { nv ->
                    val clean = PhoneUtils.sanitizeDialable(nv.text)
                    if (clean == nv.text) onValueChange(nv) else setNumber(clean)
                },
                readOnly = true,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = fontSize,
                    textAlign = TextAlign.Center,
                    textDirection = TextDirection.Ltr,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (value.text.isEmpty()) {
                            Text(
                                stringResource(R.string.dialpad_hint),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        // Keypad: always left-to-right, like every phone keypad, also in Arabic (RTL) mode.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                for (row in KEY_ROWS) {
                    Row(Modifier.fillMaxWidth()) {
                        for ((digit, letters) in row) {
                            val desc = when (digit) {
                                "*" -> stringResource(R.string.key_star)
                                "#" -> stringResource(R.string.key_pound)
                                "0" -> stringResource(R.string.key_zero_plus)
                                else -> digit
                            }
                            val speedDialHint = digit.toIntOrNull()?.takeIf { it in 1..9 } != null
                            DialKey(
                                digit = digit,
                                letters = letters,
                                description = desc,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    playTone(digit)
                                    insert(digit)
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (digit == "0") {
                                        insert("+")
                                    } else if (speedDialHint && value.text.isEmpty()) {
                                        val n = digit.toInt()
                                        val sd = speedDials[n]
                                        if (sd != null) {
                                            callController.call(sd.number)
                                        } else {
                                            val msg = context.getString(R.string.speeddial_none, n)
                                            scope.launch {
                                                val r = snackbar.showSnackbar(msg, actionLabel = setUpLabel)
                                                if (r == SnackbarResult.ActionPerformed) onOpenSpeedDial()
                                            }
                                        }
                                    } else {
                                        insert(digit)
                                    }
                                },
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_paste)) },
                                onClick = {
                                    menuOpen = false
                                    val clip = Intents.clipboardText(context)?.let { PhoneUtils.sanitizeDialable(it) }.orEmpty()
                                    if (clip.isEmpty()) scope.launch { snackbar.showSnackbar(emptyClipMsg) } else setNumber(clip)
                                },
                            )
                            if (value.text.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_copy)) },
                                    onClick = {
                                        menuOpen = false
                                        Intents.copyNumber(context, value.text)
                                        scope.launch { snackbar.showSnackbar(copiedMsg) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_edit_number)) },
                                    onClick = {
                                        menuOpen = false
                                        editOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_add_contact)) },
                                    onClick = {
                                        menuOpen = false
                                        Intents.addContact(context, value.text)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.speeddial_title)) },
                                onClick = {
                                    menuOpen = false
                                    onOpenSpeedDial()
                                },
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FloatingActionButton(
                            onClick = {
                                if (value.text.isNotEmpty()) {
                                    callController.call(value.text)
                                } else if (!lastDialed.isNullOrEmpty()) {
                                    setNumber(PhoneUtils.sanitizeDialable(lastDialed))
                                }
                            },
                            containerColor = CallGreen,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = stringResource(R.string.action_call),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Box(
                        Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value.text.isNotEmpty()) {
                            val backspaceDesc = stringResource(R.string.dialpad_backspace)
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .semantics { contentDescription = backspaceDesc }
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClick = { backspace() },
                                        onLongClick = { onValueChange(TextFieldValue("")) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editOpen) {
        var draft by remember { mutableStateOf(value.text) }
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text(stringResource(R.string.action_edit_number)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = TextStyle(textDirection = TextDirection.Ltr),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    setNumber(PhoneUtils.sanitizeDialable(draft))
                    editOpen = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { editOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    digit: String,
    letters: String,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier
            .padding(4.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = description }
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
            if (letters.isNotEmpty()) {
                Text(
                    letters,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
