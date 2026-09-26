package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.money.Money

/** Pola ruang yang ditawarkan onboarding (S02). */
enum class RoomTemplate {
    /** Memberi, Diri, Keluarga dengan pembagian bawaan 10/30/60. */
    TIGA_HAK,

    /** Tanpa ruang: pengguna menambah sendiri lewat tab Ruang. */
    KOSONG,
}

/** Satu ruang bawaan sebuah pola: definisinya, pembagian bawaan, dan kategori awalnya. */
data class TemplateRoom(
    val name: String,
    val kind: RoomKind,
    val iconKey: String,
    val colorSlot: Int,
    val defaultShare: BasisPoints,
    val givingMode: String?,
    val categories: List<String>,
)

object RoomTemplates {
    /** Kategori sistem: tidak bisa dihapus atau diganti nama (docs/model-data.md). */
    const val UNTRACKED = "Tak terlacak"
    const val ZAKAT = "Zakat mal"

    val tigaHak: List<TemplateRoom> = listOf(
        TemplateRoom("Memberi", RoomKind.MENUNAIKAN, "heart", 1, BasisPoints.percent(10), "percentage", listOf("Sedekah", "Infak")),
        TemplateRoom("Diri", RoomKind.MENUMBUHKAN, "sprout", 2, BasisPoints.percent(30), null, listOf("Dana darurat", "Investasi", "Belajar")),
        TemplateRoom(
            "Keluarga", RoomKind.MENCUKUPI, "home", 3, BasisPoints.percent(60), null,
            listOf("Belanja bulanan", "Listrik dan air", "Sekolah", "Lain-lain"),
        ),
    )

    fun rooms(template: RoomTemplate): List<TemplateRoom> = when (template) {
        RoomTemplate.TIGA_HAK -> tigaHak
        RoomTemplate.KOSONG -> emptyList()
    }

    /**
     * Membangun ruang, kategori, dan aturan sebuah pola. [startOrder]: urutan ruang pertama (0 untuk akun
     * baru; setelah ruang yang sudah ada bila pola dipakai belakangan). [shares]: pembagian per ruang pola.
     */
    fun build(template: RoomTemplate, newId: () -> String, startOrder: Int = 0, shares: List<BasisPoints> = emptyList()): BuiltRooms {
        val templateRooms = rooms(template)
        val chosen = shares.ifEmpty { templateRooms.map { it.defaultShare } }
        require(chosen.size == templateRooms.size) { "Jumlah pembagian harus sama dengan jumlah ruang pola" }
        val rooms = templateRooms.mapIndexed { index, t ->
            Room(RoomId(newId()), t.name, t.kind, t.iconKey, t.colorSlot, sortOrder = startOrder + index, givingMode = t.givingMode)
        }
        val categories = rooms.zip(templateRooms).flatMap { (room, t) ->
            val own = t.categories.mapIndexed { i, label -> Category(CategoryId(newId()), room.id, label, sortOrder = i) }
            // Kategori sistem ikut dibuat: Koreksi saldo mencatat selisih ke "Tak terlacak" di ruang mana pun,
            // dan "Zakat mal" hanya di ruang Memberi.
            val system = buildList {
                if (room.kind == RoomKind.MENUNAIKAN) add(Category(CategoryId(newId()), room.id, ZAKAT, isSystem = true, sortOrder = own.size))
                add(Category(CategoryId(newId()), room.id, UNTRACKED, isSystem = true, sortOrder = own.size + size))
            }
            own + system
        }
        return BuiltRooms(rooms, categories, rooms.zip(chosen).map { (room, share) -> AllocationRule(room.id, share) })
    }
}

/** Hasil [RoomTemplates.build]: siap disimpan sekaligus. */
data class BuiltRooms(val rooms: List<Room>, val categories: List<Category>, val rules: List<AllocationRule>)

/** Akun pertama yang diisi di S04. */
data class FirstAccount(val name: String, val kind: AccountKind, val openingBalance: Money)

/**
 * Onboarding (S02 sampai S04): membuat isi awal ruang kerja satu akun dalam satu langkah atomik.
 * Hanya boleh berjalan sekali; ruang kerja yang sudah berisi ditolak, supaya memanggilnya dua
 * kali (misalnya ketukan ganda) tidak menggandakan ruang.
 */
class WorkspaceSetup(
    private val workspace: WorkspaceRepository,
    private val newId: () -> String,
) {

    /**
     * [shares] mengikuti urutan ruang pola dan harus berjumlah tepat 100% bila pola punya ruang;
     * bila kosong dipakai pembagian bawaan pola.
     */
    suspend fun setUp(
        template: RoomTemplate,
        firstAccount: FirstAccount,
        shares: List<BasisPoints> = emptyList(),
    ): LedgerResult<Unit> {
        val name = firstAccount.name.trim()
        if (name.isEmpty() || name.length > Account.NAME_MAX || firstAccount.openingBalance.isNegative) {
            return failure(LedgerError.INVALID_NAME)
        }
        val templateRooms = RoomTemplates.rooms(template)
        val chosen = shares.ifEmpty { templateRooms.map { it.defaultShare } }
        if (chosen.size != templateRooms.size) return failure(LedgerError.RULES_INVALID)
        if (templateRooms.isNotEmpty() && chosen.sumOf { it.value } != BasisPoints.FULL) {
            return failure(LedgerError.RULES_NOT_100_PERCENT)
        }
        if (!workspace.isEmpty()) return failure(LedgerError.WORKSPACE_NOT_EMPTY)

        val built = RoomTemplates.build(template, newId, shares = chosen)
        val account = Account(AccountId(newId()), name, firstAccount.kind, firstAccount.openingBalance)

        workspace.initialize(WorkspaceSnapshot(built.rooms, built.categories, built.rules, listOf(account)))
        return LedgerResult.Success(Unit)
    }
}
