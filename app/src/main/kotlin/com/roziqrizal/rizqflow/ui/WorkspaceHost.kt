package com.roziqrizal.rizqflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roziqrizal.rizqflow.backup.BackupFileService
import com.roziqrizal.rizqflow.domain.auth.AccountStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.roziqrizal.rizqflow.ui.onboarding.OnboardingFlow
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import com.roziqrizal.rizqflow.workspace.WorkspaceController
import com.roziqrizal.rizqflow.workspace.WorkspaceState

/**
 * Membuka ruang kerja (database) milik akun yang sedang masuk, lalu memilih layar: onboarding bila
 * akun ini belum punya ruang, atau [content] bila sudah. Berganti akun membuka berkas lain dan
 * menutup yang lama; Keluar tidak menghapus berkas.
 *
 * [content] juga menerima `restoreFromBackup`: dipanggil dari layar Backup (S20) dengan isi
 * database yang sudah didekripsi. Berkas database ditimpa setelah [AccountWorkspace] lama ditutup
 * (SQLite tidak boleh ditimpa selagi koneksinya terbuka), lalu ruang kerja dibuka ulang dari berkas
 * baru — `content` dipanggil lagi dengan objek [AccountWorkspace] yang berbeda.
 */
@Composable
fun WorkspaceHost(
    accountId: String,
    onDemoFailed: () -> Unit = {},
    content: @Composable (AccountWorkspace, restoreFromBackup: (ByteArray) -> Unit) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val isDemo = accountId == AccountStorage.DEMO_ACCOUNT_ID
    var restoreGeneration by remember(accountId) { mutableIntStateOf(0) }
    var pendingRestore by remember(accountId) { mutableStateOf<ByteArray?>(null) }
    val workspace = remember(accountId, restoreGeneration) {
        // Demo selalu mulai dari data contoh yang segar; sisa sesi lama (proses mati saat demo) dibuang.
        if (isDemo) context.deleteDatabase(AccountStorage.databaseName(accountId))
        AccountWorkspace.open(context, accountId)
    }
    DisposableEffect(workspace) {
        onDispose {
            workspace.close()
            // Keluar dari demo menghapus data contoh; data pengguna tidak pernah disentuh.
            if (isDemo) context.deleteDatabase(AccountStorage.databaseName(accountId))
        }
    }
    // Berjalan di komposisi WorkspaceHost, bukan layar Backup: tetap selesai walau layar yang
    // memicunya sudah dibuang begitu `restoreGeneration` bertambah dan ruang kerja baru terbuka.
    LaunchedEffect(pendingRestore) {
        val decrypted = pendingRestore ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            workspace.close()
            BackupFileService.overwrite(context, accountId, decrypted)
        }
        pendingRestore = null
        restoreGeneration++
    }
    var seeded by remember(workspace) { mutableStateOf(!isDemo) }
    LaunchedEffect(workspace) {
        if (isDemo) {
            try {
                workspace.demo.seed(LocalDate.now())
                seeded = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onDemoFailed()
            }
        }
    }

    val scope = rememberCoroutineScope()
    val controller = remember(workspace) {
        WorkspaceController(workspace.repositories.workspace, workspace.setup, scope)
    }
    LaunchedEffect(controller, seeded) { if (seeded) controller.start() }

    when (val state = controller.state.collectAsState().value) {
        WorkspaceState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        is WorkspaceState.NeedsOnboarding -> OnboardingFlow(busy = state.busy, failed = state.failed, onFinish = controller::finish)
        WorkspaceState.Ready -> content(workspace) { pendingRestore = it }
    }
}
