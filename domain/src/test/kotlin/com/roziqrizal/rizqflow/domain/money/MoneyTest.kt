package com.roziqrizal.rizqflow.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `rupiah disimpan sebagai bilangan bulat tanpa desimal`() {
        val uang = Money.rupiah(1_250_000)

        assertEquals(1_250_000L, uang.minor)
        assertEquals(Currency.IDR, uang.currency)
        assertEquals(0, uang.currency.minorUnitExponent)
    }

    @Test
    fun `penjumlahan dan pengurangan bersifat eksak`() {
        val gaji = Money.rupiah(8_500_000)
        val sedekah = Money.rupiah(850_000)

        assertEquals(Money.rupiah(9_350_000), gaji + sedekah)
        assertEquals(Money.rupiah(7_650_000), gaji - sedekah)
    }

    @Test
    fun `pengurangan boleh menghasilkan nilai negatif`() {
        val selisih = Money.rupiah(100_000) - Money.rupiah(150_000)

        assertEquals(Money.rupiah(-50_000), selisih)
        assertTrue(selisih.isNegative)
    }

    @Test
    fun `tanda nol negatif dan positif`() {
        assertTrue(Money.zero().isZero)
        assertFalse(Money.zero().isNegative)
        assertFalse(Money.zero().isPositive)
        assertTrue(Money.rupiah(1).isPositive)
        assertTrue(Money.rupiah(-1).isNegative)
    }

    @Test
    fun `negasi dan nilai mutlak`() {
        assertEquals(Money.rupiah(-47_000), -Money.rupiah(47_000))
        assertEquals(Money.rupiah(47_000), Money.rupiah(-47_000).abs())
        assertEquals(Money.rupiah(47_000), Money.rupiah(47_000).abs())
    }

    @Test
    fun `perkalian dengan bilangan bulat`() {
        assertEquals(Money.rupiah(45_000), Money.rupiah(15_000) * 3)
    }

    @Test
    fun `luapan Long melempar ArithmeticException, tidak membungkus diam-diam`() {
        assertFailsWith<ArithmeticException> { Money.rupiah(Long.MAX_VALUE) + Money.rupiah(1) }
        assertFailsWith<ArithmeticException> { Money.rupiah(Long.MIN_VALUE) - Money.rupiah(1) }
        assertFailsWith<ArithmeticException> { Money.rupiah(Long.MAX_VALUE) * 2 }
        assertFailsWith<ArithmeticException> { -Money.rupiah(Long.MIN_VALUE) }
    }

    @Test
    fun `mata uang berbeda tidak boleh dicampur`() {
        assertFailsWith<IllegalArgumentException> { Money(1, Currency.IDR) + Money(1, Currency.USD) }
        assertFailsWith<IllegalArgumentException> { Money(1, Currency.IDR).compareTo(Money(1, Currency.USD)) }
    }

    @Test
    fun `perbandingan mengikuti nominal`() {
        assertTrue(Money.rupiah(100) < Money.rupiah(200))
        assertTrue(Money.rupiah(200) > Money.rupiah(100))
        assertEquals(0, Money.rupiah(100).compareTo(Money.rupiah(100)))
    }

    @Test
    fun `sepuluh kali sepuluh sen tepat satu dolar tanpa selisih pembulatan`() {
        val sepuluhKali = List(10) { Money(10, Currency.USD) }

        assertEquals(Money(100, Currency.USD), sepuluhKali.sum(Currency.USD))
    }

    @Test
    fun `jumlah daftar kosong adalah nol pada mata uang yang diminta`() {
        assertEquals(Money.zero(), emptyList<Money>().sum())
        assertEquals(Money.zero(Currency.USD), emptyList<Money>().sum(Currency.USD))
    }
}
