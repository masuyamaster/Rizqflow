package com.roziqrizal.rizqflow.domain.calendar

import java.time.DateTimeException
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/**
 * Kalender Hijriyah Umm al-Qura (diputuskan 2026-09-21), memakai [HijrahChronology] bawaan
 * Java yang juga tersedia di Android sejak API 26. Berbasis tabel, jadi hasilnya pasti,
 * bisa dihitung tanpa internet, dan mudah diuji. Bisa berbeda satu hari dari penetapan
 * Kemenag Indonesia, karena itu tampilan tanggal Hijriyah diberi keterangan perkiraan.
 * Cakupan tabel: 1300 sampai 1600 H (sekitar 1882 sampai 2174 M).
 */
class UmmAlQuraCalendar : HijriCalendar {

    override fun toHijri(date: LocalDate): HijriDate {
        val hijrah = hijrah(date)
        return HijriDate(
            year = hijrah.get(ChronoField.YEAR),
            month = hijrah.get(ChronoField.MONTH_OF_YEAR),
            day = hijrah.get(ChronoField.DAY_OF_MONTH),
        )
    }

    override fun toGregorian(date: HijriDate): LocalDate = try {
        LocalDate.from(HijrahChronology.INSTANCE.date(date.year, date.month, date.day))
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("Tanggal Hijriyah tidak ada di kalender Umm al-Qura: ${date.format()}", e)
    }

    override fun plusYears(date: LocalDate, years: Long): LocalDate =
        LocalDate.from(hijrah(date).plus(years, ChronoUnit.YEARS))

    private fun hijrah(date: LocalDate): HijrahDate = try {
        HijrahChronology.INSTANCE.date(date)
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("Tanggal di luar cakupan kalender Umm al-Qura: $date", e)
    }
}
