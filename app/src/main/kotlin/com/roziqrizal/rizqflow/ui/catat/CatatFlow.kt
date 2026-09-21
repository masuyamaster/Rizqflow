package com.roziqrizal.rizqflow.ui.catat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.allocation.AllocationResult
import com.roziqrizal.rizqflow.domain.ledger.BudgetWarning
import com.roziqrizal.rizqflow.domain.ledger.CatatContext
import com.roziqrizal.rizqflow.domain.ledger.CatatContextLoader
import com.roziqrizal.rizqflow.domain.ledger.CatatDraft
import com.roziqrizal.rizqflow.domain.ledger.CatatIssue
import com.roziqrizal.rizqflow.domain.ledger.CatatMode
import com.roziqrizal.rizqflow.domain.ledger.INCOME_SOURCES
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.OneTimeSplit
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.formatDate
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.onboarding.AmountKeypad
import com.roziqrizal.rizqflow.ui.theme.CaslonFamily
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.rizqflowFilterChipColors
import com.roziqrizal.rizqflow.ui.theme.rizqflowSegmentedColors
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Yang baru saja tersimpan: cukup untuk menyusun pesan dan mengurungkannya. [roomCount]: jumlah ruang yang menerima alirannya. */
data class SavedInfo(val id: TransactionId, val kind: TransactionKind, val amount: Money, val roomCount: Int)

/** Hasil pratinjau S07 yang sedang dilihat pengguna. [split] tidak null berarti "Ubah sekali ini" aktif. */
private data class Review(val allocation: AllocationResult, val split: OneTimeSplit?)

