package com.roziqrizal.rizqflow.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.ledger.AmountPad
import com.roziqrizal.rizqflow.domain.ledger.OnboardingDraft
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplates
import com.roziqrizal.rizqflow.domain.ledger.ShareEditor
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.RoomTile
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.rizqflowFilterChipColors
import com.roziqrizal.rizqflow.ui.theme.spacing
import kotlin.math.roundToInt

private const val STEP_POLA = 0
private const val STEP_PERSEN = 1
private const val STEP_AKUN = 2

/** Nominal contoh di ringkasan S03. */
private val SAMPLE = Money.rupiah(1_000_000)

/**
 * Onboarding akun baru (S02 pola ruang, S03 persentase, S04 akun pertama). Isian disimpan lewat
 * `rememberSaveable` supaya selamat dari layar diputar. Mulai kosong melewati S03.
 */
@Composable
fun OnboardingFlow(
    busy: Boolean,
    failed: Boolean,
    onFinish: (OnboardingDraft) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(OnboardingDraft()) }
    var step by rememberSaveable { mutableIntStateOf(STEP_POLA) }

    fun next() {
        step = if (step == STEP_POLA && draft.template == RoomTemplate.KOSONG) STEP_AKUN else step + 1
    }

    fun back() {
        step = if (step == STEP_AKUN && draft.template == RoomTemplate.KOSONG) STEP_POLA else step - 1
    }

    BackHandler(enabled = step > STEP_POLA && !busy) { back() }

    when (step) {
        STEP_POLA -> PolaScreen(draft, onTemplate = { draft = draft.chooseTemplate(it) }, onNext = ::next)
        STEP_PERSEN -> PersenScreen(draft, onChange = { draft = it }, onBack = ::back, onNext = ::next)
        else -> AkunScreen(draft, busy = busy, failed = failed, onChange = { draft = it }, onBack = ::back, onFinish = { onFinish(draft) })
    }
}

private val DraftSaver = Saver<OnboardingDraft, List<Any>>(
    save = {
        listOf(it.template.name, it.percents.joinToString(","), it.accountKind.name, it.accountName, it.nameTouched, it.balanceDigits)
    },
    restore = {
        OnboardingDraft(
            template = RoomTemplate.valueOf(it[0] as String),
            percents = (it[1] as String).split(",").filter(String::isNotEmpty).map(String::toInt),
            accountKind = AccountKind.valueOf(it[2] as String),
            accountName = it[3] as String,
            nameTouched = it[4] as Boolean,
            balanceDigits = it[5] as String,
        )
    },
)

// ---------------------------------------------------------------------------------- kerangka

@Composable
private fun StepScaffold(
    title: String,
    step: Int,
    total: Int,
    subtitle: String,
    onBack: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s5, vertical = spacing.s4),
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.onb_back)) }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = if (onBack == null) spacing.s5 else spacing.s2))
        Text(
            text = stringResource(R.string.onb_step, step, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.s1),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.s2, bottom = spacing.s4),
        )
        content()
    }
}

@Composable
private fun roomDescription(name: String): String = when (name) {
    "Memberi" -> stringResource(R.string.onb_room_memberi)
    "Diri" -> stringResource(R.string.onb_room_diri)
    "Keluarga" -> stringResource(R.string.onb_room_keluarga)
    else -> ""
}

// ---------------------------------------------------------------------------------- S02

@Composable
private fun PolaScreen(draft: OnboardingDraft, onTemplate: (RoomTemplate) -> Unit, onNext: () -> Unit) {
    val spacing = MaterialTheme.spacing
    StepScaffold(
        title = stringResource(R.string.onb_pola_title),
        step = 1,
        total = draft.totalSteps,
        subtitle = stringResource(R.string.onb_pola_subtitle),
        onBack = null,
    ) {
        ChoiceCard(
            selected = draft.template == RoomTemplate.TIGA_HAK,
            title = stringResource(R.string.onb_tiga_title),
            body = stringResource(R.string.onb_tiga_body),
            onClick = { onTemplate(RoomTemplate.TIGA_HAK) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.padding(top = spacing.s3)) {
                RoomTemplates.tigaHak.forEach { room ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s3)) {
                        RoomTile(room.iconKey, room.colorSlot, size = 32)
                        Column {
                            Text(room.name, style = MaterialTheme.typography.titleSmall)
                            Text(roomDescription(room.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(spacing.s3))
        ChoiceCard(
            selected = draft.template == RoomTemplate.KOSONG,
            title = stringResource(R.string.onb_kosong_title),
            body = stringResource(R.string.onb_kosong_body),
            onClick = { onTemplate(RoomTemplate.KOSONG) },
        )
        Button(onClick = onNext, modifier = Modifier.padding(top = spacing.s5).fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.onb_next))
        }
    }
}

@Composable
private fun ChoiceCard(
    selected: Boolean,
    title: String,
    body: String,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val spacing = MaterialTheme.spacing
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(16.dp))
            .padding(spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(spacing.s3),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra()
        }
    }
}

// ---------------------------------------------------------------------------------- S03

