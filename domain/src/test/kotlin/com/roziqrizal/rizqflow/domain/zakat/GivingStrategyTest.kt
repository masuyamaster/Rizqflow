package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.calendar.UmmAlQuraCalendar
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GivingStrategyTest {

    private val kalender = UmmAlQuraCalendar()
    private val awal = LocalDate.of(2026, 1, 10)
    private val harga = Money.rupiah(1_660_000)
    private val nisab = Money.rupiah(141_100_000)
    private val bersih = Money.rupiah(152_000_000)

    private fun konteks(
        hariIni: LocalDate,
        hargaEmas: Money? = harga,
        harta: Money? = bersih,
        kejadian: List<HaulEvent> = listOf(HaulEvent.WealthChecked(awal, bersih, nisab)),
    ) = GivingContext(hariIni, Money.rupiah(8_500_000), hargaEmas, harta, kejadian)

    private val zakat = ZakatHaulHijriStrategy(HaulTracker(kalender))

    @Test
    fun `pengenal strategi sesuai nama modul`() {
        assertEquals("zakat-haul-hijri", zakat.id)
        assertEquals("percentage", PercentageGivingStrategy(BasisPoints.percent(5)).id)
    }

    @Test
    fun `mode persentase menghitung target dari rezeki bulan ini`() {
        val status = PercentageGivingStrategy(BasisPoints.percent(10)).evaluate(konteks(awal))

        assertEquals(GivingStatus.Percentage(Money.rupiah(850_000)), status)
    }

    @Test
    fun `mode persentase dengan rezeki negatif menghasilkan target nol`() {
        val status = PercentageGivingStrategy(BasisPoints.percent(10))
            .evaluate(konteks(awal).copy(monthlyIncome = Money.rupiah(-100)))

        assertEquals(GivingStatus.Percentage(Money.zero()), status)
    }

    @Test
    fun `modul zakat meminta harga emas bila belum diisi`() {
        val status = zakat.evaluate(konteks(awal, hargaEmas = null))

        assertEquals(GivingStatus.NeedsInput(goldPriceMissing = true, wealthMissing = false), status)
    }

    @Test
    fun `modul zakat meminta profil harta bila belum diisi`() {
        val status = zakat.evaluate(konteks(awal, harta = null))

        assertEquals(GivingStatus.NeedsInput(goldPriceMissing = false, wealthMissing = true), status)
    }

    @Test
    fun `haul berjalan menampilkan nisab tanpa zakat wajib`() {
        val status = assertIs<GivingStatus.Zakat>(zakat.evaluate(konteks(awal.plusDays(117))))

        assertIs<HaulStatus.Running>(status.haul)
        assertEquals(nisab, status.nisab)
        assertEquals(bersih, status.netWealth)
        assertNull(status.zakatDue)
    }

    @Test
    fun `haul genap menampilkan jumlah zakat`() {
        val due = kalender.plusYears(awal)

        val status = assertIs<GivingStatus.Zakat>(zakat.evaluate(konteks(due)))

        assertIs<HaulStatus.Completed>(status.haul)
        assertEquals(Money.rupiah(3_800_000), status.zakatDue)
    }

    @Test
    fun `belum mencapai nisab tidak menimbulkan zakat`() {
        val kurang = Money.rupiah(100_000_000)
        val status = assertIs<GivingStatus.Zakat>(
            zakat.evaluate(konteks(awal, harta = kurang, kejadian = listOf(HaulEvent.WealthChecked(awal, kurang, nisab)))),
        )

        assertEquals(HaulStatus.BelowNisab, status.haul)
        assertNull(status.zakatDue)
    }
}
