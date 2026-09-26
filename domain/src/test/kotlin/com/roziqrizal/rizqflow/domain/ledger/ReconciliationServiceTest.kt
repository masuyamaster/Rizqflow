package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReconciliationServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun service(f: LedgerFixture) = ReconciliationService(f.store, f.store, f.store, f.ledger)

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success).value

    private fun <T> LedgerResult<T>.error(): LedgerError = (this as LedgerResult.Failure).error

    // ------------------------------------------------------------------ ikhtisar dan ruang usulan

    @Test
    fun `ikhtisar memuat saldo tercatat dan ruang usulan dari pengeluaran terakhir`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000)
        runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(150_000), f.account.id, f.room("Diri").id, f.category("Diri", "Investasi").id, f.today))
        }

        val overview = runSuspend { service(f).overview(f.account.id) }!!

        assertEquals(rupiah(1_350_000), overview.recorded)
        assertEquals(f.room("Diri").id, overview.suggestedRoomId)
    }

    @Test
    fun `tanpa pengeluaran ruang usulan bertipe Mencukupi lalu ruang aktif pertama`() {
        val f = LedgerFixture().standard()

        assertEquals(f.room("Keluarga").id, runSuspend { service(f).overview(f.account.id) }!!.suggestedRoomId)

        runSuspend { f.rules.archiveRoom(f.room("Keluarga").id) }
        assertEquals(f.room("Memberi").id, runSuspend { service(f).overview(f.account.id) }!!.suggestedRoomId)
    }

    @Test
    fun `akun yang tidak ada menghasilkan overview null`() {
        val f = LedgerFixture().standard()

        assertNull(runSuspend { service(f).overview(com.roziqrizal.rizqflow.domain.model.AccountId("hilang")) })
    }

    // ------------------------------------------------------------------ mencocokkan

    @Test
    fun `saldo sama tidak mencatat apa pun tetapi memperbarui tanggal cocok`() {
        val f = LedgerFixture().standard()

        val hasil = runSuspend { service(f).correct(f.account.id, f.balance(), f.room("Keluarga").id, f.today) }

        assertIs<LedgerResult.Success<*>>(hasil)
        assertNull(hasil.value())
        assertTrue(f.store.transactionRows.isEmpty())
        assertEquals(f.today, f.store.accountRows.getValue(f.account.id).lastReconciledOn)
    }

    @Test
    fun `saldo sebenarnya lebih kecil mencatat pengeluaran Tak terlacak dan mengurangi saldo`() {
        val f = LedgerFixture().standard()
        val sebelum = f.balance()

        val hasil = runSuspend { service(f).correct(f.account.id, sebelum - rupiah(47_000), f.room("Keluarga").id, f.today) }

        assertIs<LedgerResult.Success<*>>(hasil)
        val tx = runSuspend { f.store.find(hasil.value()!!) }!!
        assertEquals(TransactionKind.EXPENSE, tx.kind)
        assertEquals(rupiah(47_000), tx.amount)
        assertEquals(TransactionOrigin.CORRECTION, tx.origin)
        assertEquals(f.category("Keluarga", "Tak terlacak").id, tx.categoryId)
        assertEquals(sebelum - rupiah(47_000), f.balance())
    }

    @Test
    fun `saldo sebenarnya lebih besar mencatat pemasukan dan dialirkan seperti pemasukan biasa`() {
        val f = LedgerFixture().standard()
        val sebelum = f.balance()

        val hasil = runSuspend { service(f).correct(f.account.id, sebelum + rupiah(200_000), f.room("Keluarga").id, f.today) }

        val tx = runSuspend { f.store.find(hasil.value()!!) }!!
        assertEquals(TransactionKind.INCOME, tx.kind)
        assertEquals(rupiah(200_000), tx.amount)
        assertEquals(TransactionOrigin.CORRECTION, tx.origin)
        assertEquals(ReconciliationService.CORRECTION_SOURCE, tx.incomeSource)
        assertEquals(sebelum + rupiah(200_000), f.balance())
        // Pemasukan biasa: dialirkan mengikuti aturan alokasi sekarang (10/30/60), bukan ke ruang usulan saja.
        val entries = runSuspend { f.store.entriesOf(tx.id) }
        assertEquals(3, entries.size)
        assertEquals(rupiah(20_000), entries.first { it.roomId == f.room("Memberi").id }.amount)
    }

    @Test
    fun `ruang yang dipakai untuk selisih bisa berbeda dari ruang usulan`() {
        val f = LedgerFixture().standard()

        runSuspend { service(f).correct(f.account.id, f.balance() - rupiah(10_000), f.room("Diri").id, f.today) }

        val tx = runSuspend { f.store.transactionRows.values.single { it.kind == TransactionKind.EXPENSE } }
        assertEquals(f.room("Diri").id, tx.roomId)
    }

    @Test
    fun `koreksi ditolak untuk akun yang tidak ada ruang yang tidak ada atau ruang terarsip`() {
        val f = LedgerFixture().standard()
        val hilangAkun = com.roziqrizal.rizqflow.domain.model.AccountId("hilang")
        val hilangRuang = RoomId("hilang")

        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, runSuspend { service(f).correct(hilangAkun, rupiah(0), f.room("Keluarga").id, f.today) }.error())
        assertEquals(LedgerError.ROOM_NOT_FOUND, runSuspend { service(f).correct(f.account.id, f.balance() - rupiah(1), hilangRuang, f.today) }.error())

        runSuspend { f.rules.archiveRoom(f.room("Keluarga").id) }
        assertEquals(LedgerError.ROOM_ARCHIVED, runSuspend { service(f).correct(f.account.id, f.balance() - rupiah(1), f.room("Keluarga").id, f.today) }.error())
    }

    @Test
    fun `saldo sebenarnya tidak boleh negatif`() {
        val f = LedgerFixture().standard()

        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, runSuspend { service(f).correct(f.account.id, rupiah(-1), f.room("Keluarga").id, f.today) }.error())
    }

    @Test
    fun `mencocokkan dua kali di hari berbeda memperbarui tanggal cocok terbaru`() {
        val f = LedgerFixture().standard()

        runSuspend { service(f).correct(f.account.id, f.balance(), f.room("Keluarga").id, f.today.minusDays(3)) }
        runSuspend { service(f).correct(f.account.id, f.balance(), f.room("Keluarga").id, f.today) }

        assertEquals(f.today, f.store.accountRows.getValue(f.account.id).lastReconciledOn)
    }
}
