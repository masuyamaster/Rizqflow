package com.roziqrizal.rizqflow.ui.transaksi

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.ledger.CatatContext
import com.roziqrizal.rizqflow.domain.ledger.CatatContextLoader
import com.roziqrizal.rizqflow.domain.ledger.ListFilter
import com.roziqrizal.rizqflow.domain.ledger.TransactionLister
import com.roziqrizal.rizqflow.domain.ledger.TransactionListing
import com.roziqrizal.rizqflow.domain.ledger.TransactionRow
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.ui.Notifier
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.RoomTile
import com.roziqrizal.rizqflow.ui.formatDate
import com.roziqrizal.rizqflow.ui.formatMonth
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.rizqflowFilterChipColors
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/**
 * S08 Daftar transaksi: satu bulan, dikelompokkan per tanggal, dengan cari, filter jenis, dan sheet
 * saringan akun/ruang/kategori. Ketuk baris membuka S09. [refreshKey] berubah setiap ada transaksi
 * yang disimpan, diubah, atau dihapus, supaya daftar dimuat ulang.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransaksiScreen(
    workspace: AccountWorkspace,
    refreshKey: Int,
    notifier: Notifier,
    onOpen: (TransactionId) -> Unit,
    onQuickCatat: () -> Unit,
    /** Transaksi pinjaman (utang-piutang) tidak dibuka di Detail; ketukannya membuka layar Utang-piutang. */
    onOpenLoan: () -> Unit = {},
) {
    val today = remember { LocalDate.now() }
    var filter by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(ListFilter(YearMonth.from(today))) }
    var listing by remember { mutableStateOf<TransactionListing?>(null) }
    var context by remember { mutableStateOf<CatatContext?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var emptyDayHint by remember { mutableStateOf(false) }
    val spacing = MaterialTheme.spacing
    val scope = rememberCoroutineScope()

    LaunchedEffect(filter, refreshKey) {
        val repos = workspace.repositories
        listing = TransactionLister(repos.accounts, repos.rooms, repos.transactions).list(filter)
    }
    LaunchedEffect(refreshKey) {
        val repos = workspace.repositories
        context = CatatContextLoader(repos.accounts, repos.rooms, repos.transactions).load()
    }
    // Petunjuk hari kosong (S08): hanya di daftar tak difilter akun, sama seperti prototipe.
    LaunchedEffect(refreshKey) {
        emptyDayHint = workspace.reminder.shouldShowEmptyDayHint(today)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = spacing.s4).padding(top = spacing.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tx_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { filter = filter.copy(month = filter.month.minusMonths(1)) }) {
                    Icon(RizqflowIcons.PanahKiri, contentDescription = stringResource(R.string.tx_month_prev))
                }
                Text(formatMonth(filter.month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                IconButton(
                    onClick = { filter = filter.copy(month = filter.month.plusMonths(1)) },
                    enabled = filter.month < YearMonth.from(today),
                ) { Icon(RizqflowIcons.PanahKanan, contentDescription = stringResource(R.string.tx_month_next)) }
            }

            OutlinedTextField(
                value = filter.query,
                onValueChange = { filter = filter.copy(query = it) },
                placeholder = { Text(stringResource(R.string.tx_search)) },
                leadingIcon = { Icon(RizqflowIcons.Cari, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { sheetOpen = true }) {
                        BadgedBox(badge = { if (filter.sheetFilterCount > 0) Badge { Text(filter.sheetFilterCount.toString()) } }) {
                            Icon(RizqflowIcons.Saring, contentDescription = stringResource(R.string.tx_filter_open))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(top = spacing.s2),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.padding(top = spacing.s2)) {
                KindChip(R.string.tx_filter_all, selected = filter.kinds.isEmpty()) { filter = filter.copy(kinds = emptySet()) }
                KindChip(R.string.tx_filter_income, selected = filter.kinds == setOf(TransactionKind.INCOME)) { filter = filter.copy(kinds = setOf(TransactionKind.INCOME)) }
                KindChip(R.string.tx_filter_expense, selected = filter.kinds == setOf(TransactionKind.EXPENSE)) { filter = filter.copy(kinds = setOf(TransactionKind.EXPENSE)) }
                KindChip(R.string.tx_filter_transfer, selected = filter.kinds == setOf(TransactionKind.TRANSFER)) { filter = filter.copy(kinds = setOf(TransactionKind.TRANSFER)) }
            }

            // Saringan dari sheet tampil sebagai chip yang bisa dilepas satu per satu.
            val ctx = context
            if (ctx != null && filter.sheetFilterCount > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.padding(top = spacing.s2)) {
                    filter.accountId?.let { id ->
                        ActiveFilterChip(stringResource(R.string.tx_active_account, ctx.accounts.firstOrNull { it.id == id }?.name.orEmpty())) { filter = filter.copy(accountId = null) }
                    }
                    filter.roomId?.let { id ->
                        ActiveFilterChip(stringResource(R.string.tx_active_room, ctx.rooms.firstOrNull { it.id == id }?.name.orEmpty())) {
                            filter = filter.copy(roomId = null, categoryId = null)
                        }
                    }
                    filter.categoryId?.let { id ->
                        val name = ctx.categories.values.flatten().firstOrNull { it.id == id }?.name.orEmpty()
                        ActiveFilterChip(stringResource(R.string.tx_active_category, name)) { filter = filter.copy(categoryId = null) }
                    }
                }
            }

            if (emptyDayHint && filter.accountId == null) {
                val dismissedMessage = stringResource(R.string.tx_empty_day_dismissed)
                EmptyDayHintBanner(
                    onCatat = onQuickCatat,
                    onDismiss = {
                        emptyDayHint = false
                        scope.launch {
                            workspace.reminder.dismissEmptyDayHint(today)
                            notifier.show(dismissedMessage)
                        }
                    },
                )
            }
        }

        val shown = listing
        when {
            shown == null -> Box(Modifier.fillMaxSize())
            shown.isEmpty -> EmptyState(narrowed = filter.isNarrowed)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = spacing.s4, end = spacing.s4, top = spacing.s3, bottom = 96.dp),
            ) {
                shown.groups.forEach { group ->
                    item(key = "h-${group.date}") {
                        Text(
                            formatDate(group.date, today),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = spacing.s3, bottom = spacing.s1),
                        )
                    }
                    items(group.rows, key = { it.transaction.id.value }) { row -> TransactionItem(row) { if (row.transaction.kind.isLoan()) onOpenLoan() else onOpen(row.transaction.id) } }
                }
            }
        }
    }

    if (sheetOpen && context != null) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            FilterSheet(context!!, filter, onChange = { filter = it }, onDone = { sheetOpen = false })
        }
    }
}

