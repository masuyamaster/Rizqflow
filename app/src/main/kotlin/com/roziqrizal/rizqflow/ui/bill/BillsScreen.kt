package com.roziqrizal.rizqflow.ui.bill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.bill.Bill
import com.roziqrizal.rizqflow.domain.bill.BillDraft
import com.roziqrizal.rizqflow.domain.bill.BillPayment
import com.roziqrizal.rizqflow.domain.bill.BillRow
import com.roziqrizal.rizqflow.domain.bill.BillStatus
import com.roziqrizal.rizqflow.domain.ledger.CatatContext
import com.roziqrizal.rizqflow.domain.ledger.CatatContextLoader
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.recurring.Frequency
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.catat.errorText
import com.roziqrizal.rizqflow.ui.formatDate
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.recurring.AccountChips
import com.roziqrizal.rizqflow.ui.recurring.DateDialog
import com.roziqrizal.rizqflow.ui.recurring.Label
import com.roziqrizal.rizqflow.ui.recurring.digitsOnly
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.rizqflowFilterChipColors
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Sheet yang sedang terbuka: null tertutup, [BillSheet.New] tambah, atau [BillSheet.Edit] ubah. */
private sealed interface BillSheet {
    data object New : BillSheet

    data class Edit(val row: BillRow) : BillSheet
}

/**
 * Tagihan dan cicilan (Tahap 10, Gratis): daftar, tambah, ubah, jeda, hapus, dan Bayar. Bayar
 * mencatat pengeluaran hari ini; pengumuman dan Urungkan-nya ditangani pemanggil lewat [onPaid]
 * supaya tetap hidup setelah layar ini ditutup. Perubahan apa pun diteruskan lewat [onChanged];
 * [refreshKey] yang berubah memuat ulang daftar (mis. sesudah Urungkan).
 */
@Composable
fun BillsScreen(workspace: AccountWorkspace, refreshKey: Int, onClose: () -> Unit, onChanged: () -> Unit, onPaid: (BillPayment) -> Unit) {
    var rows by remember { mutableStateOf<List<BillRow>?>(null) }
    var reminderOn by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<BillSheet?>(null) }
    var paying by remember { mutableStateOf<BillRow?>(null) }
    val spacing = MaterialTheme.spacing
    val today = remember { LocalDate.now() }
    BackHandler(onBack = onClose)

    LaunchedEffect(refreshKey) {
        rows = workspace.bills.list(today)
        reminderOn = workspace.reminder.settings().enabled
    }
    val data = rows

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TextButton(onClick = onClose, modifier = Modifier.padding(start = spacing.s3, top = spacing.s2)) { Text(stringResource(R.string.onb_back)) }
        Text(
            stringResource(R.string.bills_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = spacing.s5).padding(bottom = spacing.s2),
        )
        if (data == null) {
            Box(Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.s4)
                    .padding(top = spacing.s2, bottom = spacing.s5),
            ) {
                if (data.isEmpty()) {
                    Text(stringResource(R.string.bills_empty), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.bills_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.s2),
                    )
                } else if (!reminderOn) {
                    Text(
                        stringResource(R.string.bills_reminder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = spacing.s2),
                    )
                }
                data.forEach { row ->
                    BillRowItem(row, today, onOpen = { sheet = BillSheet.Edit(row) }, onPay = { paying = row })
                    HorizontalDivider()
                }
                Button(
                    onClick = { sheet = BillSheet.New },
                    modifier = Modifier.padding(top = spacing.s4).fillMaxWidth().height(52.dp),
                ) {
                    Icon(RizqflowIcons.Tambah, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.bills_add), modifier = Modifier.padding(start = spacing.s2))
                }
            }
        }
    }

    sheet?.let { current ->
        BillSheetHost(workspace, current, today, onDismiss = { sheet = null }, onChanged = {
            sheet = null
            onChanged()
        })
    }
    paying?.let { row ->
        PayDialog(workspace, row, today, onDismiss = { paying = null }, onPaid = { payment ->
            paying = null
            onPaid(payment)
        })
    }
}

