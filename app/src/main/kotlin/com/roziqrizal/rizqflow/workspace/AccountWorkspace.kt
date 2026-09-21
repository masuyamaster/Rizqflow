package com.roziqrizal.rizqflow.workspace

import android.content.Context
import com.roziqrizal.rizqflow.data.LocalLedger
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.RuleService
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
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
    val rules = RuleService(local.rooms, PlanEntitlements(), newId)

    override fun close() = local.close()

    companion object {
        fun open(context: Context, accountId: String): AccountWorkspace = AccountWorkspace(LocalLedger.open(context, accountId))
    }
}
