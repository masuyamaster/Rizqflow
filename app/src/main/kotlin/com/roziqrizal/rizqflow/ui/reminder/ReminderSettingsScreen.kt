package com.roziqrizal.rizqflow.ui.reminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.ledger.ReminderSettings
import com.roziqrizal.rizqflow.notifications.ReminderScheduler
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch

/** S26: jam pengingat malam, aktif atau tidak, dan status izin notifikasi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(workspace: AccountWorkspace, onClose: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(ReminderSettings.DEFAULT) }
    var pickingTime by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }

    LaunchedEffect(workspace) { settings = workspace.reminder.settings() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    fun update(next: ReminderSettings) {
        settings = next
        scope.launch {
            workspace.reminder.updateSettings(next.enabled, next.hour, next.minute)
            if (next.enabled && granted) ReminderScheduler.scheduleNext(context, next) else ReminderScheduler.cancel(context)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TextButton(onClick = onClose, modifier = Modifier.padding(start = spacing.s3, top = spacing.s2)) { Text(stringResource(R.string.onb_back)) }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.s5)
                .padding(bottom = spacing.s5),
        ) {
            Text(stringResource(R.string.reminder_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.reminder_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.s2),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.s5),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reminder_enabled), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.enabled, onCheckedChange = { update(settings.copy(enabled = it)) })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.s4)
                    .clickable(enabled = settings.enabled) { pickingTime = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reminder_time), style = MaterialTheme.typography.titleMedium)
                Text("%02d.%02d".format(settings.hour, settings.minute), style = MaterialTheme.typography.titleMedium)
            }

            Text(
                stringResource(R.string.reminder_preview, "%02d.%02d".format(settings.hour, settings.minute)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.s2),
            )

            if (!granted) {
                Column(
                    modifier = Modifier
                        .padding(top = spacing.s5)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .padding(spacing.s4),
                ) {
                    Text(stringResource(R.string.reminder_permission_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.reminder_permission_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.s1),
                    )
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            }
                        },
                        modifier = Modifier.padding(top = spacing.s3),
                    ) { Text(stringResource(R.string.reminder_permission_action)) }
                }
            }
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = settings.hour, initialMinute = settings.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    update(settings.copy(hour = state.hour, minute = state.minute))
                    pickingTime = false
                }) { Text(stringResource(R.string.catat_date_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.catat_date_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
