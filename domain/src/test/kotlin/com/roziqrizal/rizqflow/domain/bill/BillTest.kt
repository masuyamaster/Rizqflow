package com.roziqrizal.rizqflow.domain.bill

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.ledger.InMemorySettings
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerFixture
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.recurring.Frequency
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private class Env(val f: LedgerFixture) {
        val store = InMemoryBills()
        val service = BillService(store, f.store, f.store, f.store, f.ledger, f.newId)
        val keluarga = f.room("Keluarga")
        val kebutuhan = f.store.categoryRows.values.first { it.roomId == keluarga.id && !it.isSystem }
    }

    private fun env() = Env(LedgerFixture().standard())

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success).value

    private fun <T> LedgerResult<T>.error() = (this as LedgerResult.Failure).error

    private fun Env.draft(
        name: String = "Listrik",
        amount: Long = 350_000,
        frequency: Frequency? = Frequency.MONTHLY,
        due: LocalDate = d(2026, 9, 25),
        total: Int? = null,
        alreadyPaid: Int = 0,
        accountId: AccountId = f.account.id,
        roomId: RoomId = keluarga.id,
        categoryId: CategoryId = kebutuhan.id,
    ) = BillDraft(name, rupiah(amount), accountId, roomId, categoryId, frequency = frequency, dueDate = due, totalInstallments = total, alreadyPaid = alreadyPaid)

    private fun Env.create(draft: BillDraft = draft()): Bill = runSuspend { service.create(draft) }.value()

    private fun Env.billTransactions() = f.store.transactionRows.values.filter { it.origin == TransactionOrigin.BILL }.sortedBy { it.occurredOn }

    @Test
    fun `tagihan baru tersimpan dengan jatuh tempo dan jangkarnya`() {
        val e = env()

        val bill = e.create(e.draft(due = d(2026, 10, 31)))

        assertEquals(d(2026, 10, 31), bill.nextDue)
        assertEquals(d(2026, 10, 31), bill.startDate)
        assertEquals(0, bill.paidCount)
        assertTrue(bill.active)
        assertEquals(bill, e.store.rows[bill.id])
    }

    @Test
    fun `tagihan yang sudah lewat jatuh tempo boleh dicatat dan langsung terlambat`() {
        val e = env()

        val bill = e.create(e.draft(due = d(2026, 9, 10)))

        assertEquals(BillStatus.OVERDUE, bill.statusOn(e.f.today))
    }

    @Test
    fun `isian yang tidak sah ditolak dengan alasannya`() {
        val e = env()

        assertEquals(LedgerError.INVALID_NAME, runSuspend { e.service.create(e.draft(name = "  ")) }.error())
        assertEquals(LedgerError.INVALID_NAME, runSuspend { e.service.create(e.draft(name = "x".repeat(Bill.NAME_MAX + 1))) }.error())
        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, runSuspend { e.service.create(e.draft(amount = 0)) }.error())
        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, runSuspend { e.service.create(e.draft(accountId = AccountId("tidak-ada"))) }.error())
        assertEquals(LedgerError.ROOM_NOT_FOUND, runSuspend { e.service.create(e.draft(roomId = RoomId("tidak-ada"))) }.error())
        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, runSuspend { e.service.create(e.draft(categoryId = CategoryId("tidak-ada"))) }.error())
        assertTrue(e.store.rows.isEmpty())
    }

    @Test
    fun `kategori dari ruang lain ditolak`() {
        val e = env()
        val memberi = e.f.room("Memberi")
        val kategoriMemberi = e.f.store.categoryRows.values.first { it.roomId == memberi.id && !it.isSystem }

        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, runSuspend { e.service.create(e.draft(categoryId = kategoriMemberi.id)) }.error())
    }

    @Test
    fun `jumlah cicilan yang tidak sah ditolak`() {
        val e = env()

        assertEquals(LedgerError.INVALID_INSTALLMENTS, runSuspend { e.service.create(e.draft(total = 0)) }.error())
        assertEquals(LedgerError.INVALID_INSTALLMENTS, runSuspend { e.service.create(e.draft(frequency = null, total = 3)) }.error())
        assertEquals(LedgerError.INVALID_INSTALLMENTS, runSuspend { e.service.create(e.draft(total = 12, alreadyPaid = 12)) }.error())
        assertEquals(LedgerError.INVALID_INSTALLMENTS, runSuspend { e.service.create(e.draft(alreadyPaid = -1)) }.error())
        assertEquals(LedgerError.INVALID_INSTALLMENTS, runSuspend { e.service.create(e.draft(frequency = null, alreadyPaid = 1)) }.error())
    }

    @Test
    fun `membayar mencatat pengeluaran di ruang dan kategori tagihan lalu memajukan jatuh tempo`() {
        val e = env()
        val bill = e.create()
        val before = e.f.balance()

        val payment = runSuspend { e.service.pay(bill.id, d(2026, 9, 24)) }.value()

        val tx = e.billTransactions().single()
        assertEquals(TransactionKind.EXPENSE, tx.kind)
        assertEquals(rupiah(350_000), tx.amount)
        assertEquals(e.keluarga.id, tx.roomId)
        assertEquals(e.kebutuhan.id, tx.categoryId)
        assertEquals(d(2026, 9, 24), tx.occurredOn)
        assertEquals("Listrik", tx.note)
        assertEquals(before - rupiah(350_000), e.f.balance())
        assertEquals(d(2026, 10, 25), payment.updated.nextDue)
        assertEquals(1, payment.updated.paidCount)
        assertEquals(payment.updated, e.store.rows[bill.id])
    }

    @Test
    fun `nominal yang dibayar boleh berbeda dari nominal tagihan dan tagihan tetap`() {
        val e = env()
        val bill = e.create()

        runSuspend { e.service.pay(bill.id, d(2026, 9, 25), rupiah(410_000)) }.value()

        assertEquals(rupiah(410_000), e.billTransactions().single().amount)
        assertEquals(rupiah(350_000), e.store.rows.getValue(bill.id).amount)
    }

    @Test
    fun `jatuh tempo bulanan tanggal 31 kembali ke 31 setelah bulan pendek`() {
        val e = env()
        val bill = e.create(e.draft(due = d(2026, 1, 31)))

        val feb = runSuspend { e.service.pay(bill.id, d(2026, 1, 31)) }.value().updated
        val mar = runSuspend { e.service.pay(bill.id, d(2026, 2, 28)) }.value().updated

        assertEquals(d(2026, 2, 28), feb.nextDue)
        assertEquals(d(2026, 3, 31), mar.nextDue)
    }

    @Test
    fun `membayar tagihan terlambat memajukan satu jatuh tempo saja`() {
        val e = env()
        val bill = e.create(e.draft(due = d(2026, 7, 25)))

        val payment = runSuspend { e.service.pay(bill.id, e.f.today) }.value()

        assertEquals(d(2026, 8, 25), payment.updated.nextDue)
        assertEquals(BillStatus.OVERDUE, payment.updated.statusOn(e.f.today))
    }

    @Test
    fun `tagihan sekali bayar lunas setelah dibayar dan tidak bisa dibayar lagi`() {
        val e = env()
        val bill = e.create(e.draft(name = "Pajak motor", frequency = null))

        val paid = runSuspend { e.service.pay(bill.id, e.f.today) }.value().updated

        assertTrue(paid.isFinished)
        assertEquals(BillStatus.FINISHED, paid.statusOn(e.f.today))
        assertEquals(bill.nextDue, paid.nextDue)
        assertEquals(LedgerError.BILL_FINISHED, runSuspend { e.service.pay(bill.id, e.f.today) }.error())
        assertEquals(1, e.billTransactions().size)
    }

    @Test
    fun `cicilan berhenti di angka terakhir dan catatan menyebut urutannya`() {
        val e = env()
        val bill = e.create(e.draft(name = "Cicilan motor", total = 3, due = d(2026, 9, 5)))

        val first = runSuspend { e.service.pay(bill.id, d(2026, 9, 5)) }.value().updated
        val second = runSuspend { e.service.pay(bill.id, d(2026, 10, 5)) }.value().updated
        val third = runSuspend { e.service.pay(bill.id, d(2026, 11, 5)) }.value().updated

        assertEquals(listOf("Cicilan motor (1/3)", "Cicilan motor (2/3)", "Cicilan motor (3/3)"), e.billTransactions().map { it.note })
        assertEquals(2, first.remainingInstallments)
        assertEquals(1, second.remainingInstallments)
        assertEquals(0, third.remainingInstallments)
        assertTrue(third.isFinished)
        assertEquals(d(2026, 11, 5), third.nextDue)
        assertEquals(LedgerError.BILL_FINISHED, runSuspend { e.service.pay(bill.id, e.f.today) }.error())
    }

    @Test
    fun `cicilan yang sudah dibayar sebelum dicatat dihitung`() {
        val e = env()
        val bill = e.create(e.draft(name = "KPR", total = 12, alreadyPaid = 11, due = d(2026, 9, 5)))

        val paid = runSuspend { e.service.pay(bill.id, d(2026, 9, 5)) }.value().updated

        assertEquals("KPR (12/12)", e.billTransactions().single().note)
        assertTrue(paid.isFinished)
    }

    @Test
    fun `tagihan tanpa batas cicilan tidak pernah lunas`() {
        val e = env()
        val bill = e.create()

        val paid = runSuspend { e.service.pay(bill.id, e.f.today) }.value().updated

        assertFalse(paid.isFinished)
        assertNull(paid.remainingInstallments)
    }

    @Test
    fun `membayar dua kali untuk jatuh tempo yang sama tidak mencatat pengeluaran ganda`() {
        val e = env()
        val bill = e.create()
        // Pengeluaran sudah tercatat tapi kemajuan tagihan belum sempat tersimpan (misalnya aplikasi mati di antaranya).
        runSuspend {
            e.f.ledger.recordExpense(
                com.roziqrizal.rizqflow.domain.ledger.NewExpense(
                    rupiah(350_000), e.f.account.id, e.keluarga.id, e.kebutuhan.id, d(2026, 9, 25),
                    origin = TransactionOrigin.BILL, id = BillService.occurrenceId(bill.id, bill.nextDue),
                ),
            )
        }

        val payment = runSuspend { e.service.pay(bill.id, d(2026, 9, 25)) }.value()

        assertEquals(1, e.billTransactions().size)
        assertEquals(d(2026, 10, 25), payment.updated.nextDue)
    }

    @Test
    fun `pembayaran yang gagal dicatat tidak memajukan tagihan`() {
        val e = env()
        val bill = e.create()
        runSuspend { e.f.store.save(e.f.account.copy(archived = true)) }

        val result = runSuspend { e.service.pay(bill.id, e.f.today) }

        assertEquals(LedgerError.ACCOUNT_ARCHIVED, result.error())
        assertEquals(bill, e.store.rows[bill.id])
        assertTrue(e.billTransactions().isEmpty())
    }

    @Test
    fun `urungkan menghapus pengeluaran dan mengembalikan tagihan seperti semula`() {
        val e = env()
        val bill = e.create(e.draft(total = 6))
        val before = e.f.balance()
        val payment = runSuspend { e.service.pay(bill.id, e.f.today) }.value()

        runSuspend { e.service.undoPay(payment) }

        assertEquals(bill, e.store.rows[bill.id])
        assertTrue(e.billTransactions().isEmpty())
        assertEquals(before, e.f.balance())
        // Setelah diurungkan, tagihan bisa dibayar lagi tanpa bentrok pengenal transaksi.
        runSuspend { e.service.pay(bill.id, e.f.today) }.value()
        assertEquals(1, e.billTransactions().size)
    }

    @Test
    fun `urungkan tetap aman bila pengeluarannya sudah dihapus pengguna`() {
        val e = env()
        val bill = e.create()
        val payment = runSuspend { e.service.pay(bill.id, e.f.today) }.value()
        runSuspend { e.f.ledger.delete(payment.transactionId) }

        runSuspend { e.service.undoPay(payment) }

        assertEquals(bill, e.store.rows[bill.id])
    }

    @Test
    fun `mengubah jatuh tempo memindahkan jangkar jadwal dan tanpa perubahan jangkar tetap`() {
        val e = env()
        val bill = e.create(e.draft(due = d(2026, 9, 25)))

        val sameDate = runSuspend { e.service.update(bill.id, e.draft(name = "Listrik rumah", due = d(2026, 9, 25))) }.value()
        val moved = runSuspend { e.service.update(bill.id, e.draft(due = d(2026, 10, 3))) }.value()

        assertEquals(d(2026, 9, 25), sameDate.startDate)
        assertEquals("Listrik rumah", sameDate.name)
        assertEquals(d(2026, 10, 3), moved.startDate)
        assertEquals(d(2026, 10, 3), moved.nextDue)
    }

    @Test
    fun `setelah dibayar jangkar tetap sehingga mengubah nama tidak menggeser jatuh tempo berikutnya`() {
        val e = env()
        val bill = e.create(e.draft(due = d(2026, 1, 31)))
        val paid = runSuspend { e.service.pay(bill.id, d(2026, 1, 31)) }.value().updated

        val renamed = runSuspend { e.service.update(bill.id, e.draft(name = "Internet", due = paid.nextDue)) }.value()

        // Jatuh tempo yang tercatat di layar (28 Feb) sama dengan yang tersimpan, jadi jangkar 31 Januari dipertahankan.
        assertEquals(d(2026, 1, 31), renamed.startDate)
    }

    @Test
    fun `jumlah cicilan terbayar bisa dibetulkan lewat ubah`() {
        val e = env()
        val bill = e.create(e.draft(total = 6))

        val updated = runSuspend { e.service.update(bill.id, e.draft(total = 6, alreadyPaid = 2)) }.value()

        assertEquals(2, updated.paidCount)
        assertEquals(4, updated.remainingInstallments)
    }

    @Test
    fun `jeda tidak menggeser jatuh tempo dan tagihan yang dijeda tidak terlambat`() {
        val e = env()
        val bill = e.create(e.draft(due = d(2026, 9, 1)))

        val paused = runSuspend { e.service.setActive(bill.id, false) }.value()

        assertEquals(d(2026, 9, 1), paused.nextDue)
        assertEquals(BillStatus.PAUSED, paused.statusOn(e.f.today))
        assertEquals(BillStatus.OVERDUE, runSuspend { e.service.setActive(bill.id, true) }.value().statusOn(e.f.today))
    }

    @Test
    fun `menghapus tagihan tidak menghapus pengeluaran yang sudah tercatat`() {
        val e = env()
        val bill = e.create()
        runSuspend { e.service.pay(bill.id, e.f.today) }.value()

        runSuspend { e.service.delete(bill.id) }.value()

        assertNull(e.store.rows[bill.id])
        assertEquals(1, e.billTransactions().size)
    }

    @Test
    fun `tagihan yang tidak ada dilaporkan`() {
        val e = env()

        assertEquals(LedgerError.BILL_NOT_FOUND, runSuspend { e.service.pay("x", e.f.today) }.error())
        assertEquals(LedgerError.BILL_NOT_FOUND, runSuspend { e.service.update("x", e.draft()) }.error())
        assertEquals(LedgerError.BILL_NOT_FOUND, runSuspend { e.service.setActive("x", false) }.error())
        assertEquals(LedgerError.BILL_NOT_FOUND, runSuspend { e.service.delete("x") }.error())
    }

    @Test
    fun `daftar menaruh yang terlambat dan terdekat di atas lalu yang dijeda lalu yang lunas`() {
        val e = env()
        val soon = e.create(e.draft(name = "Internet", due = d(2026, 9, 24)))
        val late = e.create(e.draft(name = "Air", due = d(2026, 9, 10)))
        val far = e.create(e.draft(name = "Asuransi", due = d(2026, 12, 1)))
        val paused = e.create(e.draft(name = "Gym", due = d(2026, 9, 22)))
        val done = e.create(e.draft(name = "Pajak", frequency = null, due = d(2026, 9, 1)))
        runSuspend { e.service.setActive(paused.id, false) }
        runSuspend { e.service.pay(done.id, e.f.today) }

        val rows = runSuspend { e.service.list(e.f.today) }

        assertEquals(listOf("Air", "Internet", "Asuransi", "Gym", "Pajak"), rows.map { it.bill.name })
        assertEquals(listOf(late.id, soon.id, far.id, paused.id, done.id), rows.map { it.bill.id })
        assertEquals("Keluarga", rows.first().roomName)
        assertEquals("Dompet", rows.first().accountName)
        assertTrue(rows.all { it.usable })
    }

    @Test
    fun `baris ditandai tidak bisa dipakai bila akunnya diarsipkan`() {
        val e = env()
        e.create()
        runSuspend { e.f.store.save(e.f.account.copy(archived = true)) }

        assertFalse(runSuspend { e.service.list(e.f.today) }.single().usable)
    }

    @Test
    fun `status mengikuti jarak ke jatuh tempo`() {
        val e = env()
        val today = e.f.today
        fun status(due: LocalDate) = e.create(e.draft(due = due)).statusOn(today)

        assertEquals(BillStatus.OVERDUE, status(today.minusDays(1)))
        assertEquals(BillStatus.DUE_TODAY, status(today))
        assertEquals(BillStatus.DUE_SOON, status(today.plusDays(1)))
        assertEquals(BillStatus.DUE_SOON, status(today.plusDays(3)))
        assertEquals(BillStatus.UPCOMING, status(today.plusDays(4)))
    }
}

class BillReminderServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private class Env(val f: LedgerFixture) {
        val store = InMemoryBills()
        val settings = InMemorySettings()
        val service = BillService(store, f.store, f.store, f.store, f.ledger, f.newId)
        val reminders = BillReminderService(store, settings)
        val room = f.room("Keluarga")
        val category = f.store.categoryRows.values.first { it.roomId == room.id && !it.isSystem }
    }

    private fun env() = Env(LedgerFixture().standard())

    private fun Env.bill(name: String = "Listrik", due: LocalDate, frequency: Frequency? = Frequency.MONTHLY, total: Int? = null): Bill =
        (runSuspend { service.create(BillDraft(name, rupiah(350_000), f.account.id, room.id, category.id, frequency = frequency, dueDate = due, totalInstallments = total)) } as LedgerResult.Success).value

    private fun Env.due(today: LocalDate) = runSuspend { reminders.dueReminders(today) }

    @Test
    fun `jauh dari jatuh tempo tidak disapa`() {
        val e = env()
        e.bill(due = d(2026, 9, 25))

        assertTrue(e.due(d(2026, 9, 21)).isEmpty())
    }

    @Test
    fun `tiga hari sebelum jatuh tempo disapa sekali sampai ditandai`() {
        val e = env()
        val bill = e.bill(due = d(2026, 9, 25))

        val reminder = e.due(d(2026, 9, 22)).single()
        assertEquals(BillReminderStage.APPROACHING, reminder.stage)
        assertEquals(3, reminder.daysLeft)
        assertEquals(bill.id, reminder.bill.id)

        runSuspend { e.reminders.markNotified(reminder) }
        assertTrue(e.due(d(2026, 9, 22)).isEmpty())
        assertTrue(e.due(d(2026, 9, 23)).isEmpty())
    }

    @Test
    fun `pada hari jatuh tempo disapa lagi walau sudah disapa saat mendekati`() {
        val e = env()
        e.bill(due = d(2026, 9, 25))
        runSuspend { e.reminders.markNotified(e.due(d(2026, 9, 22)).single()) }

        val reminder = e.due(d(2026, 9, 25)).single()

        assertEquals(BillReminderStage.DUE_TODAY, reminder.stage)
        assertEquals(0, reminder.daysLeft)
    }

    @Test
    fun `terlambat disapa sekali lalu diam sampai dibayar`() {
        val e = env()
        e.bill(due = d(2026, 9, 25))
        runSuspend { e.reminders.markNotified(e.due(d(2026, 9, 25)).single()) }

        val late = e.due(d(2026, 9, 27)).single()
        assertEquals(BillReminderStage.OVERDUE, late.stage)
        assertEquals(-2, late.daysLeft)

        runSuspend { e.reminders.markNotified(late) }
        assertTrue(e.due(d(2026, 9, 28)).isEmpty())
        assertTrue(e.due(d(2026, 10, 20)).isEmpty())
    }

    @Test
    fun `tahap yang terlewat tidak disapa mundur`() {
        val e = env()
        e.bill(due = d(2026, 9, 25))
        // Aplikasi baru dibuka setelah jatuh tempo: langsung terlambat, tidak disapa lagi untuk tahap "mendekati".
        val late = e.due(d(2026, 9, 26)).single()
        runSuspend { e.reminders.markNotified(late) }

        assertTrue(e.due(d(2026, 9, 26)).isEmpty())
    }

    @Test
    fun `membayar memulai daur baru untuk jatuh tempo berikutnya`() {
        val e = env()
        val bill = e.bill(due = d(2026, 9, 25))
        runSuspend { e.reminders.markNotified(e.due(d(2026, 9, 25)).single()) }

        runSuspend { e.service.pay(bill.id, d(2026, 9, 25)) }

        assertTrue(e.due(d(2026, 9, 26)).isEmpty())
        assertEquals(BillReminderStage.APPROACHING, e.due(d(2026, 10, 22)).single().stage)
    }

    @Test
    fun `tagihan yang dijeda atau lunas tidak pernah disapa`() {
        val e = env()
        val paused = e.bill(name = "Gym", due = d(2026, 9, 25))
        val done = e.bill(name = "Pajak", due = d(2026, 9, 25), frequency = null)
        runSuspend { e.service.setActive(paused.id, false) }
        runSuspend { e.service.pay(done.id, d(2026, 9, 25)) }

        assertTrue(e.due(d(2026, 9, 25)).isEmpty())
    }

    @Test
    fun `beberapa tagihan disapa terurut menurut jatuh tempo`() {
        val e = env()
        e.bill(name = "Air", due = d(2026, 9, 24))
        e.bill(name = "Listrik", due = d(2026, 9, 22))

        assertEquals(listOf("Listrik", "Air"), e.due(d(2026, 9, 22)).map { it.bill.name })
    }

    @Test
    fun `penanda yang rusak dianggap belum pernah disapa`() {
        val e = env()
        val bill = e.bill(due = d(2026, 9, 25))
        runSuspend { e.settings.put("bill_reminder_notified_${bill.id}", "bukan-penanda") }

        assertEquals(BillReminderStage.DUE_TODAY, e.due(d(2026, 9, 25)).single().stage)
    }
}
