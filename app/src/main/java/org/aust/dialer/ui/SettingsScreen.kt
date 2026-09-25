package org.aust.dialer.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.aust.dialer.BuildConfig
import org.aust.dialer.DialerViewModel
import org.aust.dialer.R
import org.aust.dialer.SmsViewModel
import org.aust.dialer.core.Prefs
import org.aust.dialer.telecom.RoleUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: DialerViewModel,
    smsVm: SmsViewModel,
    onBack: () -> Unit,
    onSpeedDial: () -> Unit
) {
    val context = LocalContext.current
    val prefs = vm.prefs
    val themeMode by prefs.themeMode.collectAsState()
    val dynamic by prefs.dynamicColor.collectAsState()
    val tones by prefs.dtmfTones.collectAsState()
    val perms by vm.perms.collectAsState()
    val smsPerms by smsVm.perms.collectAsState()
    val language = prefs.language

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshPermissions()
    }
    val smsRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        smsVm.refreshPermissions()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PHONE SETTINGS SECTION
            SectionTitle(R.string.settings_phone)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = {
                            Icon(Icons.Outlined.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text(stringResource(R.string.settings_default_dialer)) },
                        supportingContent = {
                            Text(stringResource(if (perms.isDefaultDialer) R.string.settings_default_yes else R.string.settings_default_no))
                        },
                        trailingContent = {
                            if (!perms.isDefaultDialer) {
                                Button(onClick = {
                                    RoleUtils.requestIntent(context)?.let { roleLauncher.launch(it) }
                                }) { Text(stringResource(R.string.setup_default_button)) }
                            }
                        },
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        headlineContent = { Text(stringResource(R.string.settings_default_sms)) },
                        supportingContent = {
                            Text(stringResource(if (smsPerms.isDefaultSms) R.string.settings_default_yes else R.string.settings_default_no))
                        },
                        trailingContent = {
                            if (!smsPerms.isDefaultSms) {
                                Button(onClick = {
                                    RoleUtils.requestSmsIntent(context)?.let { smsRoleLauncher.launch(it) }
                                }) { Text(stringResource(R.string.setup_default_button)) }
                            }
                        },
                    )
                    ListItem(
                        modifier = Modifier.clickable(onClick = onSpeedDial),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = {
                            Icon(Icons.Outlined.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text(stringResource(R.string.speeddial_title)) },
                        supportingContent = { Text(stringResource(R.string.speeddial_summary)) },
                    )
                    ListItem(
                        modifier = Modifier.clickable { prefs.setDtmfTones(!tones) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = {
                            Icon(Icons.Outlined.Dialpad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text(stringResource(R.string.settings_dialpad_tones)) },
                        trailingContent = { Switch(checked = tones, onCheckedChange = { prefs.setDtmfTones(it) }) },
                    )
                }
            }

            // APPEARANCE SETTINGS SECTION
            SectionTitle(R.string.settings_appearance)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                        }
                        val themeOptions = listOf(
                            Prefs.THEME_SYSTEM to R.string.theme_system,
                            Prefs.THEME_LIGHT to R.string.theme_light,
                            Prefs.THEME_DARK to R.string.theme_dark,
                        )
                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            themeOptions.forEachIndexed { i, (mode, label) ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = { prefs.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, themeOptions.size),
                                ) { Text(stringResource(label), maxLines = 1) }
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ListItem(
                            modifier = Modifier.clickable { prefs.setDynamicColor(!dynamic) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            headlineContent = { Text(stringResource(R.string.settings_dynamic)) },
                            supportingContent = { Text(stringResource(R.string.settings_dynamic_summary)) },
                            trailingContent = { Switch(checked = dynamic, onCheckedChange = { prefs.setDynamicColor(it) }) },
                        )
                    }

                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
                        }
                        val langOptions = listOf("" to R.string.lang_system, "en" to R.string.lang_en, "ar" to R.string.lang_ar)
                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            langOptions.forEachIndexed { i, (code, label) ->
                                SegmentedButton(
                                    selected = language == code,
                                    onClick = {
                                        if (language != code) {
                                            prefs.language = code
                                            context.findActivity()?.recreate()
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(i, langOptions.size),
                                ) { Text(stringResource(label), maxLines = 1) }
                            }
                        }
                    }
                }
            }

            // ABOUT SECTION
            SectionTitle(R.string.settings_about)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        stringResource(R.string.settings_about_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
    )
}