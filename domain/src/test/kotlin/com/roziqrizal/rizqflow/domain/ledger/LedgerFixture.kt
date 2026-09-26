package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.entitlement.Plan
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/** Perakit tes: penyimpanan di memori, pembangkit pengenal berurutan, dan jam yang bisa digeser. */
class LedgerFixture(plans: Set<Plan> = emptySet()) {
    val store = InMemoryLedger()
    private var counter = 0
    val newId: () -> String = { "id-${++counter}" }
    var now = 1_000L
    val setup = WorkspaceSetup(store, newId)
    val ledger = LedgerService(store, store, store, newId) { now }
    val rules = RuleService(store, PlanEntitlements(plans), newId)

    val today: LocalDate = LocalDate.of(2026, 9, 21)

    /** Ruang kerja standar: pola Tiga hak (10/30/60) dan satu akun Dompet berisi Rp 500.000. */
    fun standard(): LedgerFixture {
        val result = runSuspend { setup.setUp(RoomTemplate.TIGA_HAK, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(500_000))) }
        check(result is LedgerResult.Success) { "pengaturan awal gagal: $result" }
        return this
    }

    val account: Account get() = store.accountRows.values.first()

    fun room(name: String): Room = store.roomRows.values.first { it.name == name }

    fun category(roomName: String, name: String): Category =
        store.categoryRows.values.first { it.roomId == room(roomName).id && it.name == name }

    fun balance(id: AccountId = account.id): Money = runSuspend { store.balance(id) }

    fun addAccount(name: String, opening: Long = 0, archived: Boolean = false): Account {
        val a = Account(AccountId(newId()), name, AccountKind.BANK, Money.rupiah(opening), archived, sortOrder = store.accountRows.size)
        runSuspend { store.save(a) }
        return a
    }

    fun incomeCommand(amount: Long) = NewIncome(Money.rupiah(amount), account.id, "Gaji", today)

    fun income(amount: Long, source: String? = "Gaji", note: String? = null, accountId: AccountId = account.id) =
        runSuspend { ledger.recordIncome(NewIncome(Money.rupiah(amount), accountId, source, today, note)) }
}