@Composable
private fun PersenScreen(draft: OnboardingDraft, onChange: (OnboardingDraft) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val rooms = RoomTemplates.rooms(draft.template)
    val sample = draft.sample(SAMPLE)
    val lessLabel = stringResource(R.string.onb_persen_less)
    val moreLabel = stringResource(R.string.onb_persen_more)
    StepScaffold(
        title = stringResource(R.string.onb_persen_title),
        step = 2,
        total = draft.totalSteps,
        subtitle = stringResource(R.string.onb_persen_subtitle),
        onBack = onBack,
    ) {
        Text(stringResource(R.string.onb_persen_sample, formatRupiah(SAMPLE)), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .padding(vertical = spacing.s2)
                .fillMaxWidth()
                .height(14.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(7.dp)),
        ) {
            rooms.forEachIndexed { i, room ->
                val weight = draft.percents[i]
                if (weight > 0) Box(Modifier.weight(weight.toFloat()).fillMaxSize().background(MaterialTheme.rizqflow.room(room.colorSlot)))
            }
        }
        Text(
            text = rooms.indices.joinToString("   ") { "${rooms[it].name} ${draft.percents[it]}%" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(modifier = Modifier.padding(top = spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s4)) {
            rooms.forEachIndexed { index, room ->
                val isLast = index == rooms.lastIndex
                val percent = draft.percents[index]
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s3)) {
                        RoomTile(room.iconKey, room.colorSlot)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(room.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (isLast) {
                                    stringResource(R.string.onb_persen_auto) + " \u00B7 " + formatRupiah(sample.shares[index].amount)
                                } else {
                                    formatRupiah(sample.shares[index].amount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("$percent%", style = MaterialTheme.typography.titleMedium)
                    }
                    if (!isLast) {
                        val name = room.name
                        val description = stringResource(R.string.onb_persen_slider, name)
                        val state = pluralStringResource(R.plurals.onb_percent_state, percent, percent)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onChange(draft.stepPercent(index, -1)) },
                                enabled = percent > 0,
                                modifier = Modifier.size(44.dp).semantics { contentDescription = "$lessLabel $name" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("−") }
                            Slider(
                                value = percent.toFloat(),
                                onValueChange = { onChange(draft.setPercent(index, it.roundToInt())) },
                                valueRange = 0f..100f,
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
                                onClick = { onChange(draft.stepPercent(index, 1)) },
                                enabled = percent < ShareEditor.maxFor(draft.percents, index),
                                modifier = Modifier.size(44.dp).semantics { contentDescription = "$moreLabel $name" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("+") }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(top = spacing.s4)
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.onb_persen_total), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
                Text("${draft.percents.sum()}%", style = MaterialTheme.typography.titleMedium)
                Icon(RizqflowIcons.Centang, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Button(onClick = onNext, modifier = Modifier.padding(top = spacing.s4).fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.onb_next))
        }
    }
}

// ---------------------------------------------------------------------------------- S04

@Composable
private fun AkunScreen(
    draft: OnboardingDraft,
    busy: Boolean,
    failed: Boolean,
    onChange: (OnboardingDraft) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    StepScaffold(
        title = stringResource(R.string.onb_akun_title),
        step = draft.accountStepNumber,
        total = draft.totalSteps,
        subtitle = stringResource(R.string.onb_akun_subtitle),
        onBack = if (busy) null else onBack,
    ) {
        Text(stringResource(R.string.onb_akun_kind), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.padding(vertical = spacing.s2),
            horizontalArrangement = Arrangement.spacedBy(spacing.s2),
        ) {
            listOf(
                AccountKind.CASH to R.string.onb_kind_cash,
                AccountKind.BANK to R.string.onb_kind_bank,
                AccountKind.EWALLET to R.string.onb_kind_ewallet,
            ).forEach { (kind, label) ->
                FilterChip(
                    selected = draft.accountKind == kind,
                    onClick = { onChange(draft.chooseKind(kind)) },
                    enabled = !busy,
                    label = { Text(stringResource(label)) },
                    colors = rizqflowFilterChipColors(),
                )
            }
        }
        val hint = when (draft.accountKind) {
            AccountKind.CASH -> R.string.onb_hint_cash
            AccountKind.BANK -> R.string.onb_hint_bank
            AccountKind.EWALLET -> R.string.onb_hint_ewallet
        }
        OutlinedTextField(
            value = draft.accountName,
            onValueChange = { onChange(draft.typeName(it)) },
            label = { Text(stringResource(R.string.onb_akun_name)) },
            placeholder = { Text(stringResource(hint)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.onb_akun_balance), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = spacing.s4))
        Text(
            text = formatRupiah(draft.openingBalance),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.s2)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            stringResource(R.string.onb_akun_balance_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountKeypad(enabled = !busy, onKey = { onChange(draft.pressKey(it)) }, modifier = Modifier.padding(top = spacing.s3))

        if (failed) {
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
                Text(stringResource(R.string.onb_failed), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = onFinish,
            enabled = draft.canFinish && !busy,
            modifier = Modifier.padding(top = spacing.s4).fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(if (busy) R.string.onb_saving else R.string.onb_finish))
        }
    }
}

/** Papan angka 4x3: 1 sampai 9, 000, 0, dan hapus. Aturannya di [AmountPad]. */
@Composable
internal fun AmountKeypad(enabled: Boolean, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(AmountPad.TRIPLE_ZERO, "0", AmountPad.BACK))
    val deleteLabel = stringResource(R.string.keypad_delete)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.s2)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { onKey(key) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics { if (key == AmountPad.BACK) contentDescription = deleteLabel },
                    ) {
                        Text(if (key == AmountPad.BACK) "⌫" else key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
