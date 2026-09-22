package com.roziqrizal.rizqflow.ui.ruang

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.Room
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.RuleDraft
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.ui.Notifier
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.RoomTile
import com.roziqrizal.rizqflow.ui.formatPercent
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SAMPLE = Money.rupiah(1_000_000)

/** Ruang aktif berurutan beserta aturannya saat layar dibuka; dimuat sekaligus supaya ukurannya selalu sama. */
private class LoadedRules(val rooms: List<Room>, val original: RuleDraft)

/**
 * S12 Aturan alokasi. Berlaku untuk pemasukan berikutnya; riwayat tidak berubah (potretnya sudah
 * tersimpan). Semua ruang bebas diubah dan total boleh sementara bukan 100%; **Simpan aturan** baru
 * aktif bila total tepat 100% dan ada perubahan. Keluar dengan perubahan menanyakan "Buang perubahan?".
 * [onSaved] menerima aturan sebelumnya supaya pemanggil bisa menawarkan Urungkan.
 */
@Composable
fun AturanScreen(
    workspace: AccountWorkspace,
    notifier: Notifier,
    onClose: () -> Unit,
    onSaved: (previous: List<AllocationRule>) -> Unit,
    onTemplateApplied: () -> Unit,
) {
    var loaded by remember { mutableStateOf<LoadedRules?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        val rooms = workspace.repositories.rooms
        val active = rooms.activeRooms()
        loaded = LoadedRules(active, RuleDraft.from(active, rooms.rules()))
    }

    val data = loaded
    when {
        data == null -> Box(Modifier.fillMaxSize())

        data.rooms.isEmpty() -> EmptyRules(
            onClose = onClose,
            onApply = {
                scope.launch {
                    if (workspace.rules.applyTemplate(RoomTemplate.TIGA_HAK) is LedgerResult.Success) {
                        onTemplateApplied()
                        reload++
                    }
                }
            },
        )

        else -> RulesEditor(workspace, notifier, data.rooms, data.original, onClose, onSaved)
    }
}

