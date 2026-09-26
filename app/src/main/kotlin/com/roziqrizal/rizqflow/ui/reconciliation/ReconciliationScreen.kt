package com.roziqrizal.rizqflow.ui.reconciliation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.ledger.Account
import com.roziqrizal.rizqflow.domain.ledger.AmountPad
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.ReconciliationOverview
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.ui.Notifier
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.RoomTile
import com.roziqrizal.rizqflow.ui.formatRupiah
import com.roziqrizal.rizqflow.ui.onboarding.AmountKeypad
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.rizqflowFilterChipColors
import com.roziqrizal.rizqflow.ui.theme.spacing
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import kotlinx.coroutines.launch

/**
 * S25 Koreksi saldo. Berlaku sama untuk tunai, e-wallet, dan bank (QRIS memotong saldo akun
 * pembayar, jadi ikut tercocokkan). Saldo sebenarnya lebih kecil: dicatat sebagai pengeluaran
 * kategori sistem Tak terlacak di ruang pilihan. Lebih besar: dicatat sebagai pemasukan biasa
 * (dialirkan mengikuti aturan alokasi, seperti pemasukan lain). Sama: tidak mencatat apa pun,
 * hanya menandai tanggal cocok. Nada netral, tanpa kata yang menghakimi; tombol tetap aktif
 * walau selisihnya nol. Tidak punya notifikasi sendiri.
 */
@Composable
fun ReconciliationScreen(
    workspace: AccountWorkspace,
    initialAccountId: AccountId?,
    notifier: Notifier,
    onClose: () -> Unit,
    onCorrected: (TransactionId?, Money) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val today = remember { java.time.LocalDate.now() }
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var accountId by rememberSaveable { mutableStateOf(initialAccountId?.value) }
    var overview by remember { mutableStateOf<ReconciliationOverview?>(null) }
    var digits by rememberSaveable { mutableStateOf("") }
    var roomId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LedgerError?>(null) }
    BackHandler(onBack = onClose)

    LaunchedEffect(workspace) {
        accounts = workspace.repositories.accounts.activeAccounts()
        if (accountId == null) accountId = accounts.firstOrNull()?.id?.value
    }
    LaunchedEffect(accountId) {
        val id = accountId ?: return@LaunchedEffect
        val data = workspace.reconciliation.overview(AccountId(id))
        overview = data
        roomId = data?.suggestedRoomId?.value
        digits = ""
        error = null
    }

    val data = overview
    val actual = AmountPad.toRupiah(digits)
    val diff = data?.let { actual - it.recorded }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TextButton(onClick = onClose, modifier = Modifier.padding(start = spacing.s3, top = spacing.s2)) { Text(stringResource(R.string.onb_back)) }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.s5)
                .padding(bottom = spacing.s5),
        ) {
            Text(stringResource(R.string.reconcile_title), style = MaterialTheme.typography.headlineMedium)

            if (accounts.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.padding(top = spacing.s3)) {
                    accounts.forEach { account ->
                        FilterChip(selected = accountId == account.id.value, onClick = { accountId = account.id.value }, label = { Text(account.name) }, colors = rizqflowFilterChipColors())
                    }
                }
            }

            if (data == null) {
                Box(Modifier.fillMaxSize())
                return@Column
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.s4), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.reconcile_recorded), style = MaterialTheme.typography.bodyLarge)
                Text(formatRupiah(data.recorded), style = MaterialTheme.typography.titleMedium)
            }

            Text(stringResource(R.string.reconcile_actual), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = spacing.s4, bottom = spacing.s1))
            Text(formatRupiah(actual), style = MaterialTheme.typography.displaySmall)
            AmountKeypad(enabled = !busy, onKey = { digits = AmountPad.apply(digits, it) }, modifier = Modifier.padding(top = spacing.s3))

            if (diff != null && digits.isNotEmpty()) {
                DifferenceCard(diff)

                if (diff.isNegative) {
                    Text(stringResource(R.string.reconcile_file_as), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = spacing.s4, bottom = spacing.s1))
                    Text(
                        stringResource(R.string.reconcile_category_fixed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.reconcile_room), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = spacing.s3, bottom = spacing.s1))
                    RoomRow(workspace, roomId) { roomId = it }
                } else if (diff.isPositive) {
                    Text(
                        stringResource(R.string.reconcile_income_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.s3),
                    )
                }
            }

            Button(
                onClick = {
                    val room = roomId?.let(::RoomId) ?: data.suggestedRoomId
                    if (diff != null && diff.isNegative && room == null) {
                        error = LedgerError.ROOM_NOT_FOUND
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        val result = workspace.reconciliation.correct(AccountId(accountId!!), actual, room ?: RoomId(""), today)
                        busy = false
                        when (result) {
                            is LedgerResult.Success -> onCorrected(result.value, diff ?: Money.zero())
                            is LedgerResult.Failure -> error = result.error
                        }
                    }
                },
                enabled = !busy && digits.isNotEmpty(),
                modifier = Modifier.padding(top = spacing.s5).fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.reconcile_save)) }
            error?.let {
                Text(stringResource(R.string.catat_error_generic), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = spacing.s2))
            }
            // Cakupan dipersempit: belum membuka S08 dengan saringan akun otomatis, cukup menutup layar ini dulu.
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = spacing.s2)) { Text(stringResource(R.string.reconcile_search_self)) }
        }
    }
}

@Composable
private fun DifferenceCard(diff: Money) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .padding(top = spacing.s4)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(spacing.s4),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reconcile_difference), style = MaterialTheme.typography.bodyLarge)
            Text(formatRupiah(diff.abs()), style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.padding(top = spacing.s2)) {
            when {
                diff.isZero -> {
                    Icon(RizqflowIcons.Centang, contentDescription = null, tint = MaterialTheme.rizqflow.statusGood, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.reconcile_match), style = MaterialTheme.typography.bodyMedium)
                }

                diff.isNegative -> Text(stringResource(R.string.reconcile_short), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(stringResource(R.string.reconcile_over), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomRow(workspace: AccountWorkspace, selected: String?, onPick: (String) -> Unit) {
    var rooms by remember { mutableStateOf<List<com.roziqrizal.rizqflow.domain.ledger.Room>>(emptyList()) }
    LaunchedEffect(workspace) { rooms = workspace.repositories.rooms.activeRooms() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2)) {
        rooms.forEach { room ->
            FilterChip(
                selected = selected == room.id.value,
                onClick = { onPick(room.id.value) },
                label = { Text(room.name) },
                leadingIcon = { RoomTile(room.iconKey, room.colorSlot, size = 20) },
                colors = rizqflowFilterChipColors(),
            )
        }
    }
}
