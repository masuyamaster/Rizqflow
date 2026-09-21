package com.roziqrizal.rizqflow.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.ui.catat.CatatFlow
import com.roziqrizal.rizqflow.ui.catat.SavedInfo
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = context.getString(R.string.action_undo)

    fun announce(saved: SavedInfo) {
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
            if (result == SnackbarResult.ActionPerformed) workspace.ledger.delete(saved.id)
        }
    }

    RizqflowApp(
        account = account,
        onCatat = { catat = true },
        onConnectGmail = onConnectGmail,
        onSignOut = onSignOut,
        onDismissNotice = onDismissNotice,
        snackbarHostState = snackbar,
    )
    if (catat) {
        // Surface menangkap sentuhan supaya tidak tembus ke menu utama di bawahnya.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CatatFlow(workspace, onClose = { catat = false }, onSaved = ::announce)
        }
    }
}
