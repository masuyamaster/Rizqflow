package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.money.Currency
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.money.sum
import java.math.BigInteger

/** Perbedaan pendapat tentang haul ketika harta turun di bawah nisab di tengah tahun. */
enum class HaulBreakPolicy {
    /** Haul terputus dan dimulai lagi dari nol saat harta kembali mencapai nisab. */
    RESET_WHEN_BELOW_NISAB,

    /** Nisab hanya diperiksa di awal dan di akhir haul; turun di tengah tahun diabaikan. */
    CHECK_ONLY_AT_START_AND_END,
}

/**
 * Asumsi fikih zakat mal. Nilai bawaan adalah **asumsi awal (2026-09-20) yang belum
 * diverifikasi** dengan kitab oleh pemilik; karena itu semuanya parameter, bukan konstanta.
 */
data class ZakatAssumptions(
    /** Nisab dalam miligram emas; 85 gram. */
    val nisabGoldMilligrams: Long = 85_000L,
    /** Tarif zakat mal; 2,5%. */
    val rate: BasisPoints = BasisPoints(250),
    val haulBreakPolicy: HaulBreakPolicy = HaulBreakPolicy.RESET_WHEN_BELOW_NISAB,
)

/** Perhitungan nisab dan zakat. Semua uang dalam satuan terkecil, pembulatan setengah ke atas. */
object ZakatCalculator {

    /** Nilai emas seberat [milligrams] pada harga [pricePerGram]. */
    fun goldValue(pricePerGram: Money, milligrams: Long): Money {
        require(milligrams >= 0) { "Berat emas tidak boleh negatif" }
        require(!pricePerGram.isNegative) { "Harga emas tidak boleh negatif" }
        return Money(divideHalfUp(BigInteger.valueOf(pricePerGram.minor) * BigInteger.valueOf(milligrams), MG_PER_GRAM), pricePerGram.currency)
    }

    fun nisab(pricePerGram: Money, assumptions: ZakatAssumptions = ZakatAssumptions()): Money =
        goldValue(pricePerGram, assumptions.nisabGoldMilligrams)

    /** Harta bersih = total harta dikurangi pengurang (utang jangka pendek). Bisa negatif. */
    fun netWealth(assets: List<Money>, deductions: List<Money>, currency: Currency = Currency.IDR): Money =
        assets.sum(currency) - deductions.sum(currency)

    /** Zakat yang wajib ditunaikan dari [netWealth]; nol bila harta bersih tidak positif. */
    fun zakatDue(netWealth: Money, assumptions: ZakatAssumptions = ZakatAssumptions()): Money {
        if (!netWealth.isPositive) return Money.zero(netWealth.currency)
        val numerator = BigInteger.valueOf(netWealth.minor) * BigInteger.valueOf(assumptions.rate.value.toLong())
        return Money(divideHalfUp(numerator, BigInteger.valueOf(BasisPoints.FULL.toLong())), netWealth.currency)
    }

    private val MG_PER_GRAM: BigInteger = BigInteger.valueOf(1_000L)

    private fun divideHalfUp(numerator: BigInteger, denominator: BigInteger): Long {
        val doubled = numerator * BigInteger.TWO + denominator
        return (doubled / (denominator * BigInteger.TWO)).longValueExact()
    }
}
