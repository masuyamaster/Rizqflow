package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.model.GoldPriceSource
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.WealthKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/**
 * Model modul zakat (S14 sampai S17, docs/model-data.md "Modul zakat"). Satu profil gratis;
 * profil tambahan (mis. istri, usaha) adalah Pro. Tiap profil punya harta, haul, dan riwayat zakatnya sendiri. Status haul **tidak disimpan**: [HaulTracker] menghitungnya ulang
 * dari [WealthCheck] dan [ZakatPayment], persis seperti riwayat transaksi menentukan saldo akun.
 */
data class ZakatProfile(
    val id: String,
    val name: String,
    val haulBreakPolicy: HaulBreakPolicy = HaulBreakPolicy.RESET_WHEN_BELOW_NISAB,
    val archived: Boolean = false,
) {
    init {
        require(name.isNotBlank() && name.length <= NAME_MAX) { "Nama profil harta 1 sampai $NAME_MAX karakter" }
    }

    companion object {
        const val NAME_MAX = 30
    }
}

/**
 * Satu baris harta atau pengurang. [value] selalu nilai rupiah saat disimpan (untuk emas, nilai
 * saat itu dihitung dari [goldMilligrams] dan harga emas hari itu); [kind] `DEDUCTION` mengurangi
 * harta bersih, kind lain menambahnya.
 */
data class WealthItem(
    val id: String,
    val profileId: String,
    val kind: WealthKind,
    val label: String,
    val value: Money,
    val goldMilligrams: Long? = null,
    val updatedAtMillis: Long,
) {
    init {
        require(label.isNotBlank() && label.length <= LABEL_MAX) { "Label harta 1 sampai $LABEL_MAX karakter" }
        require(!value.isNegative) { "Nilai harta tidak boleh negatif" }
        require(goldMilligrams == null || goldMilligrams >= 0) { "Berat emas tidak boleh negatif" }
    }

    companion object {
        const val LABEL_MAX = 40
    }
}

/** Harga emas pada satu hari. Riwayat disimpan supaya nisab hari-hari lama bisa direkonstruksi. */
data class GoldPrice(val day: LocalDate, val perGram: Money, val source: GoldPriceSource)

/** Setara [com.roziqrizal.rizqflow.domain.zakat.HaulEvent.WealthChecked] yang tersimpan. */
data class WealthCheck(val id: String, val profileId: String, val day: LocalDate, val netWealth: Money, val nisab: Money)

/** Setara [com.roziqrizal.rizqflow.domain.zakat.HaulEvent.ZakatPaid] yang tersimpan. */
data class ZakatPayment(val id: String, val profileId: String, val day: LocalDate, val transactionId: TransactionId)

interface ZakatRepository {
    /** Profil yang belum diarsipkan, berurutan menurut pembuatannya; kosong bila modul belum pernah diisi. */
    suspend fun profiles(): List<ZakatProfile>

    /** Profil menurut pengenal, termasuk yang terarsip; null bila tidak ada. */
    suspend fun findProfile(id: String): ZakatProfile?

    suspend fun saveProfile(profile: ZakatProfile)

    suspend fun items(profileId: String): List<WealthItem>

    /** Mengganti seluruh baris harta profil ini sekaligus (atomik): S15 selalu menyimpan daftar penuh. */
    suspend fun replaceItems(profileId: String, items: List<WealthItem>)

    suspend fun latestGoldPrice(): GoldPrice?

    suspend fun saveGoldPrice(price: GoldPrice)

    suspend fun checks(profileId: String): List<WealthCheck>

    /** Menyimpan pemeriksaan harta hari ini; menimpa baris hari yang sama bila sudah ada (satu per hari). */
    suspend fun saveCheck(check: WealthCheck)

    suspend fun payments(profileId: String): List<ZakatPayment>

    suspend fun savePayment(payment: ZakatPayment)
}
