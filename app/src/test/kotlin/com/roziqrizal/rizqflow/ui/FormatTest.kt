package com.roziqrizal.rizqflow.ui

import com.roziqrizal.rizqflow.domain.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {

    private val nbsp = " "

    @Test
    fun `angka kecil tanpa pemisah`() {
        assertEquals("Rp${nbsp}0", formatRupiah(0))
        assertEquals("Rp${nbsp}7", formatRupiah(7))
        assertEquals("Rp${nbsp}999", formatRupiah(999))
    }

    @Test
    fun `ribuan dipisah titik`() {
        assertEquals("Rp${nbsp}1.000", formatRupiah(1_000))
        assertEquals("Rp${nbsp}12.345", formatRupiah(12_345))
        assertEquals("Rp${nbsp}123.456", formatRupiah(123_456))
        assertEquals("Rp${nbsp}1.234.567", formatRupiah(1_234_567))
        assertEquals("Rp${nbsp}5.000.000", formatRupiah(5_000_000))
    }

    @Test
    fun `nilai negatif memakai tanda minus di depan`() {
        assertEquals("-Rp${nbsp}400.000", formatRupiah(-400_000))
    }

    @Test
    fun `nilai terbesar tetap terformat`() {
        assertEquals("Rp${nbsp}9.223.372.036.854.775.807", formatRupiah(Long.MAX_VALUE))
        assertEquals("-Rp${nbsp}9.223.372.036.854.775.808", formatRupiah(Long.MIN_VALUE))
    }

    @Test
    fun `Money diformat sama dengan angkanya`() {
        assertEquals(formatRupiah(1_234_567), formatRupiah(Money.rupiah(1_234_567)))
    }

    @Test
    fun `spasi tak terputus menjaga Rp dan angka tidak terpisah baris`() {
        assertEquals(true, formatRupiah(1_000).contains(nbsp))
        assertEquals(false, formatRupiah(1_000).contains(" "))
    }
}