private fun statusLabel(status: BillStatus): Int = when (status) {
    BillStatus.OVERDUE -> R.string.bills_status_overdue
    BillStatus.DUE_TODAY -> R.string.bills_status_today
    BillStatus.DUE_SOON -> R.string.bills_status_soon
    BillStatus.UPCOMING -> R.string.bills_status_upcoming
    BillStatus.PAUSED -> R.string.bills_status_paused
    BillStatus.FINISHED -> R.string.bills_status_finished
}

private fun statusIcon(status: BillStatus): ImageVector = when (status) {
    BillStatus.OVERDUE -> RizqflowIcons.Peringatan
    BillStatus.DUE_TODAY, BillStatus.DUE_SOON -> RizqflowIcons.Jam
    BillStatus.UPCOMING, BillStatus.PAUSED -> RizqflowIcons.Lingkaran
    BillStatus.FINISHED -> RizqflowIcons.Centang
}

@Composable
private fun BillRowItem(row: BillRow, today: LocalDate, onOpen: () -> Unit, onPay: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val bill = row.bill
    val status = bill.statusOn(today)
    val statusColor = when (status) {
        BillStatus.OVERDUE, BillStatus.DUE_TODAY -> MaterialTheme.rizqflow.statusWarning
        BillStatus.FINISHED -> MaterialTheme.rizqflow.statusGood
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Salinan lokal: properti dari modul :domain tidak bisa di-smart-cast.
    val total = bill.totalInstallments
    val progress = when {
        bill.frequency == null -> stringResource(R.string.bills_once)
        total != null -> stringResource(R.string.bills_progress, minOf(bill.paidCount + 1, total), total)
        bill.paidCount > 0 -> stringResource(R.string.bills_paid_times, bill.paidCount)
        else -> frequencyText(bill.frequency)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s3),
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onOpen)) {
            Text(bill.name, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.bills_row_where, row.roomName, row.categoryName, row.accountName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s1)) {
                Icon(statusIcon(status), contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.bills_row_meta, stringResource(statusLabel(status)), progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status != BillStatus.FINISHED) {
                Text(
                    stringResource(R.string.bills_row_due, formatDate(bill.nextDue, today)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!row.usable && status != BillStatus.FINISHED) {
                Text(stringResource(R.string.bills_status_unusable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.s1)) {
            Text(formatRupiah(bill.amount), style = MaterialTheme.typography.titleSmall)
            if (status != BillStatus.FINISHED) {
                OutlinedButton(onClick = onPay, enabled = row.usable, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.bills_pay)) }
            }
        }
    }
}

@Composable
private fun frequencyText(frequency: Frequency?): String = stringResource(
    when (frequency) {
        null -> R.string.bills_freq_once
        Frequency.DAILY -> R.string.recurring_freq_daily
        Frequency.WEEKLY -> R.string.recurring_freq_weekly
        Frequency.MONTHLY -> R.string.recurring_freq_monthly
    },
)

private fun billErrorText(error: LedgerError): Int = when (error) {
    LedgerError.INVALID_INSTALLMENTS -> R.string.bills_err_installments
    LedgerError.BILL_FINISHED -> R.string.bills_err_finished
    LedgerError.INVALID_NAME -> R.string.manage_err_name
    LedgerError.AMOUNT_NOT_POSITIVE -> R.string.manage_err_amount
    else -> errorText(error)
}

