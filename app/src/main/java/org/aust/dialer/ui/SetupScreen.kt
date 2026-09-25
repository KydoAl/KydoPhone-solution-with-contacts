package org.aust.dialer.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.aust.dialer.DialerViewModel
import org.aust.dialer.SmsViewModel
import org.aust.dialer.R
import org.aust.dialer.core.Intents
import org.aust.dialer.telecom.RoleUtils

/** First-run screen: explains each permission before asking, then offers the default-phone-app role. */
@Composable
fun SetupScreen(vm: DialerViewModel, smsVm: SmsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val perms by vm.perms.collectAsState()
    val smsPerms by smsVm.perms.collectAsState()

    val permissionArray = buildList {
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshPermissions()
        smsVm.refreshPermissions()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshPermissions()
    }
    val smsRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        smsVm.refreshPermissions()
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)

            StepCard(
                title = stringResource(R.string.setup_perm_title),
                text = stringResource(R.string.setup_perm_text),
                done = perms.coreGranted,
                doneText = stringResource(R.string.setup_perm_done),
                buttonText = stringResource(R.string.setup_grant),
                onClick = { permLauncher.launch(permissionArray) },
            )

            StepCard(
                title = stringResource(R.string.setup_default_title),
                text = stringResource(R.string.setup_default_text),
                done = perms.isDefaultDialer,
                doneText = stringResource(R.string.setup_default_done),
                buttonText = stringResource(R.string.setup_default_button),
                onClick = { RoleUtils.requestIntent(context)?.let { roleLauncher.launch(it) } },
            )

            StepCard(
                title = stringResource(R.string.setup_default_sms_title),
                text = stringResource(R.string.setup_default_sms_text),
                done = smsPerms.isDefaultSms,
                doneText = stringResource(R.string.setup_default_sms_done),
                buttonText = stringResource(R.string.setup_default_button),
                onClick = { RoleUtils.requestSmsIntent(context)?.let { smsRoleLauncher.launch(it) } },
            )

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_continue))
            }
            if (!perms.coreGranted) {
                TextButton(onClick = { Intents.openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    text: String,
    done: Boolean,
    doneText: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (done) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("  $doneText", color = MaterialTheme.colorScheme.primary)
                }
            } else {
                OutlinedButton(onClick = onClick) { Text(buttonText) }
            }
        }
    }
}
