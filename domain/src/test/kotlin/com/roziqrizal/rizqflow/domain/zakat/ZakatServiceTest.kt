package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.calendar.UmmAlQuraCalendar
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerFixture
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.WealthKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZakatServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private class Env(f: LedgerFixture) {
        val zakat = InMemoryZakat()
        val service = ZakatService(f.store, f.store, zakat, f.ledger, f.newId, UmmAlQuraCalendar())
        val room = f.room("Memberi").id
    }

    private fun env(): Pair<LedgerFixture, Env> {
        val f = LedgerFixture().standard()
        return f to Env(f)
    }

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success).value

    private fun <T> LedgerResult<T>.error() = (this as LedgerResult.Failure).error

    // ------------------------------------------------------------------ ringkasan mode persentase (bawaan template)

    @Test
    fun `ruang Memberi bawaan mulai di mode persentase donasi tanpa perlu harta`() {
        val (f, e) = env()
        f.income(10_000_000)

        val overview = runSuspend { e.service.overview(e.room, f.today) }!!

        val status = overview.status
        assertIs<GivingStatus.Percentage>(status)
        assertEquals(rupiah(1_000_000), status.target)
        assertTrue(overview.items.isEmpty())
        assertTrue(!overview.neverSetUp)
    }

    @Test
    fun `ruang yang tidak ada menghasilkan null`() {
        val (_, e) = env()

        assertNull(runSuspend { e.service.overview(RoomId("tidak-ada"), LocalDate.of(2026, 9, 21)) })
    }

    // ------------------------------------------------------------------ berpindah mode

    @Test
    fun `berpindah ke mode zakat sebelum harta diisi meminta harga emas dan harta`() {
        val (f, e) = env()
        assertIs<LedgerResult.Success<*>>(runSuspend { e.service.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) })

        val overview = runSuspend { e.service.overview(e.room, f.today) }!!

        assertTrue(overview.neverSetUp)
        val status = overview.status
        assertIs<GivingStatus.NeedsInput>(status)
        assertTrue(status.goldPriceMissing && status.wealthMissing)
    }

    @Test
    fun `mode tidak dikenal ditolak dan ruang yang tidak ada ditolak`() {
        val (_, e) = env()
        assertEquals(LedgerError.ROOM_NOT_FOUND, runSuspend { e.service.setGivingMode(RoomId("hilang"), ZakatHaulHijriStrategy.ID) }.error())
    }

    // ------------------------------------------------------------------ mengisi harta

    @Test
    fun `menyimpan harta menghitung nilai emas dari gram dan mencatat pemeriksaan hari itu`() {
        val (f, e) = env()
        runSuspend { e.service.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        val today = LocalDate.of(2026, 1, 10)

        val hasil = runSuspend {
            e.service.saveWealth(
                listOf(
                    NewWealthItem(kind = WealthKind.GOLD, label = "Emas", value = Money.zero(), goldMilligrams = 60_000),
                    NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(38_000_000)),
                    NewWealthItem(kind = WealthKind.INVESTMENT, label = "Investasi", value = rupiah(25_000_000)),
                    NewWealthItem(kind = WealthKind.RECEIVABLE, label = "Piutang", value = rupiah(4_000_000)),
                    NewWealthItem(kind = WealthKind.DEDUCTION, label = "Hutang", value = rupiah(14_600_000)),
                ),
                rupiah(1_660_000),
                today,
            )
        }

        assertIs<LedgerResult.Success<*>>(hasil)
        val overview = runSuspend { e.service.overview(e.room, today) }!!
        assertEquals(rupiah(99_600_000), overview.items.first { it.label == "Emas" }.value)
        val status = overview.status as GivingStatus.Zakat
        assertEquals(rupiah(152_000_000), status.netWealth)
        assertIs<HaulStatus.Running>(status.haul)
    }

    @Test
    fun `harga emas wajib positif dan item tidak boleh label kosong atau nilai negatif`() {
        val (f, e) = env()
        val today = f.today

        assertEquals(
            LedgerError.GOLD_PRICE_REQUIRED,
            runSuspend { e.service.saveWealth(emptyList(), rupiah(0), today) }.error(),
        )
        assertEquals(
            LedgerError.INVALID_WEALTH_ITEM,
            runSuspend { e.service.saveWealth(listOf(NewWealthItem(kind = WealthKind.OTHER, label = " ", value = rupiah(0))), rupiah(1_000), today) }.error(),
        )
    }

    @Test
    fun `menyimpan harta lagi mengganti seluruh daftar dan tidak menumpuk baris lama`() {
        val (f, e) = env()
        runSuspend { e.service.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        runSuspend { e.service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(10_000_000))), rupiah(1_000_000), f.today) }

        runSuspend { e.service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan baru", value = rupiah(20_000_000))), rupiah(1_100_000), f.today) }

        val overview = runSuspend { e.service.overview(e.room, f.today) }!!
        assertEquals(listOf("Tabungan baru"), overview.items.map { it.label })
    }

    // ------------------------------------------------------------------ haul: mulai, genap, reset

    @Test
    fun `haul dimulai saat harta pertama kali mencapai nisab dan genap setahun Hijriyah kemudian`() {
        val (f, e) = env()
        runSuspend { e.service.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        val awal = LocalDate.of(2026, 1, 10)
        runSuspend { e.service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }

        val berjalan = (runSuspend { e.service.overview(e.room, awal.plusMonths(6)) }!!.status as GivingStatus.Zakat).haul
        assertIs<HaulStatus.Running>(berjalan)
        assertEquals(awal, berjalan.start)

        val genap = UmmAlQuraCalendar().plusYears(awal)
        val status = runSuspend { e.service.overview(e.room, genap) }!!.status as GivingStatus.Zakat
        assertIs<HaulStatus.Completed>(status.haul)
        assertEquals(rupiah(5_000_000), status.zakatDue)
    }

    @Test
    fun `harta turun di bawah nisab mereset haul sesuai kebijakan bawaan`() {
        val (f, e) = env()
        runSuspend { e.service.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        val awal = LocalDate.of(2026, 1, 10)
        runSuspend { e.service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }
        runSuspend { e.service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(1_000_000))), rupiah(1_660_000), awal.plusMonths(2)) }

        val status = runSuspend { e.service.overview(e.room, awal.plusMonths(3)) }!!.status as GivingStatus.Zakat
        assertIs<HaulStatus.BelowNisab>(status.haul)
    }

    // ------------------------------------------------------------------ menunaikan zakat

    @Test
    fun `menunaikan zakat mencatat pengeluaran di kategori Zakat mal dan memulai haul baru`() {
        val (f, e) = env()
        runSuspend { e.service.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        val awal = LocalDate.of(2026, 1, 10)
        runSuspend { e.service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }
        val genap = UmmAlQuraCalendar().plusYears(awal)

        val hasil = runSuspend { e.service.payZakat(e.room, f.account.id, rupiah(5_000_000), genap) }
        assertIs<LedgerResult.Success<*>>(hasil)

        val tx = runSuspend { f.store.find(hasil.value()) }!!
        assertEquals(f.category("Memberi", "Zakat mal").id, tx.categoryId)
        assertEquals(rupiah(5_000_000), tx.amount)

        val overview = runSuspend { e.service.overview(e.room, genap) }!!
        assertEquals(1, overview.payments.size)
        assertEquals(rupiah(5_000_000), overview.payments.first().amount)
        val status = overview.status as GivingStatus.Zakat
        assertIs<HaulStatus.Running>(status.haul)
        assertEquals(genap, status.haul.start)
    }

    @Test
    fun `menunaikan zakat sebelum haul genap tetap diperbolehkan`() {
        val (f, e) = env()
        runSuspend { e.service.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        val awal = LocalDate.of(2026, 1, 10)
        runSuspend { e.service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }

        val hasil = runSuspend { e.service.payZakat(e.room, f.account.id, rupiah(1_000_000), awal.plusMonths(2)) }

        assertIs<LedgerResult.Success<*>>(hasil)
    }

    @Test
    fun `menunaikan zakat gagal bila ruang tidak ada atau tidak punya kategori Zakat mal`() {
        val (f, e) = env()
        val diri = f.room("Diri").id

        assertEquals(
            LedgerError.ZAKAT_CATEGORY_MISSING,
            runSuspend { e.service.payZakat(diri, f.account.id, rupiah(1_000), f.today) }.error(),
        )
        assertEquals(
            LedgerError.ROOM_NOT_FOUND,
            runSuspend { e.service.payZakat(RoomId("hilang"), f.account.id, rupiah(1_000), f.today) }.error(),
        )
    }
}
