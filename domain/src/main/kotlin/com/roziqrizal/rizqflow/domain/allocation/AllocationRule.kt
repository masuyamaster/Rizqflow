package com.roziqrizal.rizqflow.domain.allocation

import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Money

/**
 * Persentase sebagai bilangan bulat basis point: 1% = 100, 100% = 10.000. Tidak ada floating
 * point, dan persentase pecahan seperti 2,5% (= 250) tetap eksak.
 */
@JvmInline
value class BasisPoints(val value: Int) {
    init {
        require(value in 0..FULL) { "Basis point harus 0 sampai $FULL, bukan $value" }
    }

    companion object {
        const val FULL = 10_000

        /** Persentase bulat, misalnya `percent(10)` untuk 10%. */
        fun percent(percent: Int): BasisPoints = BasisPoints(Math.multiplyExact(percent, 100))
    }
}

/**
 * Satu baris aturan alokasi: ruang [roomId] menerima [share] dari setiap rezeki yang masuk.
 * Urutan aturan dalam daftar adalah urutan prioritas ruang, yang menjadi pemutus seri saat
 * sisa pembulatan dibagikan (lihat [AllocationEngine]).
 */
data class AllocationRule(val roomId: RoomId, val share: BasisPoints)

/** Bagian rezeki yang jatuh ke satu ruang. */
data class AllocationShare(val roomId: RoomId, val amount: Money)

/**
 * Mode aturan alokasi, berlaku untuk seluruh ruleset sekaligus (docs/monetisasi.md, "Aturan alokasi
 * lanjutan"). [PERCENTAGE] adalah dasar (gratis); [WATERFALL] adalah lanjutan (Pro).
 */
enum class AllocationMode { PERCENTAGE, WATERFALL }

/**
 * Satu baris aturan lanjutan (Pro): ruang [roomId] diisi berurutan menurut prioritas sampai
 * [capAmount], lalu sisanya mengalir ke ruang berikutnya di urutan itu. [capAmount] null berarti
 * tak terbatas: ruang ini menampung seluruh sisa yang tersisa (cocok untuk ruang prioritas
 * terakhir, supaya sisa tidak selalu jatuh ke "belum dialirkan"). Urutan daftar adalah urutan
 * prioritas ruang, sama seperti [AllocationRule] (lihat [AllocationEngine.allocateWaterfall]).
 */
data class AllocationCap(val roomId: RoomId, val capAmount: Money?)