/**
 * Layar Catat (S06) dan Pratinjau alokasi (S07). Isian ada di [CatatDraft] yang murni dan disimpan
 * lewat `rememberSaveable`; layar ini hanya menampilkan dan memanggil layanan. Pemasukan melewati
 * S07 dulu (rezeki terlihat mengalir sebelum disimpan); pengeluaran dan transfer langsung simpan.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CatatFlow(
    workspace: AccountWorkspace,
    onClose: () -> Unit,
    onSaved: (SavedInfo) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var loaded by remember { mutableStateOf<CatatContext?>(null) }
    LaunchedEffect(workspace) {
        val repos = workspace.repositories
        loaded = CatatContextLoader(repos.accounts, repos.rooms, repos.transactions).load()
    }
    val context = loaded
    if (context == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(CatatDraft.start(context, today)) }
    var review by remember { mutableStateOf<Review?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LedgerError?>(null) }
    var warning by remember { mutableStateOf<BudgetWarning?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Banner lembut jatah terlampaui: dihitung ulang setiap nominal, ruang, atau tanggal berubah.
    LaunchedEffect(draft.mode, draft.roomId, draft.amount, draft.date) {
        val room = draft.roomId
        warning = if (draft.mode == CatatMode.EXPENSE && draft.amount.isPositive && room != null) {
            workspace.ledger.budgetWarning(room, draft.amount, draft.date)
        } else {
            null
        }
    }

    BackHandler(enabled = !busy) { if (review != null) review = null else onClose() }

    fun change(next: CatatDraft) {
        draft = next
        error = null
    }

    fun saveDirect(keepOpen: Boolean) {
        if (busy || draft.issue(context) != null) return
        busy = true
        error = null
        scope.launch {
            val result = if (draft.mode == CatatMode.EXPENSE) {
                workspace.ledger.recordExpense(draft.toExpense())
            } else {
                workspace.ledger.recordTransfer(draft.toTransfer())
            }
            busy = false
            when (result) {
                is LedgerResult.Success -> {
                    val tx = result.value
                    onSaved(SavedInfo(tx.id, tx.kind, tx.amount, 0))
                    if (keepOpen) change(draft.copy(digits = "", note = "")) else onClose()
                }

                is LedgerResult.Failure -> error = result.error
            }
        }
    }

    fun openReview() {
        if (busy || draft.issue(context) != null) return
        scope.launch { review = Review(workspace.ledger.previewIncome(draft.amount), split = null) }
    }

    fun updateSplit(split: OneTimeSplit?) {
        scope.launch { review = Review(workspace.ledger.previewIncome(draft.amount, split?.toRules()), split) }
    }

    fun confirmIncome() {
        val current = review ?: return
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val result = workspace.ledger.recordIncome(draft.toIncome(current.split?.toRules()))
            busy = false
            when (result) {
                is LedgerResult.Success -> {
                    val receipt = result.value
                    onSaved(SavedInfo(receipt.transaction.id, TransactionKind.INCOME, receipt.transaction.amount, receipt.allocation.shares.count { it.amount.isPositive }))
                    onClose()
                }

                is LedgerResult.Failure -> error = result.error
            }
        }
    }

    val shown = review
    if (shown != null) {
        ReviewScreen(
            context = context,
            draft = draft,
            allocation = shown.allocation,
            split = shown.split,
            busy = busy,
            error = error,
            onBack = { review = null },
            onToggleEdit = { on -> updateSplit(if (on) OneTimeSplit.from(context.rules) else null) },
            onPercent = { index, value -> shown.split?.let { updateSplit(it.set(index, value)) } },
            onStep = { index, delta -> shown.split?.let { updateSplit(it.step(index, delta)) } },
            onConfirm = ::confirmIncome,
        )
        return
    }

    val spacing = MaterialTheme.spacing
    val issue = draft.issue(context)
    val imeVisible = WindowInsets.isImeVisible
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        // Bagian atas bisa digulir; papan angka dan tombol simpan menempel di bawah supaya selalu terjangkau.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.s5)
                .padding(top = spacing.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = !busy) {
                    Icon(RizqflowIcons.Tutup, contentDescription = stringResource(R.string.catat_close))
                }
                Text(stringResource(R.string.catat_title), style = MaterialTheme.typography.titleLarge)
            }

            ModeTabs(draft.mode, canTransfer = context.canTransfer, onMode = { change(draft.withMode(it, context)) })
            if (!context.canTransfer && draft.mode == CatatMode.TRANSFER) {
                Hint(stringResource(R.string.catat_transfer_needs_two))
            }

            // Nominal besar: satu-satunya hal yang paling penting di layar ini.
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s3), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatRupiah(draft.amount).removePrefix("Rp\u00A0"),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = CaslonFamily),
                    color = if (draft.amount.isPositive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            // Banner dan petunjuk tepat di bawah nominal supaya terlihat tanpa menggulir.
            warning?.let { WarningBanner(it) }
            error?.let { Hint(stringResource(errorText(it))) }
            if (issue != null && draft.amount.isPositive) Hint(stringResource(issueText(issue)))

            when (draft.mode) {
                CatatMode.INCOME -> {
                    FieldLabel(R.string.catat_source)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
                        INCOME_SOURCES.forEach { source ->
                            FilterChip(
                                selected = draft.source == source,
                                onClick = { change(draft.withSource(source)) },
                                label = { Text(source) },
                                colors = rizqflowFilterChipColors(),
                            )
                        }
                    }
                    FieldLabel(R.string.catat_account_to)
                    AccountChips(context, selected = draft.accountId, onPick = { change(draft.withAccount(it, context)) })
                }

                CatatMode.EXPENSE -> {
                    if (context.rooms.isEmpty()) {
                        Hint(stringResource(R.string.catat_no_rooms))
                    } else {
                        FieldLabel(R.string.catat_room)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
                            context.rooms.forEach { room ->
                                FilterChip(
                                    selected = draft.roomId == room.id,
                                    onClick = { change(draft.withRoom(room.id, context)) },
                                    label = { Text(room.name) },
                                    colors = rizqflowFilterChipColors(),
                                )
                            }
                        }
                        FieldLabel(R.string.catat_category)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
                            draft.roomId?.let(context::categoriesOf).orEmpty().forEach { category ->
                                FilterChip(
                                    selected = draft.categoryId == category.id,
                                    onClick = { change(draft.withCategory(category.id)) },
                                    label = { Text(category.name) },
                                    colors = rizqflowFilterChipColors(),
                                )
                            }
                        }
                        FieldLabel(R.string.catat_account)
                        AccountChips(context, selected = draft.accountId, onPick = { change(draft.withAccount(it, context)) })
                    }
                }

                CatatMode.TRANSFER -> {
                    FieldLabel(R.string.catat_from)
                    AccountChips(context, selected = draft.accountId, onPick = { change(draft.withAccount(it, context)) })
                    FieldLabel(R.string.catat_to)
                    AccountChips(context, selected = draft.toAccountId, onPick = { change(draft.withToAccount(it, context)) })
                }
            }

            FieldLabel(R.string.catat_date)
            OutlinedButton(onClick = { pickingDate = true }, enabled = !busy) { Text(formatDate(draft.date, today)) }

            OutlinedTextField(
                value = draft.note,
                onValueChange = { change(draft.withNote(it)) },
                label = { Text(stringResource(R.string.catat_note)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = spacing.s3),
            )

        }

        Column(modifier = Modifier.padding(horizontal = spacing.s5).padding(top = spacing.s2, bottom = spacing.s3)) {
            // Saat papan ketik sistem terbuka (mengisi catatan), papan angka disembunyikan supaya layar tidak sesak.
            if (!imeVisible) AmountKeypad(enabled = !busy, onKey = { change(draft.pressKey(it)) })

            val canSave = issue == null && !busy
            when (draft.mode) {
                CatatMode.INCOME -> Button(
                    onClick = ::openReview,
                    enabled = canSave,
                    modifier = Modifier.padding(top = spacing.s3).fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.catat_next)) }

                CatatMode.EXPENSE -> Row(
                    modifier = Modifier.padding(top = spacing.s3).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { saveDirect(false) }, enabled = canSave, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.catat_save))
                    }
                    OutlinedButton(onClick = { saveDirect(true) }, enabled = canSave, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.catat_save_more))
                    }
                }

                CatatMode.TRANSFER -> Button(
                    onClick = { saveDirect(false) },
                    enabled = canSave,
                    modifier = Modifier.padding(top = spacing.s3).fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.catat_save)) }
            }
        }
    }

    if (pickingDate) {
        val startMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startMillis,
            // Yang dicatat adalah yang sudah terjadi: tanggal depan tidak bisa dipilih.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        change(draft.withDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.catat_date_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.catat_date_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

// ---------------------------------------------------------------------------------- bagian kecil

@Composable
private fun ModeTabs(mode: CatatMode, canTransfer: Boolean, onMode: (CatatMode) -> Unit) {
    val items = listOf(
        CatatMode.INCOME to R.string.catat_tab_income,
        CatatMode.EXPENSE to R.string.catat_tab_expense,
        CatatMode.TRANSFER to R.string.catat_tab_transfer,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.s2)) {
        items.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onMode(value) },
                shape = SegmentedButtonDefaults.itemShape(index, items.size),
                colors = rizqflowSegmentedColors(),
                // Dengan satu akun, Transfer nonaktif; penjelasannya tampil di bawah saat tab ini dipilih.
                enabled = value != CatatMode.TRANSFER || canTransfer,
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun FieldLabel(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = MaterialTheme.spacing.s4, bottom = MaterialTheme.spacing.s1),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(context: CatatContext, selected: AccountId?, onPick: (AccountId) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2)) {
        context.accounts.forEach { account ->
            FilterChip(
                selected = selected == account.id,
                onClick = { onPick(account.id) },
                label = { Text(account.name) },
                colors = rizqflowFilterChipColors(),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = MaterialTheme.spacing.s3)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Banner lembut (bukan merah): jatah terlampaui, tetapi tetap bisa disimpan. */
