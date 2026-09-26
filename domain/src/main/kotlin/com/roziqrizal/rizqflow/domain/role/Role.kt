package com.roziqrizal.rizqflow.domain.role

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Money
import java.math.BigInteger

/*
 * Sistem per peran (Pro, docs/monetisasi.md): modul opsional yang dipasang pada satu ruang, mirip
 * modul Memberi. Sebuah ruang punya paling banyak satu peran. Sengaja ringan: tidak ada harga aset
 * atau portofolio (docs/konsep.md, "Sengaja tidak masuk"); modul hanya menjaga disiplin di atas
 * transaksi biasa, jadi semua uangnya tetap pengeluaran di ruang itu.
 */

/** Peran yang bisa dipasang pada sebuah ruang. */
enum class RoleKind { TRADER, INVESTOR }

/**
 * Trader: batas risiko per trade. [capital] adalah modal trading yang diisi pengguna (bukan saldo
 * yang dihitung aplikasi), [riskPerTrade] persentase modal yang boleh hilang dalam satu trade.
 * Kerugian dicatat sebagai pengeluaran biasa di ruang itu; pengeluaran di atas [maxRiskPerTrade]
 * memunculkan peringatan lembut di Catat, tidak pernah menghalangi menyimpan.
 */
data class TraderProfile(val roomId: RoomId, val capital: Money, val riskPerTrade: BasisPoints) {
    init {
        require(capital.isPositive) { "Modal harus lebih dari nol" }
        require(riskPerTrade.value in 1..MAX_RISK_BP) { "Risiko per trade 0,01% sampai 50%" }
    }

    /** Modal dikali persentase, dibulatkan ke bawah; eksak tanpa floating point. */
    val maxRiskPerTrade: Money
        get() = Money(
            BigInteger.valueOf(capital.minor).multiply(BigInteger.valueOf(riskPerTrade.value.toLong()))
                .divide(BigInteger.valueOf(BasisPoints.FULL.toLong())).longValueExact(),
            capital.currency,
        )

    companion object {
        const val MAX_RISK_BP = 5_000
    }
}

/**
 * Investor: jadwal DCA. Tiap bulan pada tanggal [dayOfMonth], [amount] diinvestasikan dari akun
 * [accountId]. Sesuai keputusan di docs/konsep.md ("Investasi dicatat sebagai pengeluaran di ruang"),
 * mencatatnya berarti satu pengeluaran di kategori [categoryId] milik ruang itu, sehingga jatah
 * ruang ikut terpakai. Tanggal dibatasi 1 sampai 28 supaya ada di setiap bulan.
 */
data class DcaPlan(
    val roomId: RoomId,
    val amount: Money,
    val dayOfMonth: Int,
    val accountId: AccountId,
    val categoryId: CategoryId,
) {
    init {
        require(amount.isPositive) { "Nominal DCA harus lebih dari nol" }
        require(dayOfMonth in 1..MAX_DAY) { "Tanggal DCA 1 sampai $MAX_DAY" }
    }

    companion object {
        const val MAX_DAY = 28
    }
}

/** Penyimpanan modul peran. Satu ruang hanya boleh punya satu baris di antara [trader] dan [dca]. */
interface RoleRepository {
    suspend fun trader(roomId: RoomId): TraderProfile?

    suspend fun saveTrader(profile: TraderProfile)

    suspend fun dca(roomId: RoomId): DcaPlan?

    suspend fun saveDca(plan: DcaPlan)

    /** Semua jadwal DCA, dasar pengingat harian. */
    suspend fun allDca(): List<DcaPlan>

    /** Melepas modul peran dari ruang (apa pun jenisnya). Transaksi yang sudah dicatat tidak tersentuh. */
    suspend fun remove(roomId: RoomId)
}
