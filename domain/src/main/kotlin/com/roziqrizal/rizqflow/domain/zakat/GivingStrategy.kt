package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.money.Money
import java.math.BigInteger
import java.time.LocalDate

/** Bahan yang dibutuhkan strategi untuk menilai ruang Memberi. */
data class GivingContext(
    val today: LocalDate,
    /** Rezeki yang masuk bulan ini; dasar mode persentase donasi. */
    val monthlyIncome: Money,
    /** Harga emas per gram (input manual, atau otomatis untuk Pro); null bila belum diisi. */
    val goldPricePerGram: Money?,
    /** Harta bersih menurut profil harta terakhir; null bila profil belum diisi. */
    val netWealth: Money?,
    val haulEvents: List<HaulEvent>,
)

sealed interface GivingStatus {
    /** Mode persentase donasi: target donasi bulan ini. */
    data class Percentage(val target: Money) : GivingStatus

    /** Modul zakat butuh harga emas dan profil harta sebelum bisa menghitung. */
    data class NeedsInput(val goldPriceMissing: Boolean, val wealthMissing: Boolean) : GivingStatus

    /** Modul zakat: [zakatDue] terisi hanya saat [haul] sudah genap. */
    data class Zakat(val haul: HaulStatus, val nisab: Money, val netWealth: Money, val zakatDue: Money?) : GivingStatus
}

/**
 * Strategi ruang Memberi. Dua implementasi yang bisa ditukar dan dimatikan tanpa merusak
 * aplikasi: [ZakatHaulHijriStrategy] (`zakat-haul-hijri`) dan [PercentageGivingStrategy] (`percentage`).
 */
interface GivingStrategy {
    val id: String

    fun evaluate(context: GivingContext): GivingStatus
}

/** Mode alternatif: donasi sebagai persentase dari rezeki, tanpa nisab dan haul. */
class PercentageGivingStrategy(private val rate: BasisPoints) : GivingStrategy {
    override val id: String = ID

    override fun evaluate(context: GivingContext): GivingStatus {
        val income = context.monthlyIncome
        val target = BigInteger.valueOf(income.minor.coerceAtLeast(0)) * BigInteger.valueOf(rate.value.toLong()) /
            BigInteger.valueOf(BasisPoints.FULL.toLong())
        return GivingStatus.Percentage(Money(target.longValueExact(), income.currency))
    }

    companion object {
        const val ID = "percentage"
    }
}

/** Mode zakat mal: nisab dari harga emas, haul satu tahun Hijriyah, zakat saat haul genap. */
class ZakatHaulHijriStrategy(
    private val tracker: HaulTracker,
    private val assumptions: ZakatAssumptions = ZakatAssumptions(),
) : GivingStrategy {
    override val id: String = ID

    override fun evaluate(context: GivingContext): GivingStatus {
        val price = context.goldPricePerGram
        val wealth = context.netWealth
        if (price == null || wealth == null) {
            return GivingStatus.NeedsInput(goldPriceMissing = price == null, wealthMissing = wealth == null)
        }
        val haul = tracker.status(context.haulEvents, context.today)
        val due = if (haul is HaulStatus.Completed) ZakatCalculator.zakatDue(wealth, assumptions) else null
        return GivingStatus.Zakat(haul, ZakatCalculator.nisab(price, assumptions), wealth, due)
    }

    companion object {
        const val ID = "zakat-haul-hijri"
    }
}
