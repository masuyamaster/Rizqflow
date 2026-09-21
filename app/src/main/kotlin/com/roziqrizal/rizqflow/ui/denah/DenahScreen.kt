package com.roziqrizal.rizqflow.ui.denah

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.ledger.AttentionItem
import com.roziqrizal.rizqflow.domain.ledger.DenahLoader
import com.roziqrizal.rizqflow.domain.ledger.DenahOverview
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.RoomCard
import com.roziqrizal.rizqflow.domain.ledger.RoomStatus
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.RoomTile
import com.roziqrizal.rizqflow.ui.formatMonth
import com.roziqrizal.rizqflow.ui.formatPercent
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.theme.CappedFontScale
import com.roziqrizal.rizqflow.ui.theme.HERO_MAX_FONT_SCALE
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private val MonthSaver = Saver<YearMonth, String>(save = { it.toString() }, restore = { YearMonth.parse(it) })

/** Font sistem sebesar ini atau lebih membuat kartu ruang satu kolom (docs/design/README.md). */
private const val ONE_COLUMN_FONT_SCALE = 1.5f

/**
 * S05 Denah: menjawab "apakah setiap hak sudah ditunaikan bulan ini?". Rezeki bulan itu dan
 * pembagiannya, kartu tiap ruang dengan status, dan butir Perlu perhatian (hanya bulan berjalan).
 * Kartu ruang belum bisa dibuka; detail ruang (S11) menyusul. [refreshKey] berubah setiap ada
 * perubahan data dari luar layar ini.
 */
@Composable
fun DenahScreen(
    workspace: AccountWorkspace,
    refreshKey: Int,
    onCatat: () -> Unit,
    onOpenRules: () -> Unit,
    onChanged: () -> Unit,
    /** Mode demo aktif: header memberi penanda yang membuka sheet keluar. */
    demo: Boolean = false,
    onDemoClick: () -> Unit = {},
    onTryDemo: () -> Unit = {},
    /** Kartu ruang dibuka ke Detail ruang (S11) untuk bulan yang sedang dilihat. */
    onOpenRoom: (RoomId, YearMonth) -> Unit = { _, _ -> },
) {
    val today = remember { LocalDate.now() }
    val currentMonth = remember(today) { YearMonth.from(today) }
    var month by rememberSaveable(stateSaver = MonthSaver) { mutableStateOf(currentMonth) }
    var denah by remember { mutableStateOf<DenahOverview?>(null) }
    var localVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val spacing = MaterialTheme.spacing

    LaunchedEffect(month, refreshKey, localVersion) {
        val repos = workspace.repositories
        denah = DenahLoader(repos.rooms, repos.transactions).load(month, today)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s4)
            .padding(top = spacing.s4, bottom = 96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_denah), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (demo) {
                AssistChip(onClick = onDemoClick, label = { Text(stringResource(R.string.demo_badge)) })
            }
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(RizqflowIcons.PanahKiri, contentDescription = stringResource(R.string.tx_month_prev))
            }
            Text(formatMonth(month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < currentMonth) {
                Icon(RizqflowIcons.PanahKanan, contentDescription = stringResource(R.string.tx_month_next))
            }
        }

        val data = denah
        if (data == null) {
            Box(Modifier.fillMaxSize())
            return@Column
        }

        IncomeCard(data, isCurrent = month == currentMonth, onCatat = onCatat, onTryDemo = if (demo) null else onTryDemo)

        Text(stringResource(R.string.denah_rooms), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = spacing.s5, bottom = spacing.s2))
        if (!data.hasRooms) {
            NoRoomsCard(onApply = {
                scope.launch {
                    if (workspace.rules.applyTemplate(RoomTemplate.TIGA_HAK) is LedgerResult.Success) {
                        localVersion++
                        onChanged()
                    }
                }
            })
        } else {
            RoomGrid(data.cards) { onOpenRoom(it, month) }
        }

        if (month == currentMonth) {
            Text(stringResource(R.string.denah_attention), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = spacing.s5, bottom = spacing.s2))
            if (data.attention.isEmpty()) {
                Text(stringResource(R.string.denah_attention_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s2)) {
                    data.attention.forEach { item -> AttentionRow(item, onOpenRules) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- rezeki bulan ini

@Composable
private fun IncomeCard(data: DenahOverview, isCurrent: Boolean, onCatat: () -> Unit, onTryDemo: (() -> Unit)?) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .padding(top = spacing.s3)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(spacing.s4),
    ) {
        Text(stringResource(R.string.denah_income), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CappedFontScale(HERO_MAX_FONT_SCALE) {
            Text(formatRupiah(data.income), style = MaterialTheme.typography.displaySmall)
        }
        if (data.hasIncome) {
            AllocationBar(data)
            if (data.unallocated.isPositive) {
                Text(
                    stringResource(R.string.denah_unallocated, formatRupiah(data.unallocated)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = spacing.s2),
                )
            }
        } else {
            Text(
                stringResource(if (isCurrent) R.string.denah_empty_title else R.string.denah_empty_past),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = spacing.s3),
            )
            if (isCurrent) {
                Text(stringResource(R.string.denah_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onCatat, modifier = Modifier.padding(top = spacing.s3).fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.denah_empty_action)) }
                if (onTryDemo != null) TextButton(onClick = onTryDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.demo_try)) }
            }
        }
    }
}