@Composable
private fun RulesEditor(
    workspace: AccountWorkspace,
    notifier: Notifier,
    roomList: List<Room>,
    base: RuleDraft,
    onClose: () -> Unit,
    onSaved: (previous: List<AllocationRule>) -> Unit,
) {
    // Isian bertahan saat layar diputar.
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(base) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changed = draft.hasChanges(base)
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current

    fun leave() {
        if (changed) confirmingDiscard = true else onClose()
    }
    BackHandler(enabled = !busy) { leave() }

    fun save() {
        if (busy || !draft.isBalanced || !changed) return
        busy = true
        scope.launch {
            val before = workspace.repositories.rooms.rules()
            val result = workspace.rules.changeRules(draft.toRules())
            busy = false
            when (result) {
                is LedgerResult.Success -> {
                    onSaved(before)
                    onClose()
                }

                is LedgerResult.Failure -> notifier.show(roomErrorText(context, result.error))
            }
        }
    }

    val sample = draft.sample(SAMPLE)
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
            TextButton(onClick = ::leave, enabled = !busy) { Text(stringResource(R.string.onb_back)) }
            Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = spacing.s1))
            Text(
                stringResource(R.string.rules_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.s2, bottom = spacing.s3),
            )

            // Ringkasan contoh: hanya bila total tidak melebihi 100%, karena pembagian seperti itu tidak sah.
            Text(stringResource(R.string.onb_persen_sample, formatRupiah(SAMPLE)), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .padding(vertical = spacing.s2)
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(7.dp)),
            ) {
                roomList.forEachIndexed { i, room ->
                    val weight = draft.shares[i]
                    if (weight > 0 && draft.totalBp <= 10_000) {
                        Box(Modifier.weight(weight.toFloat()).fillMaxSize().background(MaterialTheme.rizqflow.room(room.colorSlot)))
                    }
                }
            }
            Text(
                roomList.indices.joinToString("   ") { "${roomList[it].name} ${formatPercent(draft.shares[it])}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(modifier = Modifier.padding(top = spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s4)) {
                roomList.forEachIndexed { index, room ->
                    val basisPoints = draft.shares[index]
                    val percent = basisPoints / 100
                    val description = stringResource(R.string.onb_persen_slider, room.name)
                    val state = pluralStringResource(R.plurals.onb_percent_state, percent, percent)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s3)) {
                            RoomTile(room.iconKey, room.colorSlot, size = 36)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(room.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    sample?.shares?.getOrNull(index)?.let { formatRupiah(it.amount) } ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(formatPercent(basisPoints), style = MaterialTheme.typography.titleMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { draft = draft.step(index, -1) },
                                enabled = basisPoints > 0 && !busy,
                                modifier = Modifier.size(48.dp).semantics { contentDescription = "$lessLabel ${room.name}" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("−") }
                            Slider(
                                value = percent.toFloat(),
                                // Menggeser slider menetapkan persen bulat; persen pecahan yang tidak disentuh tetap utuh.
                                onValueChange = { draft = draft.set(index, it.roundToInt() * 100) },
                                valueRange = 0f..100f,
                                enabled = !busy,
                                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = spacing.s2)
                                    .semantics {
                                        this.contentDescription = description
                                        stateDescription = state
                                    },
                            )
                            OutlinedButton(
                                onClick = { draft = draft.step(index, 1) },
                                enabled = basisPoints < 10_000 && !busy,
                                modifier = Modifier.size(48.dp).semantics { contentDescription = "$moreLabel ${room.name}" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("+") }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.s3))
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.onb_persen_total), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
                    Text(formatPercent(draft.totalBp), style = MaterialTheme.typography.titleMedium)
                    Icon(
                        if (draft.isBalanced) RizqflowIcons.Centang else RizqflowIcons.Peringatan,
                        contentDescription = null,
                        tint = if (draft.isBalanced) MaterialTheme.colorScheme.primary else MaterialTheme.rizqflow.statusWarning,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (!draft.isBalanced) {
                val remaining = draft.remainingBp
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
                        if (remaining < 0) {
                            stringResource(R.string.rules_over, formatPercent(draft.totalBp), formatPercent(-remaining))
                        } else {
                            stringResource(R.string.rules_under, formatPercent(draft.totalBp), formatPercent(remaining))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Aturan lanjutan (prioritas, batas atas, sisa mengalir): terkunci Pro; paywall S21 belum dibuat.
            Row(
                modifier = Modifier
                    .padding(top = spacing.s4)
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { scope.launch { notifier.show(context.getString(R.string.rules_pro_note)) } }
                    .padding(vertical = spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s3),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.rules_advanced), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.rules_advanced_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(RizqflowIcons.PanahKanan, contentDescription = null)
            }
        }

        Button(
            onClick = ::save,
            enabled = draft.isBalanced && changed && !busy,
            modifier = Modifier.padding(horizontal = spacing.s5).padding(bottom = spacing.s3).fillMaxWidth().height(52.dp),
        ) { Text(stringResource(if (busy) R.string.onb_saving else R.string.rules_save)) }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.rules_discard_title)) },
            text = { Text(stringResource(R.string.rules_discard_body)) },
            confirmButton = { TextButton(onClick = { confirmingDiscard = false }) { Text(stringResource(R.string.rules_discard_keep)) } },
            dismissButton = {
                TextButton(onClick = {
                    confirmingDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.rules_discard_confirm), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun EmptyRules(onClose: () -> Unit, onApply: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = spacing.s5)
            .padding(top = spacing.s3),
    ) {
        BackHandler(onBack = onClose)
        TextButton(onClick = onClose) { Text(stringResource(R.string.onb_back)) }
        Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.rules_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = spacing.s3),
        )
        FilledTonalButton(onClick = onApply, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.room_apply_template)) }
    }
}

/** Menyimpan [RuleDraft] agar selamat dari layar diputar. */
private val DraftSaver = Saver<RuleDraft, List<String>>(
    save = { listOf(it.roomIds.joinToString(",") { r -> r.value }, it.shares.joinToString(",")) },
    restore = {
        RuleDraft(
            roomIds = it[0].split(",").filter(String::isNotEmpty).map(::RoomId),
            shares = it[1].split(",").filter(String::isNotEmpty).map(String::toInt),
        )
    },
)
