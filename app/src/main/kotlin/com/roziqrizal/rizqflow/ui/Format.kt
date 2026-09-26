package com.roziqrizal.rizqflow.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth

/** Rupiah dengan pemisah ribuan titik dan spasi tak terputus setelah "Rp", misalnya `Rp 1.234.567`. */
fun formatRupiah(rupiah: Long): String {
    val digits = rupiah.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return (if (rupiah < 0) "-" else "") + "Rp " + grouped
}

fun formatRupiah(money: Money): String = formatRupiah(money.minor)

/**
 * Inti murni tanpa resource Android, supaya bisa diuji lewat kotlin.test biasa tanpa Robolectric.
 * [formatDate] di bawah adalah pembungkus Composable yang mengisi [todayLabel]/[yesterdayLabel]/
 * [monthNames] dari string resource sesuai bahasa tampilan.
 */
fun formatDateWith(date: LocalDate, today: LocalDate, todayLabel: String, yesterdayLabel: String, monthNames: List<String>): String = when (date) {
    today -> todayLabel
    today.minusDays(1) -> yesterdayLabel
    else -> "${date.dayOfMonth} ${monthNames[date.monthValue - 1]} ${date.year}"
}

/** Tanggal untuk kolom Catat: "Hari ini", "Kemarin", atau "21 Sep 2026" (nama bulan dan label mengikuti bahasa tampilan). */
@Composable
fun formatDate(date: LocalDate, today: LocalDate): String = formatDateWith(
    date,
    today,
    todayLabel = stringResource(R.string.date_today),
    yesterdayLabel = stringResource(R.string.date_yesterday),
    monthNames = stringArrayResource(R.array.month_abbrev).toList(),
)

/** Inti murni; lihat catatan di [formatDateWith]. */
fun formatMonthWith(month: YearMonth, monthNames: List<String>): String = "${monthNames[month.monthValue - 1]} ${month.year}"

/** Bulan untuk navigator S08: "Sep 2026". */
@Composable
fun formatMonth(month: YearMonth): String = formatMonthWith(month, stringArrayResource(R.array.month_abbrev).toList())

/** Persen dari basis point: "10%" untuk kelipatan 1%, dan "2,5%" untuk pecahan. */
fun formatPercent(basisPoints: Int): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return when {
        fraction == 0 -> "$whole%"
        fraction % 10 == 0 -> "$whole,${fraction / 10}%"
        else -> "$whole,${fraction.toString().padStart(2, '0')}%"
    }
}