/** Satu segmen per ruang menurut jatahnya; bagian yang belum dialirkan dibiarkan kosong. */
@Composable
private fun AllocationBar(data: DenahOverview) {
    val spacing = MaterialTheme.spacing
    val summary = data.cards.filter { it.allocated.isPositive }.joinToString(", ") { "${it.room.name} ${formatRupiah(it.allocated)}" }
    Row(
        modifier = Modifier
            .padding(top = spacing.s3)
            .fillMaxWidth()
            .height(16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .semantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        data.cards.filter { it.allocated.isPositive }.forEach { card ->
            Box(
                Modifier
                    .weight(card.allocated.minor.toFloat())
                    .fillMaxSize()
                    .background(MaterialTheme.rizqflow.room(card.room.colorSlot), RoundedCornerShape(4.dp)),
            )
        }
        if (data.unallocated.isPositive) Spacer(Modifier.weight(data.unallocated.minor.toFloat()))
    }
}

// ---------------------------------------------------------------------------------- kartu ruang

@Composable
private fun RoomGrid(cards: List<RoomCard>, onOpen: (RoomId) -> Unit) {
    val spacing = MaterialTheme.spacing
    val columns = if (LocalDensity.current.fontScale >= ONE_COLUMN_FONT_SCALE) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
        cards.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s3)) {
                row.forEach { card -> RoomCardView(card, Modifier.weight(1f)) { onOpen(card.room.id) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RoomCardView(card: RoomCard, modifier: Modifier, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val color = MaterialTheme.rizqflow.room(card.room.colorSlot)
    val progress = card.progressBp
    val percentText = progress?.let { formatPercent(it / 100 * 100) }
    val remainingText = when {
        progress == null -> stringResource(R.string.denah_no_allocation)
        card.isOver -> stringResource(R.string.denah_over, formatRupiah(-card.remaining.minor))
        else -> stringResource(R.string.denah_remaining, formatRupiah(card.remaining))
    }
    val statusLabel = stringResource(statusLabel(card.status))
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.s3)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            RoomTile(card.room.iconKey, card.room.colorSlot, size = 32)
            Text(card.room.name, style = MaterialTheme.typography.titleSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s3)) {
            CircularProgressIndicator(
                progress = { (progress ?: 0).coerceAtMost(10_000) / 10_000f },
                modifier = Modifier.size(40.dp),
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                strokeWidth = 5.dp,
            )
            Column {
                Text(percentText ?: "—", style = MaterialTheme.typography.titleMedium)
                Text(remainingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Status selalu ikon plus teks; warna hanya penguat.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s1)) {
            Icon(statusIcon(card.status), contentDescription = null, tint = statusTint(card.status), modifier = Modifier.size(16.dp))
            Text(statusLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

internal fun statusLabel(status: RoomStatus): Int = when (status) {
    RoomStatus.MENUNGGU -> R.string.status_menunggu
    RoomStatus.BERJALAN -> R.string.status_berjalan
    RoomStatus.TERPENUHI -> R.string.status_terpenuhi
    RoomStatus.PERLU_PERHATIAN -> R.string.status_perhatian
    RoomStatus.BELUM_TERCAPAI -> R.string.status_belum
}

internal fun statusIcon(status: RoomStatus): ImageVector = when (status) {
    RoomStatus.TERPENUHI -> RizqflowIcons.Centang
    RoomStatus.PERLU_PERHATIAN -> RizqflowIcons.Peringatan
    RoomStatus.BELUM_TERCAPAI -> RizqflowIcons.Lingkaran
    RoomStatus.MENUNGGU, RoomStatus.BERJALAN -> RizqflowIcons.Jam
}

@Composable
internal fun statusTint(status: RoomStatus): Color = when (status) {
    RoomStatus.TERPENUHI -> MaterialTheme.rizqflow.statusGood
    RoomStatus.PERLU_PERHATIAN -> MaterialTheme.rizqflow.statusWarning
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun NoRoomsCard(onApply: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(spacing.s4),
    ) {
        Text(stringResource(R.string.denah_no_rooms_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.denah_no_rooms_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onApply, modifier = Modifier.padding(top = spacing.s3).fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.room_apply_template)) }
    }
}

// ---------------------------------------------------------------------------------- perlu perhatian

@Composable
private fun AttentionRow(item: AttentionItem, onOpenRules: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val text = when (item) {
        is AttentionItem.RoomOverLimit -> stringResource(R.string.denah_att_over, item.room.name, formatRupiah(item.spent), formatRupiah(item.allocated))
        is AttentionItem.RoomNearLimit -> stringResource(R.string.denah_att_near, item.room.name, formatPercent(item.progressBp / 100 * 100))
        is AttentionItem.Unallocated -> stringResource(R.string.denah_att_unallocated, formatRupiah(item.amount))
    }
    // Rezeki yang belum dialirkan berujung di Aturan alokasi; butir ruang menunggu detail ruang (S11).
    val opensRules = item is AttentionItem.Unallocated
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .then(if (opensRules) Modifier.clickable(role = Role.Button, onClick = onOpenRules) else Modifier)
            .padding(spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s3),
    ) {
        Icon(RizqflowIcons.Peringatan, contentDescription = null, tint = MaterialTheme.rizqflow.statusWarning, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (opensRules) Icon(RizqflowIcons.PanahKanan, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}
