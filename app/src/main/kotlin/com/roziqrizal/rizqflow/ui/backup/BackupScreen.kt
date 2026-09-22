package com.roziqrizal.rizqflow.ui.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.backup.BackupFileService
import com.roziqrizal.rizqflow.backup.BackupPrefs
import com.roziqrizal.rizqflow.domain.backup.BackupCorruptOrWrongPassword
import com.roziqrizal.rizqflow.domain.backup.BackupCrypto
import com.roziqrizal.rizqflow.ui.Notifier
import com.roziqrizal.rizqflow.ui.formatDate
import com.roziqrizal.rizqflow.ui.login.AuthTextField
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S20 Backup dan data: cadangan terenkripsi (F7) dari seluruh database akun ini, sandi milik
 * pengguna, AES-GCM lewat [BackupCrypto]. Ekspor/impor CSV punya layar sendiri (menu terpisah);
 * ini salinan database apa adanya, dipulihkan lewat [onRestored].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(workspace: AccountWorkspace, notifier: Notifier, onClose: () -> Unit, onRestored: (ByteArray) -> Unit) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    val prefs = remember { BackupPrefs(context) }
    var lastBackupAt by rememberSaveable { mutableStateOf(prefs.lastBackupAtMillis) }
    var busy by remember { mutableStateOf(false) }
    var backupSheet by rememberSaveable { mutableStateOf(false) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    // Sandi cadangan tidak ikut lewat argumen intent CreateDocument; disimpan di sini antara sheet
    // ditutup dan hasil pemilih berkas kembali (proses tidak mati di antara keduanya).
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (password == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = runCatching {
                val plain = BackupFileService.snapshot(workspace)
                val encrypted = withContext(Dispatchers.Default) { BackupCrypto.encrypt(plain, password) }
                context.contentResolver.openOutputStream(uri)?.use { it.write(encrypted) }
            }.isSuccess
            busy = false
            if (ok) {
                val now = System.currentTimeMillis()
                prefs.lastBackupAtMillis = now
                lastBackupAt = now
                notifier.show(context.getString(R.string.backup_done))
            } else {
                notifier.show(context.getString(R.string.backup_failed))
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreError = null
            restoreUri = uri
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
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                lastBackupAt?.let { stringResource(R.string.backup_last_at, formatDate(millisToDate(it), LocalDate.now())) }
                    ?: stringResource(R.string.backup_last_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.s2),
            )

            Text(stringResource(R.string.backup_section_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = spacing.s5))
            Button(
                onClick = { backupSheet = true },
                enabled = !busy,
                modifier = Modifier.padding(top = spacing.s3).fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.backup_create_action)) }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.padding(top = spacing.s2).fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.backup_restore_action)) }

            Text(
                stringResource(R.string.backup_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.s5),
            )
        }
    }

    if (backupSheet) {
        BackupPasswordSheet(
            busy = busy,
            onDismiss = { backupSheet = false },
            onConfirm = { password ->
                pendingBackupPassword = password
                backupSheet = false
                backupLauncher.launch(defaultBackupName())
            },
        )
    }

    val pickedUri = restoreUri
    if (pickedUri != null) {
        RestoreConfirmSheet(
            busy = busy,
            error = restoreError,
            onDismiss = { restoreUri = null; restoreError = null },
            onConfirm = { password ->
                busy = true
                restoreError = null
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openInputStream(pickedUri)?.use { it.readBytes() } }.getOrNull()
                    }
                    if (bytes == null) {
                        busy = false
                        restoreError = context.getString(R.string.backup_restore_read_error)
                        return@launch
                    }
                    val decrypted = withContext(Dispatchers.Default) {
                        runCatching { BackupCrypto.decrypt(bytes, password) }
                    }
                    busy = false
                    decrypted.fold(
                        onSuccess = {
                            restoreUri = null
                            onRestored(it)
                        },
                        onFailure = {
                            restoreError = if (it is BackupCorruptOrWrongPassword) {
                                context.getString(R.string.backup_restore_wrong_password)
                            } else {
                                context.getString(R.string.backup_restore_read_error)
                            }
                        },
                    )
                }
            },
        )
    }
}

private fun millisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun defaultBackupName(): String = "rizqflow-cadangan-${LocalDate.now()}.rzqbk"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupPasswordSheet(busy: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }

    val error = when {
        !touched -> null
        password.length < MIN_PASSWORD_LENGTH -> stringResource(R.string.backup_password_too_short)
        password != repeat -> stringResource(R.string.backup_password_mismatch)
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.s5).padding(bottom = spacing.s5)) {
            Text(stringResource(R.string.backup_sheet_title), style = MaterialTheme.typography.titleLarge)
            AuthTextField(
                value = password,
                onValueChange = { password = it; touched = true },
                label = stringResource(R.string.backup_password_label),
                isPassword = true,
                hint = stringResource(R.string.backup_password_hint),
                modifier = Modifier.padding(top = spacing.s4),
            )
            AuthTextField(
                value = repeat,
                onValueChange = { repeat = it; touched = true },
                label = stringResource(R.string.backup_password_repeat_label),
                isPassword = true,
                error = error,
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(top = spacing.s3),
            )
            Button(
                onClick = { onConfirm(password) },
                enabled = !busy && password.length >= MIN_PASSWORD_LENGTH && password == repeat,
                modifier = Modifier.padding(top = spacing.s4).fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.backup_save_share_action)) }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = spacing.s1).fillMaxWidth()) {
                Text(stringResource(R.string.catat_date_cancel))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreConfirmSheet(busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    var password by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.s5).padding(bottom = spacing.s5)) {
            Text(stringResource(R.string.backup_restore_confirm_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.backup_restore_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.s2),
            )
            AuthTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.backup_password_label),
                isPassword = true,
                error = error,
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(top = spacing.s4),
            )
            Button(
                onClick = { onConfirm(password) },
                enabled = !busy && password.isNotEmpty(),
                modifier = Modifier.padding(top = spacing.s4).fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.backup_restore_confirm_action)) }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.padding(top = spacing.s1).fillMaxWidth()) {
                Text(stringResource(R.string.catat_date_cancel))
            }
        }
    }
}

private const val MIN_PASSWORD_LENGTH = 6
