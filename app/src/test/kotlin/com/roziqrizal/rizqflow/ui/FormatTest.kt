package com.roziqrizal.rizqflow.ui

import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
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

    @Test
    fun `tanggal hari ini dan kemarin disebut dengan katanya`() {
        val today = LocalDate.of(2026, 9, 21)
        assertEquals("Hari ini", formatDate(today, today))
        assertEquals("Kemarin", formatDate(today.minusDays(1), today))
    }

    @Test
    fun `tanggal lain memakai singkatan bulan Indonesia`() {
        val today = LocalDate.of(2026, 9, 21)
        assertEquals("19 Sep 2026", formatDate(LocalDate.of(2026, 9, 19), today))
        assertEquals("1 Jan 2026", formatDate(LocalDate.of(2026, 1, 1), today))
        assertEquals("31 Des 2025", formatDate(LocalDate.of(2025, 12, 31), today))
        assertEquals("15 Mei 2026", formatDate(LocalDate.of(2026, 5, 15), today))
        assertEquals("30 Agu 2026", formatDate(LocalDate.of(2026, 8, 30), today))
    }

    @Test
    fun `kemarin dikenali juga saat melewati batas bulan dan tahun`() {
        assertEquals("Kemarin", formatDate(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)))
        assertEquals("Kemarin", formatDate(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `persen bulat tanpa desimal dan pecahan memakai koma`() {
        assertEquals("0%", formatPercent(0))
        assertEquals("10%", formatPercent(1_000))
        assertEquals("100%", formatPercent(10_000))
        assertEquals("2,5%", formatPercent(250))
        assertEquals("27,5%", formatPercent(2_750))
        assertEquals("12,34%", formatPercent(1_234))
        assertEquals("0,05%", formatPercent(5))
    }
}
