package com.roziqrizal.rizqflow.ui.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.ledger.MonthComparison
import com.roziqrizal.rizqflow.domain.ledger.MonthlyReport
import com.roziqrizal.rizqflow.domain.ledger.ReportLoader
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.formatMonth
import com.roziqrizal.rizqflow.ui.formatPercent
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import java.time.YearMonth
import kotlin.math.abs

private val MonthSaver = Saver<YearMonth, String>(save = { it.toString() }, restore = { YearMonth.parse(it) })

/**
 * Laporan dasar (S36, Tahap 10, Gratis): ringkasan bulanan dengan perbandingan bulan lalu — nilai
 * yang paling cepat dirasakan menurut riset pasar. Laporan dan insight bulanan/tahunan berupa PDF
 * tetap Pro (Tahap 7); layar ini hanya ringkasan di aplikasi, tanpa ekspor.
 */
@Composable
fun ReportScreen(workspace: AccountWorkspace, refreshKey: Int, onClose: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val currentMonth = remember { YearMonth.now() }
    var month by rememberSaveable(stateSaver = MonthSaver) { mutableStateOf(currentMonth) }
    var report by remember { mutableStateOf<MonthlyReport?>(null) }
    BackHandler(onBack = onClose)

    LaunchedEffect(month, refreshKey) {
        val repos = workspace.repositories
        report = ReportLoader(repos.rooms, repos.transactions).load(month)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = spacing.s2)) {
            TextButton(onClick = onClose, modifier = Modifier.padding(start = spacing.s3)) { Text(stringResource(R.string.onb_back)) }
            Text(
                stringResource(R.string.report_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).padding(horizontal = spacing.s2),
            )
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(RizqflowIcons.PanahKiri, contentDescription = stringResource(R.string.tx_month_prev))
            }
            Text(formatMonth(month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < currentMonth) {
                Icon(RizqflowIcons.PanahKanan, contentDescription = stringResource(R.string.tx_month_next))
            }
        }

        val data = report
        if (data == null) {
            Box(Modifier.fillMaxSize())
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.s4)
                .padding(top = spacing.s2, bottom = spacing.s5),
        ) {
            if (!data.hasData) {
                Text(stringResource(R.string.report_empty), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.report_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.s2),
                )
                return@Column
            }

            SummaryRow(stringResource(R.string.report_income), data.income)
            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.s3))
            SummaryRow(stringResource(R.string.report_expense), data.expense)
            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.s3))
            SummaryRow(stringResource(R.string.report_net), data.net)

            if (data.roomSpend.isNotEmpty()) {
                Text(
                    stringResource(R.string.report_by_room),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = spacing.s5, bottom = spacing.s2),
                )
                val maxRoom = data.roomSpend.first().amount
                data.roomSpend.forEach { BarRow(it.room.name, it.amount, maxRoom, MaterialTheme.rizqflow.room(it.room.colorSlot)) }
            }

            if (data.topCategories.isNotEmpty()) {
                Text(
                    stringResource(R.string.report_by_category),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = spacing.s5, bottom = spacing.s2),
                )
                val maxCategory = data.topCategories.first().amount
                data.topCategories.forEach {
                    BarRow("${it.category.name} · ${it.room.name}", it.amount, maxCategory, MaterialTheme.rizqflow.room(it.room.colorSlot))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, comparison: MonthComparison) {
    val spacing = MaterialTheme.spacing
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatRupiah(comparison.current), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = spacing.s1))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = spacing.s1)) {
            val percent = comparison.percentBp
            if (comparison.previous.isZero && percent == null) {
                Icon(RizqflowIcons.Lingkaran, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = spacing.s1))
                Text(stringResource(R.string.report_no_previous_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (comparison.delta.isZero) {
                Icon(RizqflowIcons.Lingkaran, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = spacing.s1))
                Text(
                    stringResource(R.string.report_same_as_last_month, formatRupiah(comparison.previous)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val up = comparison.delta.isPositive
                Icon(
                    if (up) RizqflowIcons.PanahAtas else RizqflowIcons.PanahBawah,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = spacing.s1),
                )
                val direction = stringResource(if (up) R.string.report_up else R.string.report_down, percent?.let { formatPercent(abs(it)) } ?: formatRupiah(comparison.delta.abs()))
                Text(
                    stringResource(R.string.report_vs_last_month, direction, formatRupiah(comparison.previous)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarRow(label: String, amount: Money, max: Money, color: Color) {
    val spacing = MaterialTheme.spacing
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(formatRupiah(amount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val fraction = if (max.isPositive) (amount.minor.toFloat() / max.minor.toFloat()).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.padding(top = spacing.s1).fillMaxWidth().height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
