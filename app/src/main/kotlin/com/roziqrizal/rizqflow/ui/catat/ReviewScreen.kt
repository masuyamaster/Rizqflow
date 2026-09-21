package com.roziqrizal.rizqflow.ui.catat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.allocation.AllocationResult
import com.roziqrizal.rizqflow.domain.ledger.CatatContext
import com.roziqrizal.rizqflow.domain.ledger.CatatDraft
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.OneTimeSplit
import com.roziqrizal.rizqflow.domain.ledger.ShareEditor
import com.roziqrizal.rizqflow.ui.RoomTile
import com.roziqrizal.rizqflow.ui.formatPercent
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.theme.CaslonFamily
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.spacing
import kotlin.math.roundToInt

/**
 * S07 Pratinjau alokasi: inti produk. Pengguna melihat rezeki mengalir ke hak-hak sebelum
 * menyimpan. **Ubah sekali ini** membuka persentase khusus untuk pemasukan ini; aturan tetap.
 * Bila persentase kurang dari 100%, sisanya tampil jelas di baris "Belum dialirkan".
 */
@Composable
internal fun ReviewScreen(
    context: CatatContext,
    draft: CatatDraft,
    allocation: AllocationResult,
    split: OneTimeSplit?,
    busy: Boolean,
    error: LedgerError?,
    onBack: () -> Unit,
    onToggleEdit: (Boolean) -> Unit,
    onPercent: (index: Int, value: Int) -> Unit,
    onStep: (index: Int, delta: Int) -> Unit,
    onConfirm: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    // Baris mengikuti urutan aturan (prioritas); persentase dari aturan, atau dari pilihan sekali ini.
    val rows = if (split != null) {
        split.roomIds.mapIndexed { i, id -> Triple(id, split.percents[i] * 100, i) }
    } else {
        context.rules.mapIndexed { i, rule -> Triple(rule.roomId, rule.share.value, i) }
    }
    val lessLabel = stringResource(R.string.onb_persen_less)
    val moreLabel = stringResource(R.string.onb_persen_more)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s5)
            .padding(top = spacing.s3),
    ) {
        TextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.onb_back)) }
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = spacing.s1))

        Text(
            text = draft.source ?: stringResource(R.string.review_income),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.s3),
        )
        Text(formatRupiah(draft.amount), style = MaterialTheme.typography.displaySmall.copy(fontFamily = CaslonFamily))

        // Bar bersegmen: satu segmen per ruang, sebanding dengan bagian yang diterimanya.
        Row(
            modifier = Modifier
                .padding(vertical = spacing.s3)
                .fillMaxWidth()
                .height(16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)),
        ) {
            allocation.shares.forEachIndexed { i, share ->
                val room = context.rooms.firstOrNull { it.id == rows.getOrNull(i)?.first }
                if (share.amount.isPositive && room != null) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .weight(share.amount.minor.toFloat())
                            .fillMaxSize()
                            .background(MaterialTheme.rizqflow.room(room.colorSlot)),
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            Text(stringResource(R.string.review_no_rooms), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
            rows.forEachIndexed { i, (roomId, basisPoints, index) ->
                val room = context.rooms.firstOrNull { it.id == roomId } ?: return@forEachIndexed
                val percent = basisPoints / 100
                val isLast = index == rows.lastIndex
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s3)) {
                        RoomTile(room.iconKey, room.colorSlot, size = 32)
                        Text(room.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatPercent(basisPoints), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatRupiah(allocation.shares[i].amount), style = MaterialTheme.typography.titleSmall)
                    }
                    if (split != null && !isLast) {
                        val name = room.name
                        val description = stringResource(R.string.onb_persen_slider, name)
                        val state = pluralStringResource(R.plurals.onb_percent_state, percent, percent)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onStep(index, -1) },
                                enabled = percent > 0 && !busy,
                                modifier = Modifier.size(44.dp).semantics { contentDescription = "$lessLabel $name" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("−") }
                            Slider(
                                value = percent.toFloat(),
                                onValueChange = { onPercent(index, it.roundToInt()) },
                                valueRange = 0f..100f,
                                enabled = !busy,
                                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = spacing.s2)
                                    .semantics {
                                        contentDescription = description
                                        stateDescription = state
                                    },
                            )
                            OutlinedButton(
                                onClick = { onStep(index, 1) },
                                enabled = percent < ShareEditor.maxFor(split.percents, index) && !busy,
                                modifier = Modifier.size(44.dp).semantics { contentDescription = "$moreLabel $name" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("+") }
                        }
                    } else if (split != null) {
                        Text(
                            stringResource(R.string.onb_persen_auto),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 44.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = spacing.s3))
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.review_unallocated), style = MaterialTheme.typography.titleSmall)
            Text(formatRupiah(allocation.unallocated), style = MaterialTheme.typography.titleSmall)
        }

        if (rows.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.s4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_edit_once), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.review_edit_once_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = split != null, onCheckedChange = onToggleEdit, enabled = !busy)
            }
        }

        error?.let {
            Text(
                stringResource(errorText(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.s3).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(spacing.s4))
    }
    // Tombol utama menempel di bawah supaya tetap terlihat walau daftar ruang dan slider panjang.
    Button(
        onClick = onConfirm,
        enabled = !busy,
        modifier = Modifier.padding(horizontal = spacing.s5).padding(bottom = spacing.s3).fillMaxWidth().height(52.dp),
    ) {
        Text(stringResource(if (busy) R.string.onb_saving else if (rows.isEmpty()) R.string.catat_save else R.string.review_confirm))
    }
    }
}
