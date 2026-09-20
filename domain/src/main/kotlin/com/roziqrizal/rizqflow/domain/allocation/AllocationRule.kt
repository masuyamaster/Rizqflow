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
