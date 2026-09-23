package com.roziqrizal.rizqflow.ui.recurring

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.ledger.CatatContext
import com.roziqrizal.rizqflow.domain.ledger.CatatContextLoader
import com.roziqrizal.rizqflow.domain.ledger.INCOME_SOURCES
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.recurring.Frequency
import com.roziqrizal.rizqflow.domain.recurring.RecurringDraft
import com.roziqrizal.rizqflow.domain.recurring.RecurringRow
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.catat.errorText
import com.roziqrizal.rizqflow.ui.formatDate
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.theme.rizqflowFilterChipColors
import com.roziqrizal.rizqflow.ui.theme.rizqflowSegmentedColors
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Sheet yang sedang terbuka: null tertutup, [RecurringSheet.New] tambah, atau [RecurringSheet.Edit] ubah. */
private sealed interface RecurringSheet {
    data object New : RecurringSheet

    data class Edit(val row: RecurringRow) : RecurringSheet
}

/**
 * Transaksi berulang (Tahap 10, Gratis): daftar aturan, tambah, ubah, jeda, dan hapus. Aturan yang
 * tanggalnya sudah tiba dicatat sendiri oleh `RecurringService.runDue` saat aplikasi dibuka; layar
 * ini menjalankannya juga sehabis menyimpan supaya kemunculan pertama yang jatuh hari ini langsung
 * tercatat. Perubahan diteruskan lewat [onChanged] supaya layar lain memuat ulang.
 */
@Composable
fun RecurringScreen(workspace: AccountWorkspace, onClose: () -> Unit, onChanged: () -> Unit) {
    var rows by remember { mutableStateOf<List<RecurringRow>?>(null) }
    var sheet by remember { mutableStateOf<RecurringSheet?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val spacing = MaterialTheme.spacing
    val today = remember { LocalDate.now() }
    BackHandler(onBack = onClose)

    LaunchedEffect(version) { rows = workspace.recurring.list() }
    val data = rows

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TextButton(onClick = onClose, modifier = Modifier.padding(start = spacing.s3, top = spacing.s2)) { Text(stringResource(R.string.onb_back)) }
        Text(
            stringResource(R.string.recurring_title),
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
                    Text(stringResource(R.string.recurring_empty), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.recurring_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.s2),
                    )
                }
                data.forEach { row ->
                    RuleRow(row, today) { sheet = RecurringSheet.Edit(row) }
                    HorizontalDivider()
                }
                Button(
                    onClick = { sheet = RecurringSheet.New },
                    modifier = Modifier.padding(top = spacing.s4).fillMaxWidth().height(52.dp),
                ) {
                    Icon(RizqflowIcons.Tambah, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.recurring_add), modifier = Modifier.padding(start = spacing.s2))
                }
            }
        }
    }

    sheet?.let { current -> RuleSheet(workspace, current, today, onDismiss = { sheet = null }, onChanged = {
        sheet = null
        version++
        onChanged()
    }) }
}

