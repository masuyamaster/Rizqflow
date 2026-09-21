package com.roziqrizal.rizqflow.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.ledger.TransactionSnapshot
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.ui.catat.CatatFlow
import com.roziqrizal.rizqflow.ui.catat.SavedInfo
import com.roziqrizal.rizqflow.ui.transaksi.TransaksiScreen
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch

/**
 * Menu utama beserta layar Catat di atasnya. Catat menutupi menu utama (bukan menggantinya)
 * sehingga tab yang sedang dibuka tidak hilang. Setelah menyimpan, snackbar menawarkan
 * **Urungkan**, yang menghapus transaksi itu (dan potret alokasinya).
 */
@Composable
fun MainHost(
    workspace: AccountWorkspace,
    account: AccountUi,
    onConnectGmail: () -> Unit,
    onSignOut: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val context = LocalContext.current
    var catat by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    // Berubah setiap ada transaksi yang disimpan, diubah, dihapus, atau diurungkan: daftar memuat ulang.
    var version by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = context.getString(R.string.action_undo)

    fun announceEdit(previous: TransactionSnapshot) {
        version++
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(context.getString(R.string.saved_edit), actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                workspace.ledger.restore(previous)
                version++
            }
        }
    }

    fun announceDelete(snapshot: TransactionSnapshot) {
        version++
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(context.getString(R.string.deleted), actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                workspace.ledger.restore(snapshot)
                version++
            }
        }
    }

    fun announce(saved: SavedInfo) {
        saved.previous?.let { return announceEdit(it) }
        version++
        val amount = formatRupiah(saved.amount)
        val message = when (saved.kind) {
            TransactionKind.INCOME ->
                if (saved.roomCount > 0) context.getString(R.string.saved_income, amount, saved.roomCount) else context.getString(R.string.saved_income_none, amount)

            TransactionKind.EXPENSE -> context.getString(R.string.saved_expense, amount)
            TransactionKind.TRANSFER -> context.getString(R.string.saved_transfer, amount)
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                workspace.ledger.delete(saved.id)
                version++
            }
        }
    }

    RizqflowApp(
        account = account,
        onCatat = { catat = true },
        onConnectGmail = onConnectGmail,
        onSignOut = onSignOut,
        onDismissNotice = onDismissNotice,
        snackbarHostState = snackbar,
        transaksiContent = { TransaksiScreen(workspace, refreshKey = version, onOpen = { editId = it.value }) },
    )
    val editing = editId
    if (editing != null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CatatFlow(
                workspace = workspace,
                editing = TransactionId(editing),
                onClose = { editId = null },
                onSaved = ::announce,
                onDeleted = ::announceDelete,
            )
        }
    } else if (catat) {
        // Surface menangkap sentuhan supaya tidak tembus ke menu utama di bawahnya.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CatatFlow(workspace, onClose = { catat = false }, onSaved = ::announce)
        }
    }
}
