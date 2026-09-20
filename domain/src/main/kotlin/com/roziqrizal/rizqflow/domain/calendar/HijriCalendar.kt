package com.roziqrizal.rizqflow.domain.calendar

import java.time.LocalDate

/** Tanggal Hijriyah. Bulan 1 (Muharram) sampai 12 (Dzulhijjah), hari 1 sampai 30. */
data class HijriDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12) { "Bulan Hijriyah harus 1 sampai 12, bukan $month" }
        require(day in 1..30) { "Hari Hijriyah harus 1 sampai 30, bukan $day" }
    }

    /** Contoh: `8 Dzulhijjah 1447`. */
    fun format(): String = "$day ${MONTH_NAMES[month - 1]} $year"

    companion object {
        private val MONTH_NAMES = listOf(
            "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir", "Jumadil Awal", "Jumadil Akhir",
            "Rajab", "Syaban", "Ramadhan", "Syawal", "Dzulqa'dah", "Dzulhijjah",
        )
    }
}

/**
 * Konversi tanggal Masehi dan Hijriyah. Berupa antarmuka supaya metode kalender bisa diganti
 * (Umm al-Qura sekarang; kriteria Kemenag atau hisab Al-Kaukaba di kemudian hari) tanpa
 * mengubah kalkulator haul.
 */
interface HijriCalendar {
    fun toHijri(date: LocalDate): HijriDate

    /** Kebalikan [toHijri]. Melempar [IllegalArgumentException] bila tanggalnya tidak ada. */
    fun toGregorian(date: HijriDate): LocalDate

    /**
     * Tanggal yang sama [years] tahun Hijriyah kemudian. Bila hari itu tidak ada di bulan
     * tujuan (bulan berumur 29 hari), hari dipangkas ke hari terakhir bulan itu.
     */
    fun plusYears(date: LocalDate, years: Long = 1): LocalDate
}
