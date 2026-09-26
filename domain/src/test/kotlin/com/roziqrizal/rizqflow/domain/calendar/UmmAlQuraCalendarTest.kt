package com.roziqrizal.rizqflow.domain.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UmmAlQuraCalendarTest {

    private val kalender = UmmAlQuraCalendar()

    @Test
    fun `awal Ramadan 1446 jatuh pada 1 Maret 2025`() {
        assertEquals(LocalDate.of(2025, 3, 1), kalender.toGregorian(HijriDate(1446, 9, 1)))
        assertEquals(HijriDate(1446, 9, 1), kalender.toHijri(LocalDate.of(2025, 3, 1)))
    }

    @Test
    fun `awal Muharram 1447 jatuh pada 26 Juni 2025`() {
        assertEquals(LocalDate.of(2025, 6, 26), kalender.toGregorian(HijriDate(1447, 1, 1)))
    }

    @Test
    fun `konversi bolak-balik konsisten untuk tiga tahun berturut-turut`() {
        var tanggal = LocalDate.of(2025, 1, 1)
        repeat(1_100) {
            assertEquals(tanggal, kalender.toGregorian(kalender.toHijri(tanggal)))
            tanggal = tanggal.plusDays(1)
        }
    }

    @Test
    fun `satu tahun Hijriyah lebih pendek dari tahun Masehi`() {
        val awal = LocalDate.of(2026, 1, 10)
        val hariIni = kalender.toHijri(awal)

        val setahun = kalender.plusYears(awal)
        val hariBerikut = kalender.toHijri(setahun)

        assertEquals(HijriDate(hariIni.year + 1, hariIni.month, hariIni.day), hariBerikut)
        assertTrue(ChronoUnit.DAYS.between(awal, setahun) in 354L..355L)
    }

    @Test
    fun `hari ke-30 dipangkas bila bulan tujuan hanya 29 hari`() {
        // Cari tanggal 30 yang tahun depannya jatuh di bulan berumur 29 hari.
        var tanggal = LocalDate.of(2025, 1, 1)
        var dipangkas: Pair<LocalDate, LocalDate>? = null
        while (dipangkas == null && tanggal.isBefore(LocalDate.of(2035, 1, 1))) {
            val hijri = kalender.toHijri(tanggal)
            if (hijri.day == 30) {
                val tahunDepan = kalender.plusYears(tanggal)
                if (kalender.toHijri(tahunDepan).day == 29) dipangkas = tanggal to tahunDepan
            }
            tanggal = tanggal.plusDays(1)
        }

        val (asal, tujuan) = requireNotNull(dipangkas) { "Tidak ada contoh pemangkasan dalam sepuluh tahun" }
        val hijriAsal = kalender.toHijri(asal)
        val hijriTujuan = kalender.toHijri(tujuan)
        assertEquals(hijriAsal.month, hijriTujuan.month)
        assertEquals(hijriAsal.year + 1, hijriTujuan.year)
        assertEquals(29, hijriTujuan.day)
        // Sehari sesudahnya sudah awal bulan berikutnya, jadi 29 memang hari terakhir bulan itu.
        assertEquals(1, kalender.toHijri(tujuan.plusDays(1)).day)
    }

    @Test
    fun `tanggal Hijriyah yang tidak ada ditolak`() {
        assertFailsWith<IllegalArgumentException> { kalender.toGregorian(HijriDate(1000, 1, 1)) }
    }

    @Test
    fun `tanggal Masehi di luar cakupan tabel ditolak`() {
        assertFailsWith<IllegalArgumentException> { kalender.toHijri(LocalDate.of(1800, 1, 1)) }
    }

    @Test
    fun `HijriDate memeriksa bulan dan hari`() {
        assertFailsWith<IllegalArgumentException> { HijriDate(1447, 13, 1) }
        assertFailsWith<IllegalArgumentException> { HijriDate(1447, 1, 31) }
        assertFailsWith<IllegalArgumentException> { HijriDate(1447, 1, 0) }
    }

    @Test
    fun `format memakai nama bulan bahasa Indonesia`() {
        assertEquals("8 Dzulhijjah 1447", HijriDate(1447, 12, 8).format())
        assertEquals("1 Muharram 1448", HijriDate(1448, 1, 1).format())
        assertEquals("15 Rabiul Akhir 1448", HijriDate(1448, 4, 15).format())
    }
}
