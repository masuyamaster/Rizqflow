package com.roziqrizal.rizqflow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.ui.denah.DenahScreen
import com.roziqrizal.rizqflow.ui.kelola.KelolaScreen
import com.roziqrizal.rizqflow.ui.kelola.manageErrorText
import com.roziqrizal.rizqflow.ui.ruang.AturanScreen
import com.roziqrizal.rizqflow.ui.ruang.RuangScreen
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
import com.roziqrizal.rizqflow.ui.catat.QuickCatatSheet
import com.roziqrizal.rizqflow.domain.ledger.FavoriteUse
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import com.roziqrizal.rizqflow.ui.catat.SavedInfo
import com.roziqrizal.rizqflow.ui.transaksi.TransaksiScreen
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch

/**
 * Menu utama beserta layar Catat di atasnya. Catat menutupi menu utama (bukan menggantinya)
 * sehingga tab yang sedang dibuka tidak hilang. Setelah menyimpan, snackbar menawarkan
 * **Urungkan**, yang menghapus transaksi itu (dan potret alokasinya).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHost(
    workspace: AccountWorkspace,
    account: AccountUi,
    onConnectGmail: () -> Unit,
    onSignOut: () -> Unit,
    onDismissNotice: () -> Unit,
    /** Bertambah setiap ada permintaan Catat kilat dari luar (pintasan ikon, tile). */
    quickCatatRequest: Int = 0,
) {
    val context = LocalContext.current
    var catat by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var aturan by rememberSaveable { mutableStateOf(false) }
    var kelola by rememberSaveable { mutableStateOf(false) }
    var quick by rememberSaveable { mutableStateOf(false) }
    var handledQuick by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(quickCatatRequest) {
        if (quickCatatRequest > handledQuick) {
            handledQuick = quickCatatRequest
            quick = true
        }
    }
    // Berubah setiap ada transaksi yang disimpan, diubah, dihapus, atau diurungkan: daftar memuat ulang.
    var version by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = context.getString(R.string.action_undo)

    // Snackbar untuk layar mana pun; hasilnya true bila tombol aksinya ditekan.
    val notifier = remember(snackbar) {
        object : Notifier {
            override suspend fun show(message: String, actionLabel: String?): Boolean {
                snackbar.currentSnackbarData?.dismiss()
                return snackbar.showSnackbar(message, actionLabel = actionLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed
            }
        }
    }

    // Berjalan di scope MainHost supaya tetap hidup setelah layar Aturan ditutup.
    fun announceRules(previous: List<AllocationRule>) {
        version++
        scope.launch {
            if (notifier.show(context.getString(R.string.rules_saved), undoLabel)) {
                workspace.rules.restoreRules(previous)
                version++
            }
        }
    }

    fun announceFavorite(use: FavoriteUse) {
        version++
        scope.launch {
            if (notifier.show(context.getString(R.string.quick_used, use.previous.name, formatRupiah(use.transaction.amount)), undoLabel)) {
                workspace.favorites.undoUse(use)
                version++
            }
        }
    }

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

            TransactionKind.EXPENSE -> {
                val extra = when {
                    saved.favoriteSaved -> " " + context.getString(R.string.saved_favorite)
                    saved.favoriteError != null -> " " + context.getString(R.string.saved_favorite_failed, manageErrorText(context, saved.favoriteError))
                    else -> ""
                }
                context.getString(R.string.saved_expense, amount) + extra
            }
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
        denahContent = {
            DenahScreen(workspace, refreshKey = version, onCatat = { catat = true }, onOpenRules = { aturan = true }, onChanged = { version++ })
        },
        ruangContent = {
            RuangScreen(workspace, refreshKey = version, notifier = notifier, onOpenRules = { aturan = true }, onChanged = { version++ })
        },
        onOpenRules = { aturan = true },
        onOpenManage = { kelola = true },
    )
    if (quick) {
        ModalBottomSheet(onDismissRequest = { quick = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            QuickCatatSheet(workspace = workspace, onClose = { quick = false }, onSaved = ::announce, onFavoriteUsed = ::announceFavorite)
        }
    }
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
    } else if (kelola) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box {
                KelolaScreen(workspace = workspace, notifier = notifier, onClose = { kelola = false }, onChanged = { version++ })
                SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
            }
        }
    } else if (aturan) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box {
                AturanScreen(
                    workspace = workspace,
                    notifier = notifier,
                    onClose = { aturan = false },
                    onSaved = ::announceRules,
                    onTemplateApplied = { version++ },
                )
                // Layar ini menutupi Scaffold beserta snackbar-nya, jadi ia membawa SnackbarHost sendiri.
                SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
            }
        }
    }
}