@Composable
private fun RuleRow(row: RecurringRow, today: LocalDate, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val rule = row.rule
    val title = rule.note
        ?: when (rule.kind) {
            TransactionKind.EXPENSE -> row.categoryName.orEmpty()
            TransactionKind.INCOME -> rule.incomeSource ?: stringResource(R.string.catat_tab_income)
            TransactionKind.TRANSFER -> stringResource(R.string.catat_tab_transfer) + " → " + row.toAccountName.orEmpty()
        }
    val where = when (rule.kind) {
        TransactionKind.EXPENSE -> "${row.roomName.orEmpty()}, ${row.categoryName.orEmpty()}, ${row.accountName}"
        TransactionKind.INCOME -> stringResource(R.string.recurring_into, row.accountName)
        TransactionKind.TRANSFER -> "${row.accountName} → ${row.toAccountName.orEmpty()}"
    }
    val status = when {
        rule.isFinished -> stringResource(R.string.recurring_status_finished)
        !rule.active -> stringResource(R.string.recurring_status_paused)
        else -> stringResource(R.string.recurring_row_next, formatDate(rule.nextDue, today))
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.recurring_row_meta, stringResource(frequencyLabel(rule.frequency)), status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!row.usable && rule.active && !rule.isFinished) {
                Text(stringResource(R.string.recurring_status_unusable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(formatRupiah(rule.amount), style = MaterialTheme.typography.titleSmall)
        Icon(RizqflowIcons.PanahKanan, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

private fun frequencyLabel(frequency: Frequency): Int = when (frequency) {
    Frequency.DAILY -> R.string.recurring_freq_daily
    Frequency.WEEKLY -> R.string.recurring_freq_weekly
    Frequency.MONTHLY -> R.string.recurring_freq_monthly
}

private fun recurringErrorText(error: LedgerError): Int = when (error) {
    LedgerError.INVALID_SCHEDULE -> R.string.recurring_err_schedule
    else -> errorText(error)
}

private fun digitsOnly(text: String): String = text.filter(Char::isDigit).take(12)

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    workspace: AccountWorkspace,
    sheet: RecurringSheet,
    today: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    var context by remember { mutableStateOf<CatatContext?>(null) }
    LaunchedEffect(Unit) {
        context = CatatContextLoader(workspace.repositories.accounts, workspace.repositories.rooms, workspace.repositories.transactions).load()
    }
    val loaded = context ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        RuleForm(workspace, existing = (sheet as? RecurringSheet.Edit)?.row, context = loaded, today = today, onCancel = onDismiss, onChanged = onChanged)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleForm(
    workspace: AccountWorkspace,
    existing: RecurringRow?,
    context: CatatContext,
    today: LocalDate,
    onCancel: () -> Unit,
    onChanged: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val rule = existing?.rule
    val defaultRoom = rule?.roomId ?: context.lastRoomId?.takeIf { id -> context.rooms.any { it.id == id } } ?: context.rooms.firstOrNull()?.id
    var kind by rememberSaveable { mutableStateOf(rule?.kind ?: TransactionKind.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf(rule?.amount?.minor?.toString().orEmpty()) }
    var accountId by rememberSaveable { mutableStateOf(rule?.accountId?.value ?: context.lastAccountId?.value ?: context.accounts.firstOrNull()?.id?.value) }
    var toAccountId by rememberSaveable { mutableStateOf(rule?.toAccountId?.value) }
    var roomId by rememberSaveable { mutableStateOf(defaultRoom?.value) }
    var categoryId by rememberSaveable { mutableStateOf(rule?.categoryId?.value ?: defaultRoom?.let(context::defaultCategory)?.value) }
    var source by rememberSaveable { mutableStateOf(rule?.incomeSource ?: INCOME_SOURCES.first()) }
    var note by rememberSaveable { mutableStateOf(rule?.note.orEmpty()) }
    var frequency by rememberSaveable { mutableStateOf(rule?.frequency ?: Frequency.MONTHLY) }
    var start by rememberSaveable { mutableLongStateOf((rule?.startDate ?: today).toEpochDay()) }
    var end by rememberSaveable { mutableLongStateOf(rule?.endDate?.toEpochDay() ?: NO_DATE) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LedgerError?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val startDate = LocalDate.ofEpochDay(start)
    val endDate = end.takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay)
    val amountValue = amount.toLongOrNull() ?: 0L
    val ready = amountValue > 0 && accountId != null && when (kind) {
        TransactionKind.INCOME -> true
        TransactionKind.EXPENSE -> roomId != null && categoryId != null
        TransactionKind.TRANSFER -> toAccountId != null && toAccountId != accountId
    }

    fun save() {
        busy = true
        scope.launch {
            val draft = RecurringDraft(
                kind = kind,
                amount = Money.rupiah(amountValue),
                accountId = AccountId(accountId.orEmpty()),
                toAccountId = toAccountId?.let(::AccountId),
                roomId = roomId?.let(::RoomId),
                categoryId = categoryId?.let(::CategoryId),
                incomeSource = source,
                note = note,
                frequency = frequency,
                startDate = startDate,
                endDate = endDate,
            )
            val result = if (rule == null) workspace.recurring.create(draft, today) else workspace.recurring.update(rule.id, draft, today)
            when (result) {
                is LedgerResult.Success -> {
                    // Kemunculan pertama yang jatuh hari ini (atau yang tertahan lalu diperbaiki) langsung dicatat.
                    workspace.recurring.runDue(today)
                    onChanged()
                }

                is LedgerResult.Failure -> error = result.error
            }
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.s5).padding(bottom = spacing.s5)) {
        Text(stringResource(if (rule == null) R.string.recurring_new else R.string.recurring_edit), style = MaterialTheme.typography.titleLarge)

        val kinds = listOf(
            TransactionKind.INCOME to R.string.catat_tab_income,
            TransactionKind.EXPENSE to R.string.catat_tab_expense,
            TransactionKind.TRANSFER to R.string.catat_tab_transfer,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = spacing.s3)) {
            kinds.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = kind == value,
                    onClick = {
                        kind = value
                        error = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                    colors = rizqflowSegmentedColors(),
                    enabled = value != TransactionKind.TRANSFER || context.canTransfer,
                    label = { Text(stringResource(label), maxLines = 1) },
                )
            }
        }

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

        when (kind) {
            TransactionKind.INCOME -> {
                Label(R.string.catat_source)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
                    (INCOME_SOURCES + listOfNotNull(source.takeIf { it.isNotBlank() && it !in INCOME_SOURCES })).forEach { option ->
                        FilterChip(selected = source == option, onClick = { source = option }, label = { Text(option) }, colors = rizqflowFilterChipColors())
                    }
                }
                Label(R.string.catat_account_to)
                AccountChips(context, accountId) { accountId = it }
            }

            TransactionKind.EXPENSE -> {
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
            }

            TransactionKind.TRANSFER -> {
                Label(R.string.catat_from)
                AccountChips(context, accountId) { accountId = it }
                Label(R.string.catat_to)
                AccountChips(context, toAccountId) { toAccountId = it }
            }
        }

        Label(R.string.recurring_frequency)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            Frequency.entries.forEach { option ->
                FilterChip(selected = frequency == option, onClick = { frequency = option }, label = { Text(stringResource(frequencyLabel(option))) }, colors = rizqflowFilterChipColors())
            }
        }

        Label(R.string.recurring_start)
        OutlinedButton(onClick = { pickingStart = true }, enabled = !busy) { Text(formatDate(startDate, today)) }
        Text(
            stringResource(R.string.recurring_start_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.s1),
        )

        Label(R.string.recurring_end)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            OutlinedButton(onClick = { pickingEnd = true }, enabled = !busy) {
                Text(if (endDate != null) formatDate(endDate, today) else stringResource(R.string.recurring_end_none))
            }
            if (endDate != null) TextButton(onClick = { end = NO_DATE }) { Text(stringResource(R.string.recurring_end_clear)) }
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
                stringResource(recurringErrorText(it)),
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
        if (rule != null) {
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        workspace.recurring.setActive(rule.id, !rule.active, today)
                        workspace.recurring.runDue(today)
                        onChanged()
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(top = spacing.s2).fillMaxWidth().height(48.dp),
            ) { Text(stringResource(if (rule.active) R.string.recurring_pause else R.string.recurring_resume)) }
            TextButton(onClick = { confirmingDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recurring_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.catat_date_cancel)) }
    }

    if (confirmingDelete && rule != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.recurring_delete_title)) },
            text = { Text(stringResource(R.string.recurring_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    scope.launch {
                        workspace.recurring.delete(rule.id)
                        onChanged()
                    }
                }) { Text(stringResource(R.string.recurring_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.catat_date_cancel)) } },
        )
    }

    if (pickingStart) {
        DateDialog(
            initial = startDate,
            // Yang lewat dicatat sendiri oleh pengguna; aturan hanya mengejar sesudah tanggal awalnya.
            minimum = if (rule != null && startDate < today) startDate else today,
            onPicked = {
                start = it.toEpochDay()
                if (end != NO_DATE && end < start) end = NO_DATE
                error = null
            },
            onDismiss = { pickingStart = false },
        )
    }
    if (pickingEnd) {
        DateDialog(
            initial = endDate ?: startDate,
            minimum = startDate,
            onPicked = {
                end = it.toEpochDay()
                error = null
            },
            onDismiss = { pickingEnd = false },
        )
    }
}

private const val NO_DATE = Long.MIN_VALUE

@Composable
private fun Label(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = MaterialTheme.spacing.s4, bottom = MaterialTheme.spacing.s1),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(context: CatatContext, selected: String?, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2)) {
        context.accounts.forEach { account ->
            FilterChip(selected = selected == account.id.value, onClick = { onPick(account.id.value) }, label = { Text(account.name) }, colors = rizqflowFilterChipColors())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, minimum: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val minimumMillis = minimum.toPickerMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(initial, minimum).toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minimumMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(it.fromPickerMillis()) }
                onDismiss()
            }) { Text(stringResource(R.string.catat_date_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catat_date_cancel)) } },
    ) { DatePicker(state = state) }
}
