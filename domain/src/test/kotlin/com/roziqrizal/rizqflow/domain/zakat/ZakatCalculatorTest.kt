package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZakatCalculatorTest {

    @Test
    fun `nisab bawaan adalah 85 gram emas`() {
        // Contoh S16: 85 g x Rp 1.660.000 per gram.
        assertEquals(Money.rupiah(141_100_000), ZakatCalculator.nisab(Money.rupiah(1_660_000)))
    }

    @Test
    fun `asumsi bawaan sesuai keputusan awal yang belum diverifikasi`() {
        val asumsi = ZakatAssumptions()

        assertEquals(85_000L, asumsi.nisabGoldMilligrams)
        assertEquals(250, asumsi.rate.value)
        assertEquals(HaulBreakPolicy.RESET_WHEN_BELOW_NISAB, asumsi.haulBreakPolicy)
    }

    @Test
    fun `nilai emas pecahan gram dibulatkan setengah ke atas`() {
        // 500 mg x Rp 1.001 = 500,5 -> 501.
        assertEquals(Money.rupiah(501), ZakatCalculator.goldValue(Money.rupiah(1_001), 500))
        // 60,5 g x Rp 1.660.000 = 100.430.000 persis.
        assertEquals(Money.rupiah(100_430_000), ZakatCalculator.goldValue(Money.rupiah(1_660_000), 60_500))
        // 499 mg x Rp 1.001 = 499,499 -> 499.
        assertEquals(Money.rupiah(499), ZakatCalculator.goldValue(Money.rupiah(1_001), 499))
    }

    @Test
    fun `emas nol gram bernilai nol`() {
        assertEquals(Money.zero(), ZakatCalculator.goldValue(Money.rupiah(1_660_000), 0))
    }

    @Test
    fun `harga atau berat negatif ditolak`() {
        assertFailsWith<IllegalArgumentException> { ZakatCalculator.goldValue(Money.rupiah(-1), 1_000) }
        assertFailsWith<IllegalArgumentException> { ZakatCalculator.goldValue(Money.rupiah(1_000), -1) }
    }

    @Test
    fun `harta bersih adalah harta dikurangi pengurang`() {
        // Contoh rincian S16.
        val bersih = ZakatCalculator.netWealth(
            assets = listOf(Money.rupiah(99_600_000), Money.rupiah(38_000_000), Money.rupiah(25_000_000), Money.rupiah(4_000_000)),
            deductions = listOf(Money.rupiah(14_600_000)),
        )

        assertEquals(Money.rupiah(152_000_000), bersih)
    }

    @Test
    fun `harta bersih boleh negatif bila utang lebih besar`() {
        val bersih = ZakatCalculator.netWealth(listOf(Money.rupiah(1_000_000)), listOf(Money.rupiah(3_000_000)))

        assertTrue(bersih.isNegative)
    }

    @Test
    fun `tanpa harta dan pengurang hasilnya nol`() {
        assertEquals(Money.zero(), ZakatCalculator.netWealth(emptyList(), emptyList()))
    }

    @Test
    fun `zakat dua setengah persen dari harta bersih`() {
        assertEquals(Money.rupiah(3_800_000), ZakatCalculator.zakatDue(Money.rupiah(152_000_000)))
    }

    @Test
    fun `zakat dibulatkan setengah ke atas ke rupiah`() {
        // 2,5% x Rp 100 = 2,5 -> 3; 2,5% x Rp 90 = 2,25 -> 2.
        assertEquals(Money.rupiah(3), ZakatCalculator.zakatDue(Money.rupiah(100)))
        assertEquals(Money.rupiah(2), ZakatCalculator.zakatDue(Money.rupiah(90)))
    }

    @Test
    fun `harta bersih nol atau negatif tidak menimbulkan zakat`() {
        assertEquals(Money.zero(), ZakatCalculator.zakatDue(Money.zero()))
        assertEquals(Money.zero(), ZakatCalculator.zakatDue(Money.rupiah(-5_000_000)))
    }

    @Test
    fun `tarif dan nisab bisa diganti lewat asumsi`() {
        val asumsi = ZakatAssumptions(nisabGoldMilligrams = 20_000, rate = BasisPoints(500))

        assertEquals(Money.rupiah(20_000_000), ZakatCalculator.nisab(Money.rupiah(1_000_000), asumsi))
        assertEquals(Money.rupiah(500_000), ZakatCalculator.zakatDue(Money.rupiah(10_000_000), asumsi))
    }
}
