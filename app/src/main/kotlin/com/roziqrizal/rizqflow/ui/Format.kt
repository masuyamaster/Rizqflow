package com.roziqrizal.rizqflow.ui

import com.roziqrizal.rizqflow.domain.money.Money

/** Rupiah dengan pemisah ribuan titik dan spasi tak terputus setelah "Rp", misalnya `Rp 1.234.567`. */
fun formatRupiah(rupiah: Long): String {
    val digits = rupiah.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return (if (rupiah < 0) "-" else "") + "Rp " + grouped
}

fun formatRupiah(money: Money): String = formatRupiah(money.minor)
