package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationCap
import com.roziqrizal.rizqflow.domain.allocation.AllocationMode
import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.entitlement.Entitlements
import com.roziqrizal.rizqflow.domain.entitlement.Feature
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind

data class NewRoom(val name: String, val kind: RoomKind, val iconKey: String, val colorSlot: Int)

/** Satu ruang aktif beserta persentasenya (basis point; 0 bila belum punya aturan). */
data class RoomEntry(val room: Room, val shareBp: Int)

/** Keadaan sebelum ruang diarsipkan: ruang itu dan aturan alokasi aktif saat itu. */
data class ArchiveUndo(val room: Room, val activeRules: List<AllocationRule>)

/**
 * Isi S10: ruang aktif berurutan menurut prioritas, yang diarsipkan, total pembagian, dan batas ruang
 * paket ini ([roomLimit] null = tak terbatas). Total selain 100% bukan galat: ia tampil sebagai
 * ajakan mengatur ulang (misalnya setelah mengarsipkan ruang).
 */
data class RoomOverview(val active: List<RoomEntry>, val archived: List<Room>, val roomLimit: Int?) {
    val totalBp: Int get() = active.sumOf { it.shareBp }
    val isBalanced: Boolean get() = active.isEmpty() || totalBp == BasisPoints.FULL
    val canAdd: Boolean get() = roomLimit == null || active.size < roomLimit
}