/** Dialog Bayar: nominal bawaan dari tagihan, boleh diubah; dicatat hari ini. Gagal tetap di dialog dengan alasannya. */
@Composable
private fun PayDialog(workspace: AccountWorkspace, row: BillRow, today: LocalDate, onDismiss: () -> Unit, onPaid: (BillPayment) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(row.bill.amount.minor.toString()) }
    var error by remember { mutableStateOf<LedgerError?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val value = amount.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.bills_pay_title, row.bill.name)) },
        text = {
            Column {
                Text(stringResource(R.string.bills_pay_body, row.accountName), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = digitsOnly(it)
                        error = null
                    },
                    label = { Text(stringResource(R.string.bills_pay_amount)) },
                    prefix = { Text("Rp ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.s3),
                )
                error?.let {
                    Text(
                        stringResource(billErrorText(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.s2),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value > 0 && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        when (val result = workspace.bills.pay(row.bill.id, today, Money.rupiah(value))) {
                            is LedgerResult.Success -> onPaid(result.value)
                            is LedgerResult.Failure -> error = result.error
                        }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.bills_pay)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.catat_date_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillSheetHost(workspace: AccountWorkspace, sheet: BillSheet, today: LocalDate, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var context by remember { mutableStateOf<CatatContext?>(null) }
    LaunchedEffect(Unit) {
        context = CatatContextLoader(workspace.repositories.accounts, workspace.repositories.rooms, workspace.repositories.transactions).load()
    }
    val loaded = context ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BillForm(workspace, existing = (sheet as? BillSheet.Edit)?.row, context = loaded, today = today, onCancel = onDismiss, onChanged = onChanged)
    }
}

/** Frekuensi yang masuk akal untuk tagihan: sekali, bulanan, atau mingguan (harian tidak ada padanannya di dunia tagihan). */
private val BILL_FREQUENCIES: List<Frequency?> = listOf(null, Frequency.MONTHLY, Frequency.WEEKLY)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillForm(
    workspace: AccountWorkspace,
    existing: BillRow?,
    context: CatatContext,
    today: LocalDate,
    onCancel: () -> Unit,
    onChanged: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val bill: Bill? = existing?.bill
    val defaultRoom = bill?.roomId ?: context.lastRoomId?.takeIf { id -> context.rooms.any { it.id == id } } ?: context.rooms.firstOrNull()?.id
    var name by rememberSaveable { mutableStateOf(bill?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(bill?.amount?.minor?.toString().orEmpty()) }
    var accountId by rememberSaveable { mutableStateOf(bill?.accountId?.value ?: context.lastAccountId?.value ?: context.accounts.firstOrNull()?.id?.value) }
    var roomId by rememberSaveable { mutableStateOf(defaultRoom?.value) }
    var categoryId by rememberSaveable { mutableStateOf(bill?.categoryId?.value ?: defaultRoom?.let(context::defaultCategory)?.value) }
    var frequency by rememberSaveable { mutableStateOf(if (bill != null) bill.frequency else Frequency.MONTHLY) }
    var due by rememberSaveable { mutableLongStateOf((bill?.nextDue ?: today).toEpochDay()) }
    var installments by rememberSaveable { mutableStateOf(bill?.totalInstallments?.toString().orEmpty()) }
    var alreadyPaid by rememberSaveable { mutableStateOf(bill?.paidCount?.takeIf { it > 0 }?.toString().orEmpty()) }
    var note by rememberSaveable { mutableStateOf(bill?.note.orEmpty()) }
    var pickingDue by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LedgerError?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dueDate = LocalDate.ofEpochDay(due)
    val amountValue = amount.toLongOrNull() ?: 0L
    val ready = name.isNotBlank() && amountValue > 0 && accountId != null && roomId != null && categoryId != null

    fun save() {
        busy = true
        scope.launch {
            val repeating = frequency != null
            val draft = BillDraft(
                name = name,
                amount = Money.rupiah(amountValue),
                accountId = AccountId(accountId.orEmpty()),
                roomId = RoomId(roomId.orEmpty()),
                categoryId = CategoryId(categoryId.orEmpty()),
                note = note,
                frequency = frequency,
                dueDate = dueDate,
                totalInstallments = if (repeating) installments.toIntOrNull() else null,
                alreadyPaid = if (repeating) alreadyPaid.toIntOrNull() ?: 0 else 0,
            )
            val result = if (bill == null) workspace.bills.create(draft) else workspace.bills.update(bill.id, draft)
            when (result) {
                is LedgerResult.Success -> onChanged()
                is LedgerResult.Failure -> error = result.error
            }
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.s5).padding(bottom = spacing.s5)) {
        Text(stringResource(if (bill == null) R.string.bills_new else R.string.bills_edit), style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(Bill.NAME_MAX)
                error = null
            },
            label = { Text(stringResource(R.string.bills_name)) },
            placeholder = { Text(stringResource(R.string.bills_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.s3),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = {
                amount = digitsOnly(it)
                error = null
            },
            label = { Text(stringResource(R.string.manage_amount)) },
            prefix = { Text("Rp ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.s3),
        )

        Label(R.string.catat_room)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            context.rooms.forEach { room ->
                FilterChip(
                    selected = roomId == room.id.value,
                    onClick = {
                        roomId = room.id.value
                        categoryId = context.defaultCategory(room.id)?.value
                    },
                    label = { Text(room.name) },
                    colors = rizqflowFilterChipColors(),
                )
            }
        }
        Label(R.string.catat_category)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            roomId?.let { context.categoriesOf(RoomId(it)) }.orEmpty().forEach { category ->
                FilterChip(selected = categoryId == category.id.value, onClick = { categoryId = category.id.value }, label = { Text(category.name) }, colors = rizqflowFilterChipColors())
            }
        }
        Label(R.string.catat_account)
        AccountChips(context, accountId) { accountId = it }

        Label(R.string.bills_frequency)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            BILL_FREQUENCIES.forEach { option ->
                FilterChip(
                    selected = frequency == option,
                    onClick = {
                        frequency = option
                        error = null
                    },
                    label = { Text(frequencyText(option)) },
                    colors = rizqflowFilterChipColors(),
                )
            }
        }

        Label(R.string.bills_due)
        OutlinedButton(onClick = { pickingDue = true }, enabled = !busy) { Text(formatDate(dueDate, today)) }
        Text(
            stringResource(R.string.bills_due_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.s1),
        )

        if (frequency != null) {
            OutlinedTextField(
                value = installments,
                onValueChange = {
                    installments = it.filter(Char::isDigit).take(3)
                    error = null
                },
                label = { Text(stringResource(R.string.bills_installments)) },
                supportingText = { Text(stringResource(R.string.bills_installments_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = spacing.s4),
            )
            OutlinedTextField(
                value = alreadyPaid,
                onValueChange = {
                    alreadyPaid = it.filter(Char::isDigit).take(3)
                    error = null
                },
                label = { Text(stringResource(R.string.bills_already_paid)) },
                supportingText = { Text(stringResource(R.string.bills_already_paid_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = spacing.s2),
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it
                error = null
            },
            label = { Text(stringResource(R.string.catat_note)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.s4),
        )

        error?.let {
            Text(
                stringResource(billErrorText(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = spacing.s3),
            )
        }

        Button(
            onClick = ::save,
            enabled = ready && !busy,
            modifier = Modifier.padding(top = spacing.s5).fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.manage_save)) }
        if (bill != null) {
            if (!bill.isFinished) {
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            workspace.bills.setActive(bill.id, !bill.active)
                            onChanged()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.padding(top = spacing.s2).fillMaxWidth().height(48.dp),
                ) { Text(stringResource(if (bill.active) R.string.bills_pause else R.string.bills_resume)) }
            }
            TextButton(onClick = { confirmingDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bills_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.catat_date_cancel)) }
    }

    if (confirmingDelete && bill != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.bills_delete_title)) },
            text = { Text(stringResource(R.string.bills_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    scope.launch {
                        workspace.bills.delete(bill.id)
                        onChanged()
                    }
                }) { Text(stringResource(R.string.bills_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.catat_date_cancel)) } },
        )
    }

    if (pickingDue) {
        DateDialog(
            initial = dueDate,
            // Tagihan yang sudah terlambat memang perlu dicatat, jadi tanggal lampau boleh dipilih.
            minimum = today.minusYears(BACK_YEARS),
            onPicked = {
                due = it.toEpochDay()
                error = null
            },
            onDismiss = { pickingDue = false },
        )
    }
}

private const val BACK_YEARS = 2L