@Composable
private fun WarningBanner(warning: BudgetWarning) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .padding(top = spacing.s3)
            .fillMaxWidth()
            .background(MaterialTheme.rizqflow.statusWarning.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(spacing.s3)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(spacing.s3),
    ) {
        Icon(RizqflowIcons.Peringatan, contentDescription = null, tint = MaterialTheme.rizqflow.statusWarning)
        Text(
            stringResource(R.string.catat_over_budget, warning.room.name, formatRupiah(warning.spentAfter), formatRupiah(warning.allocated)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun issueText(issue: CatatIssue): Int = when (issue) {
    CatatIssue.NO_AMOUNT -> R.string.catat_issue_amount
    CatatIssue.NO_ACCOUNT -> R.string.catat_issue_account
    CatatIssue.NO_ROOM -> R.string.catat_no_rooms
    CatatIssue.NO_CATEGORY -> R.string.catat_issue_category
    CatatIssue.TRANSFER_NEEDS_TWO_ACCOUNTS -> R.string.catat_transfer_needs_two
    CatatIssue.SAME_ACCOUNT -> R.string.catat_issue_same_account
}

internal fun errorText(error: LedgerError): Int = when (error) {
    LedgerError.ACCOUNT_ARCHIVED, LedgerError.ACCOUNT_NOT_FOUND -> R.string.catat_error_account
    LedgerError.ROOM_ARCHIVED, LedgerError.ROOM_NOT_FOUND, LedgerError.CATEGORY_NOT_IN_ROOM -> R.string.catat_error_room
    LedgerError.NOTE_TOO_LONG, LedgerError.SOURCE_TOO_LONG -> R.string.catat_error_note
    else -> R.string.catat_error_generic
}

// ---------------------------------------------------------------------------------- penyimpanan isian

/** Menyimpan [CatatDraft] agar selamat dari layar diputar; nilai kosong ditulis sebagai teks kosong. */
private val DraftSaver = Saver<CatatDraft, List<String>>(
    save = {
        listOf(
            it.mode.name, it.digits, it.source.orEmpty(), it.accountId?.value.orEmpty(), it.toAccountId?.value.orEmpty(),
            it.roomId?.value.orEmpty(), it.categoryId?.value.orEmpty(), it.date.toEpochDay().toString(), it.note,
        )
    },
    restore = {
        CatatDraft(
            mode = CatatMode.valueOf(it[0]),
            digits = it[1],
            source = it[2].ifEmpty { null },
            accountId = it[3].ifEmpty { null }?.let(::AccountId),
            toAccountId = it[4].ifEmpty { null }?.let(::AccountId),
            roomId = it[5].ifEmpty { null }?.let(::RoomId),
            categoryId = it[6].ifEmpty { null }?.let(::CategoryId),
            date = LocalDate.ofEpochDay(it[7].toLong()),
            note = it[8],
        )
    },
)
