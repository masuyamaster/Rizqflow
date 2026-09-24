package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportLoaderTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private class Env(val f: LedgerFixture) {
        val loader = ReportLoader(f.store, f.store)
        val keluarga = f.room("Keluarga")
        val diri = f.room("Diri")
    }

    private fun env() = Env(LedgerFixture().standard())

    private suspend fun Env.expense(amount: Long, room: Room, category: Category, date: LocalDate, note: String? = null) {
        f.ledger.recordExpense(NewExpense(rupiah(amount), f.account.id, room.id, category.id, date, note))
    }

    private suspend fun Env.income(amount: Long, date: LocalDate) {
        f.ledger.recordIncome(NewIncome(rupiah(amount), f.account.id, "Gaji", date))
    }

    private fun Env.categoryOf(room: Room): Category = f.store.categoryRows.values.first { it.roomId == room.id && !it.isSystem }

    /** Kategori tambahan langsung ke penyimpanan tiruan, di luar kategori bawaan pola Tiga hak. */
    private fun Env.addCategory(room: Room, name: String): Category {
        val category = Category(CategoryId("cat-${f.store.categoryRows.size}"), room.id, name, sortOrder = f.store.categoryRows.size)
        f.store.categoryRows[category.id] = category
        return category
    }

    private fun Env.archive(room: Room) {
        f.store.roomRows[room.id] = room.copy(archived = true)
    }

    @Test
    fun `bulan tanpa transaksi menghasilkan laporan kosong tanpa pembagi nol`() = runSuspend {
        val e = env()

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertFalse(report.hasData)
        assertEquals(rupiah(0), report.income.current)
        assertEquals(rupiah(0), report.expense.current)
        assertNull(report.income.percentBp)
        assertTrue(report.roomSpend.isEmpty())
        assertTrue(report.topCategories.isEmpty())
    }

    @Test
    fun `total pemasukan dan pengeluaran bulan ini dijumlahkan benar`() = runSuspend {
        val e = env()
        val kategori = e.categoryOf(e.keluarga)
        e.income(5_000_000, LocalDate.of(2026, 9, 1))
        e.income(1_000_000, LocalDate.of(2026, 9, 15))
        e.expense(300_000, e.keluarga, kategori, LocalDate.of(2026, 9, 5))
        e.expense(200_000, e.keluarga, kategori, LocalDate.of(2026, 9, 20))

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(rupiah(6_000_000), report.income.current)
        assertEquals(rupiah(500_000), report.expense.current)
        assertEquals(rupiah(5_500_000), report.net.current)
        assertTrue(report.hasData)
    }

    @Test
    fun `perbandingan bulan lalu menghitung selisih dan persen`() = runSuspend {
        val e = env()
        val kategori = e.categoryOf(e.keluarga)
        e.income(4_000_000, LocalDate.of(2026, 8, 1))
        e.expense(1_000_000, e.keluarga, kategori, LocalDate.of(2026, 8, 10))
        e.income(5_000_000, LocalDate.of(2026, 9, 1))
        e.expense(1_500_000, e.keluarga, kategori, LocalDate.of(2026, 9, 10))

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(rupiah(4_000_000), report.income.previous)
        assertEquals(rupiah(1_000_000), report.expense.previous)
        assertEquals(rupiah(1_000_000), report.income.delta)
        assertEquals(rupiah(500_000), report.expense.delta)
        assertEquals(2_500, report.income.percentBp) // 1.000.000 / 4.000.000 = 25%
        assertEquals(5_000, report.expense.percentBp) // 500.000 / 1.000.000 = 50%
    }

    @Test
    fun `penurunan dibanding bulan lalu menghasilkan delta dan persen negatif`() = runSuspend {
        val e = env()
        val kategori = e.categoryOf(e.keluarga)
        e.expense(1_000_000, e.keluarga, kategori, LocalDate.of(2026, 8, 10))
        e.expense(400_000, e.keluarga, kategori, LocalDate.of(2026, 9, 10))

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(rupiah(-600_000), report.expense.delta)
        assertEquals(-6_000, report.expense.percentBp) // turun 60%
        assertTrue(report.expense.delta.isNegative)
    }

    @Test
    fun `bulan lalu nol tidak punya dasar persentase tapi delta tetap ada`() = runSuspend {
        val e = env()
        val kategori = e.categoryOf(e.keluarga)
        e.expense(250_000, e.keluarga, kategori, LocalDate.of(2026, 9, 5))

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(rupiah(0), report.expense.previous)
        assertEquals(rupiah(250_000), report.expense.delta)
        assertNull(report.expense.percentBp)
    }

    @Test
    fun `pengeluaran per ruang dijumlahkan dan diurutkan terbesar dulu`() = runSuspend {
        val e = env()
        val kategoriKeluarga = e.categoryOf(e.keluarga)
        val kategoriDiri = e.categoryOf(e.diri)
        e.expense(200_000, e.keluarga, kategoriKeluarga, LocalDate.of(2026, 9, 3))
        e.expense(300_000, e.keluarga, kategoriKeluarga, LocalDate.of(2026, 9, 4))
        e.expense(150_000, e.diri, kategoriDiri, LocalDate.of(2026, 9, 5))

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(listOf(e.keluarga.id, e.diri.id), report.roomSpend.map { it.room.id })
        assertEquals(rupiah(500_000), report.roomSpend[0].amount)
        assertEquals(rupiah(150_000), report.roomSpend[1].amount)
    }

    @Test
    fun `ruang tanpa pengeluaran bulan ini tidak muncul di rincian`() = runSuspend {
        val e = env()
        val kategori = e.categoryOf(e.keluarga)
        e.expense(100_000, e.keluarga, kategori, LocalDate.of(2026, 9, 3))

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(listOf(e.keluarga.id), report.roomSpend.map { it.room.id })
    }

    @Test
    fun `kategori terbesar dibatasi lima dan diurutkan menurun`() = runSuspend {
        val e = env()
        val categories = (1..7).map { i -> e.addCategory(e.keluarga, "Kategori $i") }

        categories.forEachIndexed { index, category ->
            e.expense((7 - index) * 100_000L, e.keluarga, category, LocalDate.of(2026, 9, index + 1))
        }

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(MonthlyReport.MAX_CATEGORIES, report.topCategories.size)
        assertEquals(rupiah(700_000), report.topCategories.first().amount)
        assertEquals(listOf(700_000L, 600_000L, 500_000L, 400_000L, 300_000L), report.topCategories.map { it.amount.minor })
    }

    @Test
    fun `ruang dan kategori yang sudah diarsipkan tetap terhitung di laporan`() = runSuspend {
        val e = env()
        val kategori = e.categoryOf(e.keluarga)
        e.expense(400_000, e.keluarga, kategori, LocalDate.of(2026, 9, 5))
        // Diarsipkan setelah dicatat: laporan bulan itu tetap menyebutnya, beda dari Denah yang hanya ruang aktif.
        e.archive(e.keluarga)

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(listOf(e.keluarga.id), report.roomSpend.map { it.room.id })
        assertTrue(report.roomSpend.single().room.archived)
        assertEquals(kategori.id, report.topCategories.single().category.id)
    }

    @Test
    fun `hanya pengeluaran dan pemasukan dihitung tidak termasuk transfer pinjaman atau tagihan`() = runSuspend {
        val e = env()
        val kategori = e.categoryOf(e.keluarga)
        val other = e.f.addAccount("Tabungan", opening = 1_000_000)
        e.f.ledger.recordTransfer(NewTransfer(rupiah(200_000), e.f.account.id, other.id, LocalDate.of(2026, 9, 5)))
        e.f.ledger.recordLoanMovement(NewLoanMovement(TransactionKind.LOAN_OUT, rupiah(150_000), e.f.account.id, LocalDate.of(2026, 9, 6)))
        e.expense(50_000, e.keluarga, kategori, LocalDate.of(2026, 9, 7), note = "bayar-x")

        val report = e.loader.load(YearMonth.of(2026, 9))

        assertEquals(rupiah(50_000), report.expense.current)
        assertEquals(rupiah(0), report.income.current)
    }
}