/**
 * Mengatur ruang dan aturan alokasi (S10, S12).
 *
 * Aturan yang disimpan harus **tepat 100%** (prototipe F4: total lain tidak bisa disimpan).
 * Menambah ruang dibatasi [Entitlements]: ruang ke-6 ditolak untuk paket gratis, dan layar
 * mengubahnya menjadi paywall S21.
 *
 * Aturan lanjutan (Pro, docs/monetisasi.md): mode terpisah ([AllocationMode.WATERFALL]) yang
 * mengabaikan syarat 100% karena bukan persentase, lihat [saveAdvancedRules].
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

    /** Keadaan ruang untuk S10. */
    suspend fun overview(): RoomOverview {
        val active = rooms.activeRooms()
        val shares = rooms.rules().associate { it.roomId to it.share.value }
        return RoomOverview(
            active = active.map { RoomEntry(it, shares[it.id] ?: 0) },
            archived = rooms.allRooms().filter { it.archived },
            roomLimit = entitlements.roomLimit,
        )
    }

    /** Mengarsipkan ruang: tidak tampil dan tidak menerima alokasi baru; riwayat dan persentasenya tetap tersimpan. */
    suspend fun archiveRoom(id: RoomId): LedgerResult<Unit> {
        val room = rooms.find(id) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        if (!room.archived) rooms.saveRooms(listOf(room.copy(archived = true)))
        return LedgerResult.Success(Unit)
    }

    /**
     * Mengarsipkan ruang dan mengembalikan keadaan sebelumnya untuk Urungkan ([undoArchive]). Berbeda dengan
     * [restoreRoom] (Pulihkan dari daftar Diarsipkan: urutan terakhir, 0%), Urungkan mengembalikan urutan
     * dan persentase seperti semula.
     */
    suspend fun archiveWithUndo(id: RoomId): LedgerResult<ArchiveUndo> {
        val room = rooms.find(id) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        val undo = ArchiveUndo(room, rooms.rules())
        archiveRoom(id)
        return LedgerResult.Success(undo)
    }

    /** Mengurungkan [archiveWithUndo]. Batas ruang dan nama yang bentrok tetap diperiksa. */
    suspend fun undoArchive(undo: ArchiveUndo): LedgerResult<Unit> {
        val current = rooms.find(undo.room.id) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        if (!current.archived) return LedgerResult.Success(Unit)
        val active = rooms.activeRooms()
        if (!entitlements.canAddRoom(active.size)) return failure(LedgerError.ROOM_LIMIT_REACHED)
        if (active.any { it.name.equals(current.name, ignoreCase = true) }) return failure(LedgerError.NAME_TAKEN)
        rooms.saveRooms(listOf(undo.room.copy(archived = false)), rules = undo.activeRules)
        return LedgerResult.Success(Unit)
    }

    /**
     * Memulihkan ruang terarsip ke urutan paling belakang. Batas ruang paket berlaku (memulihkan menambah
     * ruang aktif). Persentasenya dimulai 0% supaya total aturan aktif tidak diam-diam melebihi 100%.
     */
    suspend fun restoreRoom(id: RoomId): LedgerResult<Unit> {
        val room = rooms.find(id) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        if (!room.archived) return LedgerResult.Success(Unit)
        val active = rooms.activeRooms()
        if (!entitlements.canAddRoom(active.size)) return failure(LedgerError.ROOM_LIMIT_REACHED)
        if (active.any { it.name.equals(room.name, ignoreCase = true) }) return failure(LedgerError.NAME_TAKEN)

        val restored = room.copy(archived = false, sortOrder = (active.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        rooms.saveRooms(listOf(restored), rules = rooms.rules() + AllocationRule(id, BasisPoints(0)))
        return LedgerResult.Success(Unit)
    }

    /** Menggeser ruang satu tempat naik ([delta] -1) atau turun (+1). Urutan adalah prioritas sisa pembulatan dan urutan di Denah. */
    suspend fun moveRoom(id: RoomId, delta: Int): LedgerResult<Unit> {
        val active = rooms.activeRooms()
        val index = active.indexOfFirst { it.id == id }
        if (index < 0) return failure(LedgerError.ROOM_NOT_FOUND)
        val target = index + delta
        if (delta == 0 || target !in active.indices) return LedgerResult.Success(Unit)
        val a = active[index]
        val b = active[target]
        rooms.saveRooms(listOf(a.copy(sortOrder = b.sortOrder), b.copy(sortOrder = a.sortOrder)))
        return LedgerResult.Success(Unit)
    }

    /** Memakai pola ruang (Tiga hak) pada akun yang belum punya ruang aktif, misalnya setelah Mulai kosong. */
    suspend fun applyTemplate(template: RoomTemplate): LedgerResult<Unit> {
        if (rooms.activeRooms().isNotEmpty()) return failure(LedgerError.WORKSPACE_NOT_EMPTY)
        val count = RoomTemplates.rooms(template).size
        if (count == 0) return LedgerResult.Success(Unit)
        val limit = entitlements.roomLimit
        if (limit != null && count > limit) return failure(LedgerError.ROOM_LIMIT_REACHED)
        val next = (rooms.allRooms().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val built = RoomTemplates.build(template, newId, startOrder = next)
        rooms.addRooms(built.rooms, built.categories, built.rules)
        return LedgerResult.Success(Unit)
    }

    /** Mengembalikan aturan ke keadaan sebelumnya tanpa memeriksa total (dasar Urungkan setelah menyimpan aturan). */
    suspend fun restoreRules(rules: List<AllocationRule>) {
        rooms.replaceRules(rules)
    }

    /** Paket sekarang membuka aturan alokasi lanjutan (S12). */
    suspend fun advancedRulesEntitled(): Boolean = entitlements.isEnabled(Feature.ADVANCED_ALLOCATION_RULES)

    /**
     * Menyalakan mode lanjutan (Pro) dan menyimpan batas atasnya sekaligus (S12). [caps] harus
     * mencakup persis ruang aktif, tanpa ganda; batas atas tidak boleh negatif. Aturan persentase
     * ([rules]) tidak ikut berubah, jadi mematikan mode lanjutan nanti kembali memakainya apa adanya.
     */
    suspend fun saveAdvancedRules(caps: List<AllocationCap>): LedgerResult<Unit> {
        if (!entitlements.isEnabled(Feature.ADVANCED_ALLOCATION_RULES)) return failure(LedgerError.FEATURE_LOCKED)
        val activeRooms = rooms.activeRooms()
        val active = activeRooms.map { it.id }.toSet()
        if (caps.map { it.roomId }.toSet().size != caps.size || caps.any { it.roomId !in active } || caps.size != active.size) {
            return failure(LedgerError.RULES_INVALID)
        }
        if (caps.any { it.capAmount?.isNegative == true }) return failure(LedgerError.AMOUNT_NOT_POSITIVE)

        val priority = activeRooms.withIndex().associate { (index, room) -> room.id to index }
        rooms.setAllocationMode(AllocationMode.WATERFALL)
        rooms.replaceCaps(caps.sortedBy { priority.getValue(it.roomId) })
        return LedgerResult.Success(Unit)
    }

    /** Kembali ke mode persentase dasar. Selalu diizinkan: turun paket tidak boleh mengunci pengguna keluar dari datanya. */
    suspend fun disableAdvancedRules() {
        rooms.setAllocationMode(AllocationMode.PERCENTAGE)
    }

    /** Menambah ruang peran baru dengan persentase 0% dan kategori awal `Lain-lain` serta kategori sistem `Tak terlacak`. */
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
        // Ruang Menunaikan lain (mis. dibuat manual, bukan lewat pola Tiga hak) tetap butuh kategori Zakat mal
        // supaya Tunaikan zakat (S17) selalu punya tujuan; lihat WorkspaceSetup untuk pola bawaan.
        val zakat = if (command.kind == RoomKind.MENUNAIKAN) {
            listOf(Category(CategoryId(newId()), room.id, RoomTemplates.ZAKAT, isSystem = true, sortOrder = 1))
        } else {
            emptyList()
        }
        val untracked = Category(CategoryId(newId()), room.id, RoomTemplates.UNTRACKED, isSystem = true, sortOrder = 1 + zakat.size)
        rooms.addRoom(room, listOf(other) + zakat + listOf(untracked))
        return LedgerResult.Success(room.id)
    }

    companion object {
        const val OTHER_CATEGORY = "Lain-lain"
    }
}
