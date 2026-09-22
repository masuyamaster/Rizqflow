package com.roziqrizal.rizqflow.workspace

import android.content.Context
import com.roziqrizal.rizqflow.data.LocalLedger
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.ledger.DemoData
import com.roziqrizal.rizqflow.domain.ledger.FavoriteService
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.ManagementService
import com.roziqrizal.rizqflow.domain.ledger.ReconciliationService
import com.roziqrizal.rizqflow.domain.ledger.ReminderService
import com.roziqrizal.rizqflow.domain.ledger.RuleService
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.zakat.ZakatService
import java.util.UUID

/**
 * Ruang kerja satu akun yang sedang masuk: databasenya dan layanan di atasnya. Dibuat saat akun
 * masuk dan ditutup saat keluar; akun lain membuka berkas lain (satu database per akun).
 * Pengenal dibuat sebagai UUID dan waktu diambil dari jam sistem.
 */
class AccountWorkspace private constructor(private val local: LocalLedger) : AutoCloseable {
    private val newId: () -> String = { UUID.randomUUID().toString() }

    val repositories: LocalLedger get() = local
    val setup = WorkspaceSetup(local.workspace, newId)
    val ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId, System::currentTimeMillis)

    // Paket gratis sampai Tahap 7 menghubungkannya dengan status pembelian.
    private val entitlements = PlanEntitlements()
    val rules = RuleService(local.rooms, entitlements, newId)
    val management = ManagementService(local.accounts, local.rooms, entitlements, newId)
    val favorites = FavoriteService(local.favorites, local.accounts, local.rooms, ledger, newId, System::currentTimeMillis)
    val zakat = ZakatService(local.rooms, local.transactions, local.zakat, ledger, newId)
    val reconciliation = ReconciliationService(local.accounts, local.rooms, local.transactions, ledger)
    val reminder = ReminderService(local.settings, local.accounts, local.rooms, local.transactions, ledger)
    val demo = DemoData(setup, ledger, management, favorites, local.rooms)

    override fun close() = local.close()

    companion object {
        fun open(context: Context, accountId: String): AccountWorkspace = AccountWorkspace(LocalLedger.open(context, accountId))
    }
}
