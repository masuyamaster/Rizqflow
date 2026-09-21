package com.roziqrizal.rizqflow.ui

import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/** Rupiah dengan pemisah ribuan titik dan spasi tak terputus setelah "Rp", misalnya `Rp 1.234.567`. */
fun formatRupiah(rupiah: Long): String {
    val digits = rupiah.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return (if (rupiah < 0) "-" else "") + "Rp " + grouped
}

fun formatRupiah(money: Money): String = formatRupiah(money.minor)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

/** Tanggal untuk kolom Catat: "Hari ini", "Kemarin", atau "21 Sep 2026". */
fun formatDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hari ini"
    today.minusDays(1) -> "Kemarin"
    else -> "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"
}

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
