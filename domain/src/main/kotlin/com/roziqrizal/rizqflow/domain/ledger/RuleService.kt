package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.entitlement.Entitlements
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind

data class NewRoom(val name: String, val kind: RoomKind, val iconKey: String, val colorSlot: Int)

/**
 * Mengatur ruang dan aturan alokasi (S10, S12).
 *
 * Aturan yang disimpan harus **tepat 100%** (prototipe F4: total lain tidak bisa disimpan).
 * Menambah ruang dibatasi [Entitlements]: ruang ke-6 ditolak untuk paket gratis, dan layar
 * mengubahnya menjadi paywall S21.
 */
class RuleService(
    private val rooms: RoomRepository,
    private val entitlements: Entitlements,
    private val newId: () -> String,
) {

    suspend fun changeRules(rules: List<AllocationRule>): LedgerResult<Unit> {
        val activeRooms = rooms.activeRooms()
        val active = activeRooms.map { it.id }.toSet()
        if (rules.isEmpty() || rules.map { it.roomId }.toSet().size != rules.size || rules.any { it.roomId !in active }) {
            return failure(LedgerError.RULES_INVALID)
        }
        if (rules.sumOf { it.share.value } != BasisPoints.FULL) return failure(LedgerError.RULES_NOT_100_PERCENT)

        // Urutan aturan mengikuti prioritas ruang, bukan urutan masukan: pemutus seri pembulatan
        // tidak boleh bergantung pada cara layar menyusun daftar.
        val priority = activeRooms.withIndex().associate { (index, room) -> room.id to index }
        rooms.replaceRules(rules.sortedBy { priority.getValue(it.roomId) })
        return LedgerResult.Success(Unit)
    }

    /** Menambah ruang peran baru dengan persentase 0% dan satu kategori awal `Lain-lain`. */
    suspend fun addRoom(command: NewRoom): LedgerResult<RoomId> {
        val name = command.name.trim()
        if (name.isEmpty() || name.length > Room.NAME_MAX || command.colorSlot < 1) return failure(LedgerError.INVALID_NAME)

        val active = rooms.activeRooms()
        if (!entitlements.canAddRoom(active.size)) return failure(LedgerError.ROOM_LIMIT_REACHED)
        if (active.any { it.name.equals(name, ignoreCase = true) }) return failure(LedgerError.NAME_TAKEN)

        val room = Room(
            id = RoomId(newId()),
            name = name,
            kind = command.kind,
            iconKey = command.iconKey,
            colorSlot = command.colorSlot,
            sortOrder = (active.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        val other = Category(CategoryId(newId()), room.id, OTHER_CATEGORY, sortOrder = 0)
        rooms.addRoom(room, listOf(other))
        return LedgerResult.Success(room.id)
    }

    companion object {
        const val OTHER_CATEGORY = "Lain-lain"
    }
}
