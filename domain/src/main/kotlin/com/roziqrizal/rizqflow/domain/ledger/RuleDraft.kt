package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationEngine
import com.roziqrizal.rizqflow.domain.allocation.AllocationResult
import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Money

/**
 * Isian layar Aturan alokasi (S12) yang belum disimpan: persentase (basis point) tiap ruang aktif,
 * berurutan menurut prioritas. Berbeda dengan onboarding (S03), **semua ruang bebas diubah** dan
 * totalnya boleh sementara bukan 100%; tombol simpan baru aktif bila total tepat 100% dan ada
 * perubahan ([isBalanced], [hasChanges]). Persentase pecahan yang tidak disentuh tetap utuh.
 */
data class RuleDraft(val roomIds: List<RoomId>, val shares: List<Int>) {
    init {
        require(roomIds.size == shares.size) { "Jumlah ruang dan persentase harus sama" }
    }

    val totalBp: Int get() = shares.sum()

    val isBalanced: Boolean get() = roomIds.isNotEmpty() && totalBp == BasisPoints.FULL

    /** Positif: kurang dari 100% (masih tersisa). Negatif: melebihi 100%. */
    val remainingBp: Int get() = BasisPoints.FULL - totalBp

    fun set(index: Int, basisPoints: Int): RuleDraft {
        require(index in shares.indices) { "Ruang $index tidak ada" }
        return copy(shares = shares.toMutableList().also { it[index] = basisPoints.coerceIn(0, BasisPoints.FULL) })
    }

    /** Tombol kurang/tambah: [deltaPercent] persen bulat (100 basis point per persen). */
    fun step(index: Int, deltaPercent: Int): RuleDraft = set(index, shares[index] + deltaPercent * 100)

    fun hasChanges(original: RuleDraft): Boolean = roomIds != original.roomIds || shares != original.shares

    fun toRules(): List<AllocationRule> = roomIds.zip(shares) { room, bp -> AllocationRule(room, BasisPoints(bp)) }

    /** Ringkasan contoh (S12). Null bila total melebihi 100%, karena pembagian seperti itu tidak sah. */
    fun sample(amount: Money): AllocationResult? =
        if (roomIds.isEmpty() || totalBp > BasisPoints.FULL) null else AllocationEngine.allocate(amount, toRules())

    companion object {
        /** [rooms]: ruang aktif berurutan; ruang tanpa aturan dianggap 0%. */
        fun from(rooms: List<Room>, rules: List<AllocationRule>): RuleDraft {
            val byRoom = rules.associate { it.roomId to it.share.value }
            return RuleDraft(rooms.map { it.id }, rooms.map { byRoom[it.id] ?: 0 })
        }
    }
}