@Composable
private fun KindChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) }, colors = rizqflowFilterChipColors())
}

@Composable
private fun ActiveFilterChip(text: String, onClear: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClear,
        label = { Text(text) },
        trailingIcon = { Icon(RizqflowIcons.Tutup, contentDescription = stringResource(R.string.tx_filter_remove), modifier = Modifier.size(16.dp)) },
        colors = rizqflowFilterChipColors(),
    )
}

@Composable
private fun EmptyState(narrowed: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.s5), contentAlignment = Alignment.TopCenter) {
        Text(
            text = stringResource(if (narrowed) R.string.tx_no_match else R.string.tx_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.spacing.s6).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Petunjuk hari kosong (S08, Tahap 6): banner lembut, tanpa streak dan tanpa warna peringatan —
 * beda nada dari [WarningBanner] jatah terlampaui di Catat, supaya tidak terasa menghakimi.
 */
@Composable
private fun EmptyDayHintBanner(onCatat: () -> Unit, onDismiss: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .padding(top = spacing.s2)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(RizqflowIcons.Jam, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.tx_empty_day_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.padding(top = spacing.s1)) {
                TextButton(onClick = onCatat, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.s2)) {
                    Text(stringResource(R.string.tx_empty_day_catat))
                }
                TextButton(onClick = onDismiss, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.s2)) {
                    Text(stringResource(R.string.tx_empty_day_none))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- baris

@Composable
private fun TransactionItem(row: TransactionRow, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val tx = row.transaction
    val title = when (tx.kind) {
        TransactionKind.EXPENSE -> tx.note ?: row.categoryName ?: stringResource(R.string.tx_expense_default)
        TransactionKind.INCOME -> tx.note ?: tx.incomeSource ?: stringResource(R.string.tx_income_default)
        TransactionKind.TRANSFER -> tx.note ?: stringResource(R.string.tx_transfer_default)
        TransactionKind.LOAN_OUT -> tx.note ?: stringResource(R.string.tx_loan_out_default)
        TransactionKind.LOAN_IN -> tx.note ?: stringResource(R.string.tx_loan_in_default)
    }
    val subtitle = when (tx.kind) {
        TransactionKind.EXPENSE -> listOfNotNull(row.categoryName, row.accountName.ifEmpty { null }).joinToString(" · ")
        TransactionKind.INCOME -> {
            val count = row.allocatedRoomCount ?: 0
            val allocated = if (count > 0) stringResource(R.string.tx_sub_allocated, count) else stringResource(R.string.tx_sub_unallocated)
            listOfNotNull(allocated, row.accountName.ifEmpty { null }).joinToString(" · ")
        }

        TransactionKind.TRANSFER -> "${row.accountName} → ${row.toAccountName.orEmpty()}"
        TransactionKind.LOAN_OUT, TransactionKind.LOAN_IN -> row.accountName
    }
    val amountText = formatRupiah(tx.amount).removePrefix("Rp ").let {
        when (tx.kind) {
            TransactionKind.INCOME -> "+$it"
            TransactionKind.EXPENSE, TransactionKind.LOAN_OUT -> "−$it"
            TransactionKind.LOAN_IN -> "+$it"
            TransactionKind.TRANSFER -> it
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(row)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            amountText,
            style = MaterialTheme.typography.titleSmall,
            color = if (tx.kind == TransactionKind.INCOME) MaterialTheme.rizqflow.statusGood else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RowIcon(row: TransactionRow) {
    when (row.transaction.kind) {
        TransactionKind.EXPENSE -> RoomTile(row.room?.iconKey.orEmpty(), row.room?.colorSlot ?: 1, size = 40)
        TransactionKind.INCOME -> GlyphTile(RizqflowIcons.PanahAtas, MaterialTheme.rizqflow.statusGood)
        TransactionKind.TRANSFER -> GlyphTile(RizqflowIcons.Transfer, MaterialTheme.colorScheme.onSurfaceVariant)
        TransactionKind.LOAN_OUT -> GlyphTile(RizqflowIcons.PanahBawah, MaterialTheme.colorScheme.onSurfaceVariant)
        TransactionKind.LOAN_IN -> GlyphTile(RizqflowIcons.PanahAtas, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun TransactionKind.isLoan(): Boolean = this == TransactionKind.LOAN_OUT || this == TransactionKind.LOAN_IN

@Composable
private fun GlyphTile(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier.size(40.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
}

// ---------------------------------------------------------------------------------- sheet saringan

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(context: CatatContext, filter: ListFilter, onChange: (ListFilter) -> Unit, onDone: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s5)
            .padding(bottom = spacing.s5),
    ) {
        Text(stringResource(R.string.tx_filter_title), style = MaterialTheme.typography.titleLarge)

        SheetLabel(R.string.tx_filter_account)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            context.accounts.forEach { account ->
                FilterChip(
                    selected = filter.accountId == account.id,
                    onClick = { onChange(filter.copy(accountId = if (filter.accountId == account.id) null else account.id)) },
                    label = { Text(account.name) },
                    colors = rizqflowFilterChipColors(),
                )
            }
        }

        SheetLabel(R.string.tx_filter_room)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            context.rooms.forEach { room ->
                FilterChip(
                    selected = filter.roomId == room.id,
                    // Berganti ruang melepas kategori, karena kategori milik satu ruang.
                    onClick = { onChange(filter.copy(roomId = if (filter.roomId == room.id) null else room.id, categoryId = null)) },
                    label = { Text(room.name) },
                    colors = rizqflowFilterChipColors(),
                )
            }
        }

        filter.roomId?.let { roomId ->
            SheetLabel(R.string.tx_filter_category)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
                context.categoriesOf(roomId).forEach { category ->
                    FilterChip(
                        selected = filter.categoryId == category.id,
                        onClick = { onChange(filter.copy(categoryId = if (filter.categoryId == category.id) null else category.id)) },
                        label = { Text(category.name) },
                        colors = rizqflowFilterChipColors(),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.s5), horizontalArrangement = Arrangement.spacedBy(spacing.s3)) {
            TextButton(
                onClick = { onChange(filter.copy(accountId = null, roomId = null, categoryId = null)) },
                enabled = filter.sheetFilterCount > 0,
            ) { Text(stringResource(R.string.tx_filter_clear)) }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tx_filter_done)) }
        }
    }
}

@Composable
private fun SheetLabel(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = MaterialTheme.spacing.s4, bottom = MaterialTheme.spacing.s1),
    )
}

/** Menyimpan [ListFilter] agar selamat dari layar diputar; nilai kosong ditulis sebagai teks kosong. */
private val FilterSaver = Saver<ListFilter, List<String>>(
    save = {
        listOf(it.month.toString(), it.kinds.joinToString(",") { k -> k.name }, it.query, it.accountId?.value.orEmpty(), it.roomId?.value.orEmpty(), it.categoryId?.value.orEmpty())
    },
    restore = {
        ListFilter(
            month = YearMonth.parse(it[0]),
            kinds = it[1].split(",").filter(String::isNotEmpty).map(TransactionKind::valueOf).toSet(),
            query = it[2],
            accountId = it[3].ifEmpty { null }?.let(::AccountId),
            roomId = it[4].ifEmpty { null }?.let(::RoomId),
            categoryId = it[5].ifEmpty { null }?.let(::CategoryId),
        )
    },
)
