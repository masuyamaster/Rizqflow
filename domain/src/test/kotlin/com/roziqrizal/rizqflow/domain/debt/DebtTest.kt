package com.roziqrizal.rizqflow.domain.debt

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.ledger.CatatDraft
import com.roziqrizal.rizqflow.domain.ledger.InMemorySettings
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerFixture
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.MoneyTransaction
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.reminder.DueStage
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebtServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private class Env(val f: LedgerFixture) {
        val store = InMemoryDebts()
        val service = DebtService(store, f.store, f.store, f.ledger, f.newId)
    }

    private fun env() = Env(LedgerFixture().standard())

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success).value

    private fun <T> LedgerResult<T>.error() = (this as LedgerResult.Failure).error

    private fun Env.lend(
        party: String = "Budi",
        amount: Long = 200_000,
        start: LocalDate = f.today,
        due: LocalDate? = null,
        collectible: Boolean = true,
        record: Boolean = true,
    ): Debt = runSuspend {
        service.create(DebtDraft(DebtDirection.LENT, party, rupiah(amount), start, due, collectible = collectible, accountId = f.account.id, recordMovement = record))
    }.value()

    private fun Env.borrow(
        party: String = "Ibu",
        amount: Long = 500_000,
        start: LocalDate = f.today,
        due: LocalDate? = null,
        record: Boolean = true,
    ): Debt = runSuspend {
        service.create(DebtDraft(DebtDirection.BORROWED, party, rupiah(amount), start, due, accountId = f.account.id, recordMovement = record))
    }.value()

    private fun Env.row(debt: Debt, today: LocalDate = f.today) = runSuspend { service.overview(today) }.rows.first { it.debt.id == debt.id }

    private fun Env.repay(debt: Debt, amount: Long, on: LocalDate = f.today, account: AccountId? = f.account.id) =
        runSuspend { service.repay(debt.id, rupiah(amount), on, account) }

    private fun Env.loanTransactions(): List<MoneyTransaction> =
        f.store.transactionRows.values.filter { it.origin == TransactionOrigin.LOAN }.sortedBy { it.occurredOn }

    @Test
    fun `meminjamkan mengeluarkan uang dari akun tanpa menjadi pengeluaran`() {
        val e = env()
        val before = e.f.balance()

        val debt = e.lend(amount = 200_000)

        val tx = e.loanTransactions().single()
        assertEquals(TransactionKind.LOAN_OUT, tx.kind)
        assertEquals(rupiah(200_000), tx.amount)
        assertEquals("Pinjaman ke Budi", tx.note)
        assertEquals(before - rupiah(200_000), e.f.balance())
        assertNull(tx.roomId)
        assertTrue(e.f.store.transactionRows.values.none { it.kind == TransactionKind.EXPENSE })
        assertEquals(tx.id, debt.initialTransactionId)
        assertEquals(e.f.account.id, debt.accountId)
    }

    @Test
    fun `meminjam memasukkan uang ke akun tanpa dialirkan ke ruang`() {
        val e = env()
        val before = e.f.balance()

        e.borrow(amount = 500_000)

        val tx = e.loanTransactions().single()
        assertEquals(TransactionKind.LOAN_IN, tx.kind)
        assertEquals("Pinjaman dari Ibu", tx.note)
        assertEquals(before + rupiah(500_000), e.f.balance())
        assertTrue(runSuspend { e.f.store.entriesOf(tx.id) }.isEmpty())
        assertTrue(e.f.store.transactionRows.values.none { it.kind == TransactionKind.INCOME })
    }

    @Test
    fun `pinjaman lama tanpa perpindahan uang tidak menyentuh saldo`() {
        val e = env()
        val before = e.f.balance()

        val debt = e.lend(record = false)

        assertEquals(before, e.f.balance())
        assertTrue(e.loanTransactions().isEmpty())
        assertNull(debt.accountId)
        assertNull(debt.initialTransactionId)
    }

    @Test
    fun `isian yang tidak sah ditolak dan tidak ada yang tersimpan`() {
        val e = env()
        fun draft(party: String = "Budi", amount: Long = 100_000, due: LocalDate? = null, account: AccountId? = e.f.account.id, record: Boolean = true) =
            DebtDraft(DebtDirection.LENT, party, rupiah(amount), e.f.today, due, accountId = account, recordMovement = record)

        assertEquals(LedgerError.INVALID_PARTY, runSuspend { e.service.create(draft(party = " ")) }.error())
        assertEquals(LedgerError.INVALID_PARTY, runSuspend { e.service.create(draft(party = "x".repeat(Debt.PARTY_MAX + 1))) }.error())
        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, runSuspend { e.service.create(draft(amount = 0)) }.error())
        assertEquals(LedgerError.INVALID_DUE_DATE, runSuspend { e.service.create(draft(due = e.f.today.minusDays(1))) }.error())
        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, runSuspend { e.service.create(draft(account = null)) }.error())
        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, runSuspend { e.service.create(draft(account = AccountId("tidak-ada"))) }.error())
        assertTrue(e.store.debtRows.isEmpty())
        assertTrue(e.loanTransactions().isEmpty())
    }

    @Test
    fun `akun yang diarsipkan menggagalkan pinjaman baru tanpa menyimpan utangnya`() {
        val e = env()
        runSuspend { e.f.store.save(e.f.account.copy(archived = true)) }

        val result = runSuspend { e.service.create(DebtDraft(DebtDirection.LENT, "Budi", rupiah(1_000), e.f.today, accountId = e.f.account.id)) }

        assertEquals(LedgerError.ACCOUNT_ARCHIVED, result.error())
        assertTrue(e.store.debtRows.isEmpty())
    }

    @Test
    fun `pelunasan sebagian mengurangi sisa dan menggeser saldo lalu pelunasan penuh menuntaskan`() {
        val e = env()
        val debt = e.lend(amount = 200_000)
        val afterLend = e.f.balance()

        val first = e.repay(debt, 50_000).value()

        assertFalse(first.settled)
        assertEquals(rupiah(150_000), e.row(debt).outstanding)
        assertEquals(afterLend + rupiah(50_000), e.f.balance())
        val tx = assertNotNull(runSuspend { e.f.store.find(first.payment.transactionId!!) })
        assertEquals(TransactionKind.LOAN_IN, tx.kind)
        assertEquals("Pelunasan dari Budi", tx.note)

        val second = e.repay(debt, 150_000).value()

        assertTrue(second.settled)
        assertEquals(DebtStatus.SETTLED, e.row(debt).statusOn(e.f.today))
        assertEquals(afterLend + rupiah(200_000), e.f.balance())
    }

    @Test
    fun `membayar utang mengeluarkan uang dari akun`() {
        val e = env()
        val debt = e.borrow(amount = 500_000)
        val afterBorrow = e.f.balance()

        val payment = e.repay(debt, 200_000).value().payment

        assertEquals(TransactionKind.LOAN_OUT, runSuspend { e.f.store.find(payment.transactionId!!) }!!.kind)
        assertEquals("Bayar utang ke Ibu", runSuspend { e.f.store.find(payment.transactionId!!) }!!.note)
        assertEquals(afterBorrow - rupiah(200_000), e.f.balance())
        assertEquals(rupiah(300_000), e.row(debt).outstanding)
    }

    @Test
    fun `pelunasan melebihi sisa atau bernilai nol ditolak`() {
        val e = env()
        val debt = e.lend(amount = 100_000)
        e.repay(debt, 60_000).value()

        assertEquals(LedgerError.DEBT_OVERPAID, e.repay(debt, 40_001).error())
        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, e.repay(debt, 0).error())
        e.repay(debt, 40_000).value()
        assertEquals(LedgerError.DEBT_OVERPAID, e.repay(debt, 1).error())
        assertEquals(LedgerError.DEBT_NOT_FOUND, runSuspend { e.service.repay("x", rupiah(1), e.f.today, e.f.account.id) }.error())
    }

    @Test
    fun `pelunasan tanpa akun mengurangi sisa tanpa menyentuh saldo`() {
        val e = env()
        val debt = e.lend(record = false, amount = 100_000)
        val before = e.f.balance()

        val payment = e.repay(debt, 30_000, account = null).value().payment

        assertNull(payment.transactionId)
        assertEquals(before, e.f.balance())
        assertEquals(rupiah(70_000), e.row(debt).outstanding)
    }

    @Test
    fun `pelunasan yang gagal dicatat ke akun tidak mengurangi sisa`() {
        val e = env()
        val debt = e.lend(amount = 100_000)
        runSuspend { e.f.store.save(e.f.account.copy(archived = true)) }

        assertEquals(LedgerError.ACCOUNT_ARCHIVED, e.repay(debt, 10_000).error())
        assertEquals(rupiah(100_000), e.row(debt).outstanding)
    }

    @Test
    fun `urungkan pelunasan mengembalikan sisa dan saldo`() {
        val e = env()
        val debt = e.lend(amount = 100_000)
        val before = e.f.balance()
        val repayment = e.repay(debt, 100_000).value()
        assertEquals(DebtStatus.SETTLED, e.row(debt).statusOn(e.f.today))

        runSuspend { e.service.undoRepay(repayment.payment) }

        assertEquals(rupiah(100_000), e.row(debt).outstanding)
        assertEquals(before, e.f.balance())
        assertEquals(1, e.loanTransactions().size)
        // Aman diulang bila sudah terhapus.
        runSuspend { e.service.undoRepay(repayment.payment) }
    }

    @Test
    fun `menghapus satu pelunasan yang salah catat menambah sisa lagi`() {
        val e = env()
        val debt = e.lend(amount = 100_000)
        val wrong = e.repay(debt, 90_000).value().payment

        runSuspend { e.service.removePayment(wrong.id) }.value()

        assertEquals(rupiah(100_000), e.row(debt).outstanding)
        assertEquals(LedgerError.DEBT_NOT_FOUND, runSuspend { e.service.removePayment(wrong.id) }.error())
    }

    @Test
    fun `menghapus utang menghapus semua transaksi saldonya`() {
        val e = env()
        val before = e.f.balance()
        val debt = e.lend(amount = 100_000)
        e.repay(debt, 40_000).value()
        e.repay(debt, 10_000).value()

        runSuspend { e.service.delete(debt.id) }.value()

        assertTrue(e.store.debtRows.isEmpty())
        assertTrue(e.store.paymentRows.isEmpty())
        assertTrue(e.loanTransactions().isEmpty())
        assertEquals(before, e.f.balance())
        assertEquals(LedgerError.DEBT_NOT_FOUND, runSuspend { e.service.delete(debt.id) }.error())
    }

    @Test
    fun `menghapus utang tidak menyentuh utang lain`() {
        val e = env()
        val keep = e.borrow(amount = 500_000)
        val drop = e.lend(amount = 100_000)

        runSuspend { e.service.delete(drop.id) }.value()

        assertEquals(listOf(keep.id), e.store.debtRows.keys.toList())
        assertEquals(1, e.loanTransactions().size)
    }

    @Test
    fun `ubah menggeser nama jatuh tempo catatan dan lancar tanpa mengubah pokok`() {
        val e = env()
        val debt = e.lend(amount = 200_000, start = e.f.today)

        val updated = runSuspend { e.service.update(debt.id, DebtEdit("Budi Santoso", d(2026, 12, 1), "  buat servis  ", collectible = false)) }.value()

        assertEquals("Budi Santoso", updated.party)
        assertEquals(d(2026, 12, 1), updated.dueDate)
        assertEquals("buat servis", updated.note)
        assertFalse(updated.collectible)
        assertEquals(rupiah(200_000), updated.principal)
        assertEquals(debt.initialTransactionId, updated.initialTransactionId)
        assertEquals(LedgerError.INVALID_DUE_DATE, runSuspend { e.service.update(debt.id, DebtEdit("Budi", e.f.today.minusDays(1), null, true)) }.error())
        assertEquals(LedgerError.INVALID_PARTY, runSuspend { e.service.update(debt.id, DebtEdit(" ", null, null, true)) }.error())
        assertEquals(LedgerError.DEBT_NOT_FOUND, runSuspend { e.service.update("x", DebtEdit("Budi", null, null, true)) }.error())
    }

    @Test
    fun `ringkasan menjumlah sisa piutang dan utang yang belum lunas`() {
        val e = env()
        val lent = e.lend(party = "Budi", amount = 200_000)
        e.lend(party = "Sari", amount = 100_000)
        e.borrow(party = "Ibu", amount = 500_000)
        val settled = e.borrow(party = "Kakak", amount = 50_000)
        e.repay(lent, 50_000).value()
        e.repay(settled, 50_000).value()

        val overview = runSuspend { e.service.overview(e.f.today) }

        assertEquals(rupiah(250_000), overview.receivable)
        assertEquals(rupiah(500_000), overview.payable)
        assertEquals(4, overview.rows.size)
    }

    @Test
    fun `daftar menaruh yang terlambat dan terdekat di atas lalu tanpa jatuh tempo lalu yang lunas`() {
        val e = env()
        val noDue = e.lend(party = "Tanpa", amount = 10_000)
        val far = e.lend(party = "Jauh", amount = 10_000, due = d(2026, 12, 1))
        val late = e.lend(party = "Telat", amount = 10_000, start = d(2026, 9, 1), due = d(2026, 9, 10))
        val soon = e.borrow(party = "Dekat", amount = 10_000, due = d(2026, 9, 24))
        val done = e.lend(party = "Lunas", amount = 10_000)
        e.repay(done, 10_000).value()

        val rows = runSuspend { e.service.overview(e.f.today) }.rows

        assertEquals(listOf(late.id, soon.id, far.id, noDue.id, done.id), rows.map { it.debt.id })
        assertEquals(listOf(DebtStatus.OVERDUE, DebtStatus.DUE_SOON, DebtStatus.OPEN, DebtStatus.OPEN, DebtStatus.SETTLED), rows.map { it.statusOn(e.f.today) })
    }

    @Test
    fun `status mengikuti jarak ke jatuh tempo`() {
        val e = env()
        val today = e.f.today
        fun status(due: LocalDate?) = e.row(e.lend(due = due, start = today.minusDays(30))).statusOn(today)

        assertEquals(DebtStatus.OVERDUE, status(today.minusDays(1)))
        assertEquals(DebtStatus.DUE_TODAY, status(today))
        assertEquals(DebtStatus.DUE_SOON, status(today.plusDays(3)))
        assertEquals(DebtStatus.OPEN, status(today.plusDays(4)))
        assertEquals(DebtStatus.OPEN, status(null))
    }

    @Test
    fun `riwayat pelunasan terbaru dulu`() {
        val e = env()
        val debt = e.lend(amount = 100_000)
        e.repay(debt, 10_000, on = d(2026, 9, 1)).value()
        e.repay(debt, 20_000, on = d(2026, 9, 20)).value()
        e.repay(debt, 30_000, on = d(2026, 9, 10)).value()

        assertEquals(listOf(d(2026, 9, 20), d(2026, 9, 10), d(2026, 9, 1)), e.row(debt).payments.map { it.paidOn })
        assertEquals(rupiah(60_000), e.row(debt).repaid)
    }

    @Test
    fun `saran zakat hanya piutang lancar dan utang jangka pendek yang belum lunas`() {
        val e = env()
        val today = e.f.today
        val good = e.lend(party = "Lancar", amount = 300_000)
        e.lend(party = "Macet", amount = 900_000, collectible = false)
        val settled = e.lend(party = "Lunas", amount = 100_000)
        e.borrow(party = "Dekat", amount = 400_000, due = today.plusMonths(6))
        e.borrow(party = "Tanpa", amount = 100_000)
        e.borrow(party = "Pas setahun", amount = 50_000, due = today.plusMonths(12))
        e.borrow(party = "Jauh", amount = 7_000_000, due = today.plusMonths(12).plusDays(1))
        e.repay(good, 100_000).value()
        e.repay(settled, 100_000).value()

        val suggestion = runSuspend { e.service.zakatSuggestion(today) }

        assertEquals(rupiah(200_000), suggestion.receivable)
        assertEquals(rupiah(550_000), suggestion.shortTermDebt)
    }

    @Test
    fun `saran zakat kosong tanpa utang piutang`() {
        val e = env()

        val suggestion = runSuspend { e.service.zakatSuggestion(e.f.today) }

        assertEquals(rupiah(0), suggestion.receivable)
        assertEquals(rupiah(0), suggestion.shortTermDebt)
    }

    @Test
    fun `transaksi pinjaman tidak bisa diubah dari layar Catat`() {
        val e = env()
        val debt = e.lend(amount = 100_000)
        val tx = assertNotNull(runSuspend { e.f.store.find(debt.initialTransactionId!!) })

        assertFailsWith<IllegalArgumentException> { CatatDraft.from(tx) }
        val result = runSuspend {
            e.f.ledger.updateTransaction(tx.id, CatatDraft(mode = com.roziqrizal.rizqflow.domain.ledger.CatatMode.EXPENSE, digits = "5000", accountId = e.f.account.id, date = e.f.today))
        }
        assertEquals(LedgerError.KIND_MISMATCH, result.error())
    }

    @Test
    fun `transaksi pinjaman menolak ruang kategori atau akun tujuan`() {
        val base = MoneyTransaction(
            id = TransactionId("t"), kind = TransactionKind.LOAN_OUT, amount = rupiah(1), accountId = AccountId("a"),
            occurredOn = d(2026, 9, 21), createdAtMillis = 0, updatedAtMillis = 0,
        )
        assertFailsWith<IllegalArgumentException> { base.copy(roomId = RoomId("r")) }
        assertFailsWith<IllegalArgumentException> { base.copy(toAccountId = AccountId("b")) }
        assertFailsWith<IllegalArgumentException> { base.copy(incomeSource = "Gaji") }
    }
}

class DebtReminderServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private class Env(val f: LedgerFixture) {
        val store = InMemoryDebts()
        val settings = InMemorySettings()
        val service = DebtService(store, f.store, f.store, f.ledger, f.newId)
        val reminders = DebtReminderService(store, settings)
    }

    private fun env() = Env(LedgerFixture().standard())

    private fun Env.debt(direction: DebtDirection = DebtDirection.LENT, party: String = "Budi", due: LocalDate?, amount: Long = 100_000): Debt =
        (runSuspend { service.create(DebtDraft(direction, party, rupiah(amount), f.today.minusDays(30), due, accountId = f.account.id)) } as LedgerResult.Success).value

    private fun Env.due(today: LocalDate) = runSuspend { reminders.dueReminders(today) }

    @Test
    fun `tiga hari sebelum jatuh tempo disapa sekali lalu pada harinya lalu sekali saat terlambat`() {
        val e = env()
        e.debt(due = d(2026, 9, 25))

        val approaching = e.due(d(2026, 9, 22)).single()
        assertEquals(DueStage.APPROACHING, approaching.stage)
        assertEquals(3, approaching.daysLeft)
        runSuspend { e.reminders.markNotified(approaching) }
        assertTrue(e.due(d(2026, 9, 23)).isEmpty())

        val today = e.due(d(2026, 9, 25)).single()
        assertEquals(DueStage.DUE_TODAY, today.stage)
        runSuspend { e.reminders.markNotified(today) }

        val late = e.due(d(2026, 9, 27)).single()
        assertEquals(DueStage.OVERDUE, late.stage)
        assertEquals(-2, late.daysLeft)
        runSuspend { e.reminders.markNotified(late) }
        assertTrue(e.due(d(2026, 10, 30)).isEmpty())
    }

    @Test
    fun `utang saya dan piutang sama-sama disapa dengan sisanya`() {
        val e = env()
        e.debt(DebtDirection.BORROWED, "Ibu", due = d(2026, 9, 24), amount = 500_000)
        val lent = e.debt(DebtDirection.LENT, "Budi", due = d(2026, 9, 24), amount = 200_000)
        runSuspend { e.service.repay(lent.id, rupiah(50_000), e.f.today, e.f.account.id) }

        val reminders = e.due(d(2026, 9, 24))

        assertEquals(setOf("Ibu", "Budi"), reminders.map { it.debt.party }.toSet())
        assertEquals(rupiah(150_000), reminders.first { it.debt.party == "Budi" }.outstanding)
        assertEquals(DebtDirection.BORROWED, reminders.first { it.debt.party == "Ibu" }.debt.direction)
    }

    @Test
    fun `tanpa jatuh tempo atau sudah lunas tidak pernah disapa`() {
        val e = env()
        e.debt(party = "Tanpa", due = null)
        val done = e.debt(party = "Lunas", due = d(2026, 9, 24))
        runSuspend { e.service.repay(done.id, rupiah(100_000), e.f.today, null) }

        assertTrue(e.due(d(2026, 9, 24)).isEmpty())
    }

    @Test
    fun `pelunasan sebagian tidak memulai daur baru`() {
        val e = env()
        val debt = e.debt(due = d(2026, 9, 24))
        runSuspend { e.reminders.markNotified(e.due(d(2026, 9, 24)).single()) }

        runSuspend { e.service.repay(debt.id, rupiah(10_000), e.f.today, null) }

        assertTrue(e.due(d(2026, 9, 24)).isEmpty())
    }

    @Test
    fun `mengubah jatuh tempo memulai daur baru`() {
        val e = env()
        val debt = e.debt(due = d(2026, 9, 24))
        runSuspend { e.reminders.markNotified(e.due(d(2026, 9, 24)).single()) }

        runSuspend { e.service.update(debt.id, DebtEdit(debt.party, d(2026, 10, 10), null, true)) }

        assertEquals(DueStage.APPROACHING, e.due(d(2026, 10, 7)).single().stage)
    }

    @Test
    fun `jauh dari jatuh tempo tidak disapa`() {
        val e = env()
        e.debt(due = d(2026, 10, 30))

        assertTrue(e.due(d(2026, 9, 21)).isEmpty())
    }
}
