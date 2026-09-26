package com.roziqrizal.rizqflow.domain.recurring

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerFixture
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceScheduleTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun sequence(start: LocalDate, frequency: Frequency, count: Int): List<LocalDate> {
        val out = mutableListOf(start)
        repeat(count - 1) { out += RecurrenceSchedule.next(start, frequency, out.last()) }
        return out
    }

    @Test
    fun `harian maju satu hari`() {
        assertEquals(listOf(d(2026, 9, 29), d(2026, 9, 30), d(2026, 10, 1)), sequence(d(2026, 9, 29), Frequency.DAILY, 3))
    }

    @Test
    fun `mingguan maju tujuh hari dan melewati pergantian bulan`() {
        assertEquals(listOf(d(2026, 9, 24), d(2026, 10, 1), d(2026, 10, 8)), sequence(d(2026, 9, 24), Frequency.WEEKLY, 3))
    }

    @Test
    fun `bulanan yang mulai tanggal 31 jatuh di hari terakhir bulan pendek lalu kembali ke 31`() {
        assertEquals(
            listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30), d(2026, 5, 31)),
            sequence(d(2026, 1, 31), Frequency.MONTHLY, 5),
        )
    }

    @Test
    fun `bulanan yang mulai tanggal 29 Februari kabisat kembali ke 28 lalu 29 pada tahun kabisat berikutnya`() {
        val seq = sequence(d(2028, 2, 29), Frequency.MONTHLY, 13)
        assertEquals(d(2028, 3, 29), seq[1])
        assertEquals(d(2029, 2, 28), seq[12])
    }

    @Test
    fun `bulanan melewati pergantian tahun`() {
        assertEquals(listOf(d(2026, 11, 15), d(2026, 12, 15), d(2027, 1, 15)), sequence(d(2026, 11, 15), Frequency.MONTHLY, 3))
    }

    @Test
    fun `kemunculan berikutnya dari tanggal di antara dua kemunculan bulanan`() {
        val start = d(2026, 1, 15)
        assertEquals(d(2026, 9, 15), RecurrenceSchedule.next(start, Frequency.MONTHLY, d(2026, 8, 20)))
        assertEquals(d(2026, 10, 15), RecurrenceSchedule.next(start, Frequency.MONTHLY, d(2026, 9, 15)))
        assertEquals(d(2026, 9, 15), RecurrenceSchedule.next(start, Frequency.MONTHLY, d(2026, 9, 14)))
    }

    @Test
    fun `kemunculan berikutnya dari tanggal di antara dua kemunculan mingguan`() {
        val start = d(2026, 9, 1)
        assertEquals(d(2026, 9, 8), RecurrenceSchedule.next(start, Frequency.WEEKLY, d(2026, 9, 7)))
        assertEquals(d(2026, 9, 15), RecurrenceSchedule.next(start, Frequency.WEEKLY, d(2026, 9, 8)))
    }

    @Test
    fun `sebelum tanggal awal kemunculan pertamanya adalah tanggal awal itu sendiri`() {
        for (f in Frequency.entries) {
            assertEquals(d(2026, 9, 24), RecurrenceSchedule.next(d(2026, 9, 24), f, d(2026, 1, 1)), "$f")
            assertEquals(d(2026, 9, 24), RecurrenceSchedule.firstOnOrAfter(d(2026, 9, 24), f, d(2026, 9, 24)), "$f")
        }
    }

    @Test
    fun `firstOnOrAfter mengembalikan tanggal itu sendiri bila tepat kemunculan`() {
        val start = d(2026, 1, 15)
        assertEquals(d(2026, 9, 15), RecurrenceSchedule.firstOnOrAfter(start, Frequency.MONTHLY, d(2026, 9, 15)))
        assertEquals(d(2026, 10, 15), RecurrenceSchedule.firstOnOrAfter(start, Frequency.MONTHLY, d(2026, 9, 16)))
    }
}

class RecurringServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private class Env(val f: LedgerFixture) {
        val store = InMemoryRecurring()
        val service = RecurringService(store, f.store, f.store, f.store, f.ledger, f.newId)
        val keluarga = f.room("Keluarga")
        val kebutuhan = f.store.categoryRows.values.first { it.roomId == keluarga.id && !it.isSystem }
        val savingsAccount = f.addAccount("Tabungan", opening = 1_000_000)
    }

    private fun env() = Env(LedgerFixture().standard())

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success).value

    private fun <T> LedgerResult<T>.error() = (this as LedgerResult.Failure).error

    private fun Env.rent(
        amount: Long = 1_500_000,
        frequency: Frequency = Frequency.MONTHLY,
        start: LocalDate = f.today,
        end: LocalDate? = null,
        today: LocalDate = start,
    ) = runSuspend {
        service.create(
            RecurringDraft(
                kind = TransactionKind.EXPENSE, amount = rupiah(amount), accountId = f.account.id, roomId = keluarga.id,
                categoryId = kebutuhan.id, note = "Kontrakan", frequency = frequency, startDate = start, endDate = end,
            ),
            today,
        ).value()
    }

    private fun Env.run(today: LocalDate) = runSuspend { service.runDue(today) }

    private fun Env.recurringTransactions() = f.store.transactionRows.values.filter { it.origin == TransactionOrigin.RECURRING }.sortedBy { it.occurredOn }

    // ------------------------------------------------------------------ membuat dan mengubah

    @Test
    fun `aturan baru menunggu di tanggal awalnya dan belum mencatat apa pun`() {
        val e = env()
        val rule = e.rent(start = d(2026, 10, 1), today = e.f.today)

        assertEquals(d(2026, 10, 1), rule.nextDue)
        assertTrue(e.run(e.f.today).let { it.recorded == 0 && it.blocked.isEmpty() })
        assertTrue(e.recurringTransactions().isEmpty())
    }

    @Test
    fun `tanggal awal yang sudah lewat ditolak`() {
        val e = env()
        val result = runSuspend {
            e.service.create(
                RecurringDraft(TransactionKind.EXPENSE, rupiah(10_000), e.f.account.id, roomId = e.keluarga.id, categoryId = e.kebutuhan.id, frequency = Frequency.DAILY, startDate = e.f.today.minusDays(1)),
                e.f.today,
            )
        }
        assertEquals(LedgerError.INVALID_SCHEDULE, result.error())
    }

    @Test
    fun `tanggal akhir sebelum tanggal awal ditolak`() {
        val e = env()
        val result = runSuspend {
            e.service.create(
                RecurringDraft(TransactionKind.INCOME, rupiah(10_000), e.f.account.id, frequency = Frequency.DAILY, startDate = e.f.today, endDate = e.f.today.minusDays(1)),
                e.f.today,
            )
        }
        assertEquals(LedgerError.INVALID_SCHEDULE, result.error())
    }

    @Test
    fun `isian yang tidak sah ditolak dengan alasannya`() {
        val e = env()
        fun create(draft: RecurringDraft) = runSuspend { e.service.create(draft, e.f.today) }.error()
        val base = RecurringDraft(TransactionKind.EXPENSE, rupiah(10_000), e.f.account.id, roomId = e.keluarga.id, categoryId = e.kebutuhan.id, frequency = Frequency.DAILY, startDate = e.f.today)

        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, create(base.copy(amount = rupiah(0))))
        assertEquals(LedgerError.ROOM_NOT_FOUND, create(base.copy(roomId = null)))
        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, create(base.copy(categoryId = e.f.category("Diri", "Investasi").id)))
        assertEquals(LedgerError.SAME_ACCOUNT, create(base.copy(kind = TransactionKind.TRANSFER, toAccountId = e.f.account.id)))
        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, create(base.copy(kind = TransactionKind.TRANSFER, toAccountId = null)))
        assertEquals(LedgerError.NOTE_TOO_LONG, create(base.copy(note = "x".repeat(201))))
        assertTrue(e.store.rows.isEmpty())
    }

    @Test
    fun `isian yang tidak berlaku bagi jenisnya dibuang`() {
        val e = env()
        val rule = runSuspend {
            e.service.create(
                RecurringDraft(
                    kind = TransactionKind.INCOME, amount = rupiah(5_000_000), accountId = e.f.account.id, toAccountId = e.savingsAccount.id,
                    roomId = e.keluarga.id, categoryId = e.kebutuhan.id, incomeSource = "  Gaji  ", frequency = Frequency.MONTHLY, startDate = e.f.today,
                ),
                e.f.today,
            ).value()
        }
        assertNull(rule.toAccountId)
        assertNull(rule.roomId)
        assertNull(rule.categoryId)
        assertEquals("Gaji", rule.incomeSource)
    }

    @Test
    fun `mengubah jadwal menghitung ulang kemunculan berikutnya mulai hari ini`() {
        val e = env()
        val rule = e.rent(start = e.f.today)
        val draft = e.draftOf(rule).copy(frequency = Frequency.WEEKLY, startDate = e.f.today.plusDays(3))

        val updated = runSuspend { e.service.update(rule.id, draft, e.f.today) }.value()

        assertEquals(Frequency.WEEKLY, updated.frequency)
        assertEquals(e.f.today.plusDays(3), updated.nextDue)
    }

    @Test
    fun `mengubah nominal tanpa mengubah jadwal tidak menggeser kemunculan berikutnya`() {
        val e = env()
        val rule = e.rent(start = d(2026, 9, 1), today = d(2026, 9, 1))
        e.run(d(2026, 9, 21)) // mencatat 1 September; berikutnya 1 Oktober
        val draft = e.draftOf(rule).copy(amount = rupiah(2_000_000))

        val updated = runSuspend { e.service.update(rule.id, draft, e.f.today) }.value()

        assertEquals(d(2026, 10, 1), updated.nextDue)
        assertEquals(rupiah(2_000_000), updated.amount)
    }

    private fun Env.draftOf(rule: RecurringRule) = RecurringDraft(
        kind = rule.kind, amount = rule.amount, accountId = rule.accountId, toAccountId = rule.toAccountId, roomId = rule.roomId,
        categoryId = rule.categoryId, incomeSource = rule.incomeSource, note = rule.note, frequency = rule.frequency,
        startDate = rule.startDate, endDate = rule.endDate,
    )

    // ------------------------------------------------------------------ mencatat

    @Test
    fun `aplikasi yang lama tidak dibuka mencatat semua kemunculan yang terlewat`() {
        val e = env()
        e.rent(start = d(2026, 6, 15), today = d(2026, 6, 15))

        val run = e.run(d(2026, 9, 21))

        assertEquals(4, run.recorded) // 15 Juni, Juli, Agustus, September
        assertEquals(listOf(d(2026, 6, 15), d(2026, 7, 15), d(2026, 8, 15), d(2026, 9, 15)), e.recurringTransactions().map { it.occurredOn })
        assertEquals(d(2026, 10, 15), e.store.rows.values.single().nextDue)
    }

    @Test
    fun `menjalankan dua kali pada hari yang sama tidak menggandakan transaksi`() {
        val e = env()
        e.rent(start = d(2026, 9, 1), today = d(2026, 9, 1))

        assertEquals(1, e.run(d(2026, 9, 21)).recorded)
        assertEquals(0, e.run(d(2026, 9, 21)).recorded)

        assertEquals(1, e.recurringTransactions().size)
    }

    @Test
    fun `kemunculan yang sudah tercatat tapi kemajuan aturan belum tersimpan dilewati bukan digandakan`() {
        val e = env()
        val rule = e.rent(start = d(2026, 9, 1), today = d(2026, 9, 1))
        // Meniru aplikasi mati tepat setelah transaksi tersimpan dan sebelum aturan maju.
        runSuspend {
            e.f.ledger.recordExpense(
                com.roziqrizal.rizqflow.domain.ledger.NewExpense(
                    rule.amount, rule.accountId, rule.roomId!!, rule.categoryId!!, d(2026, 9, 1), rule.note,
                    TransactionOrigin.RECURRING, id = RecurringService.occurrenceId(rule.id, d(2026, 9, 1)),
                ),
            )
        }

        val run = e.run(d(2026, 9, 21))

        assertEquals(0, run.recorded)
        assertEquals(1, e.recurringTransactions().size)
        assertEquals(d(2026, 10, 1), e.store.rows.getValue(rule.id).nextDue)
    }

    @Test
    fun `transaksi yang dihapus pengguna tidak dicatat ulang`() {
        val e = env()
        e.rent(start = d(2026, 9, 1), today = d(2026, 9, 1))
        e.run(d(2026, 9, 21))
        runSuspend { e.f.ledger.delete(e.recurringTransactions().single().id) }

        assertEquals(0, e.run(d(2026, 9, 22)).recorded)
        assertTrue(e.recurringTransactions().isEmpty())
    }

    @Test
    fun `pengeluaran berulang ikut memakai jatah ruangnya dan saldo akunnya`() {
        val e = env()
        e.rent(amount = 100_000, frequency = Frequency.DAILY, start = d(2026, 9, 19), today = d(2026, 9, 19))
        val before = e.f.balance()

        e.run(d(2026, 9, 21)) // 19, 20, 21

        assertEquals(before - rupiah(300_000), e.f.balance())
        val tx = e.recurringTransactions().first()
        assertEquals(e.keluarga.id, tx.roomId)
        assertEquals(e.kebutuhan.id, tx.categoryId)
        assertEquals("Kontrakan", tx.note)
    }

    @Test
    fun `pemasukan berulang dialirkan ke ruang sesuai aturan alokasi seperti pemasukan biasa`() {
        val e = env()
        runSuspend {
            e.service.create(
                RecurringDraft(TransactionKind.INCOME, rupiah(10_000_000), e.f.account.id, incomeSource = "Gaji", frequency = Frequency.MONTHLY, startDate = d(2026, 9, 1)),
                d(2026, 9, 1),
            )
        }

        e.run(d(2026, 9, 21))

        val income = e.recurringTransactions().single()
        assertEquals(TransactionKind.INCOME, income.kind)
        assertEquals("Gaji", income.incomeSource)
        val entries = runSuspend { e.f.store.entriesOf(income.id) }
        assertTrue(entries.isNotEmpty())
        assertEquals(rupiah(10_000_000), entries.fold(Money.zero(income.amount.currency)) { acc, it -> acc + it.amount })
    }

    @Test
    fun `transfer berulang memindahkan uang antar akun`() {
        val e = env()
        runSuspend {
            e.service.create(
                RecurringDraft(TransactionKind.TRANSFER, rupiah(250_000), e.f.account.id, toAccountId = e.savingsAccount.id, frequency = Frequency.WEEKLY, startDate = d(2026, 9, 7)),
                d(2026, 9, 7),
            )
        }
        val walletBefore = e.f.balance()
        val savingsBefore = e.f.balance(e.savingsAccount.id)

        e.run(d(2026, 9, 21)) // 7, 14, 21

        assertEquals(walletBefore - rupiah(750_000), e.f.balance())
        assertEquals(savingsBefore + rupiah(750_000), e.f.balance(e.savingsAccount.id))
    }

    @Test
    fun `aturan selesai setelah tanggal akhir dan tidak lagi dicatat`() {
        val e = env()
        val rule = e.rent(frequency = Frequency.DAILY, start = d(2026, 9, 19), end = d(2026, 9, 20), today = d(2026, 9, 19))

        assertEquals(2, e.run(d(2026, 9, 25)).recorded)

        val stored = e.store.rows.getValue(rule.id)
        assertTrue(stored.isFinished)
        assertEquals(0, e.run(d(2026, 10, 25)).recorded)
    }

    @Test
    fun `aturan harian yang sangat lama tak dibuka menyusul bertahap sesuai batas per panggilan`() {
        val e = env()
        e.rent(amount = 1_000, frequency = Frequency.DAILY, start = d(2024, 1, 1), today = d(2024, 1, 1))
        val today = d(2026, 1, 1) // 731 hari kemudian: 732 kemunculan termasuk hari pertama
        val total = 732

        val first = e.run(today)
        val second = e.run(today)

        assertEquals(RecurringService.MAX_CATCH_UP, first.recorded)
        assertEquals(total - RecurringService.MAX_CATCH_UP, second.recorded)
        assertEquals(0, e.run(today).recorded)
    }

    // ------------------------------------------------------------------ jeda, tertahan, hapus

    @Test
    fun `aturan yang dijeda tidak dicatat dan melanjutkan tidak mengejar yang terlewat`() {
        val e = env()
        val rule = e.rent(start = d(2026, 6, 15), today = d(2026, 6, 15))
        runSuspend { e.service.setActive(rule.id, false, d(2026, 6, 20)) }

        assertEquals(0, e.run(d(2026, 9, 21)).recorded)

        val resumed = runSuspend { e.service.setActive(rule.id, true, d(2026, 9, 21)) }.value()
        assertEquals(d(2026, 10, 15), resumed.nextDue)
        assertEquals(0, e.run(d(2026, 9, 21)).recorded)
        assertEquals(1, e.run(d(2026, 10, 15)).recorded)
    }

    @Test
    fun `akun yang diarsipkan menahan aturan itu saja dan menyusul setelah dipulihkan`() {
        val e = env()
        val rent = e.rent(start = d(2026, 9, 1), today = d(2026, 9, 1))
        runSuspend {
            e.service.create(
                RecurringDraft(TransactionKind.INCOME, rupiah(1_000_000), e.savingsAccount.id, incomeSource = "Bunga", frequency = Frequency.MONTHLY, startDate = d(2026, 9, 1)),
                d(2026, 9, 1),
            )
        }
        runSuspend { e.f.store.save(e.f.account.copy(archived = true)) }

        val run = e.run(d(2026, 9, 21))

        assertEquals(1, run.recorded) // hanya bunga yang jalan
        val blocked = run.blocked.single()
        assertEquals(rent.id, blocked.rule.id)
        assertEquals(d(2026, 9, 1), blocked.date)
        assertEquals(LedgerError.ACCOUNT_ARCHIVED, blocked.error)
        assertEquals(d(2026, 9, 1), e.store.rows.getValue(rent.id).nextDue)
        assertFalse(runSuspend { e.service.list() }.first { it.rule.id == rent.id }.usable)

        runSuspend { e.f.store.save(e.f.account.copy(archived = false)) }
        assertEquals(1, e.run(d(2026, 9, 21)).recorded)
        assertTrue(e.run(d(2026, 9, 21)).blocked.isEmpty())
    }

    @Test
    fun `menghapus aturan tidak menghapus transaksi yang sudah dicatat`() {
        val e = env()
        val rule = e.rent(start = d(2026, 9, 1), today = d(2026, 9, 1))
        e.run(d(2026, 9, 21))

        assertIs<LedgerResult.Success<Unit>>(runSuspend { e.service.delete(rule.id) })

        assertTrue(e.store.rows.isEmpty())
        assertEquals(1, e.recurringTransactions().size)
        assertEquals(LedgerError.RECURRING_NOT_FOUND, runSuspend { e.service.delete(rule.id) }.error())
    }

    @Test
    fun `daftar menampilkan nama tujuan dan menaruh yang aktif dan terdekat di atas`() {
        val e = env()
        val later = e.rent(amount = 1, start = d(2026, 12, 1), today = e.f.today)
        val sooner = e.rent(amount = 2, start = d(2026, 10, 1), today = e.f.today)
        val paused = e.rent(amount = 3, start = d(2026, 9, 25), today = e.f.today)
        runSuspend { e.service.setActive(paused.id, false, e.f.today) }

        val rows = runSuspend { e.service.list() }

        assertEquals(listOf(sooner.id, later.id, paused.id), rows.map { it.rule.id })
        val row = rows.first()
        assertEquals("Dompet", row.accountName)
        assertEquals("Keluarga", row.roomName)
        assertEquals(e.kebutuhan.name, row.categoryName)
        assertTrue(row.usable)
        assertNotNull(row.rule.note)
    }

    @Test
    fun `mencatat sebuah kemunculan lewat ledger memakai pengenal tetap`() {
        val e = env()
        val rule = e.rent(start = d(2026, 9, 1), today = d(2026, 9, 1))
        e.run(d(2026, 9, 21))

        val id = TransactionId(RecurringService.occurrenceId(rule.id, d(2026, 9, 1)))
        assertNotNull(e.f.store.transactionRows[id])
    }
}
