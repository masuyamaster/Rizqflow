package com.roziqrizal.rizqflow.domain.money

/**
 * Mata uang beserta jumlah digit desimal pada satuan terkecilnya ([minorUnitExponent]).
 * Rupiah tidak memakai desimal, jadi satuan terkecilnya satu rupiah. USD ikut ada supaya
 * dukungan multi-mata uang (Pro) tidak perlu mengubah bentuk [Money].
 */
enum class Currency(val code: String, val minorUnitExponent: Int) {
    IDR("IDR", 0),
    USD("USD", 2),
}

/**
 * Nominal uang: bilangan bulat dalam satuan terkecil mata uangnya, tidak pernah floating point.
 *
 * Aritmetika bersifat eksak. Luapan `Long` melempar [ArithmeticException] alih-alih diam-diam
 * membungkus, dan mencampur mata uang berbeda melempar [IllegalArgumentException].
 * Aturan pembulatan pembagian alokasi sengaja tidak ada di sini; itu tugas terpisah.
 */
data class Money(val minor: Long, val currency: Currency = Currency.IDR) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0L
    val isPositive: Boolean get() = minor > 0L

    operator fun plus(other: Money): Money =
        Money(Math.addExact(minor, sameCurrency(other).minor), currency)

    operator fun minus(other: Money): Money =
        Money(Math.subtractExact(minor, sameCurrency(other).minor), currency)

    operator fun unaryMinus(): Money = Money(Math.negateExact(minor), currency)

    operator fun times(factor: Long): Money = Money(Math.multiplyExact(minor, factor), currency)

    fun abs(): Money = if (isNegative) -this else this

    override fun compareTo(other: Money): Int = minor.compareTo(sameCurrency(other).minor)

    private fun sameCurrency(other: Money): Money {
        require(other.currency == currency) {
            "Mata uang berbeda tidak bisa dicampur: ${currency.code} dan ${other.currency.code}"
        }
        return other
    }

    companion object {
        fun rupiah(amount: Long): Money = Money(amount, Currency.IDR)

        fun zero(currency: Currency = Currency.IDR): Money = Money(0L, currency)
    }
}

/** Menjumlahkan sekumpulan [Money]; daftar kosong menghasilkan nol pada [currency]. */
fun Iterable<Money>.sum(currency: Currency = Currency.IDR): Money =
    fold(Money.zero(currency)) { total, next -> total + next }
