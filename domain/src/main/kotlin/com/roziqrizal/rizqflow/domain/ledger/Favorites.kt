package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/** Pengeluaran yang sering berulang, dicatat dengan satu ketukan di Catat kilat (S24) dan dikelola di S13. */
data class QuickFavorite(
    val id: String,
    val name: String,
    val amount: Money,
    val roomId: RoomId,
    val categoryId: CategoryId,
    val accountId: AccountId,
    val useCount: Int = 0,
    val lastUsedAtMillis: Long? = null,
) {
    init {
        require(name.isNotBlank() && name.length <= NAME_MAX) { "Nama favorit 1 sampai $NAME_MAX karakter" }
        require(amount.isPositive) { "Nominal favorit harus lebih dari nol" }
    }

    companion object {
        const val NAME_MAX = 24

        /** Paling banyak tersimpan dan tampil di Catat kilat. */
        const val MAX_COUNT = 6
    }
}

interface FavoriteRepository {
    /** Yang paling sering dipakai dulu, lalu yang terakhir dipakai, lalu menurut nama. */
    suspend fun all(): List<QuickFavorite>

    suspend fun find(id: String): QuickFavorite?

    suspend fun save(favorite: QuickFavorite)

    suspend fun delete(id: String)
}

/** Satu baris favorit dengan nama tujuannya. [usable]: ruang, kategori, dan akunnya masih aktif. */
data class FavoriteRow(
    val favorite: QuickFavorite,
    val roomName: String,
    val categoryName: String,
    val accountName: String,
    val usable: Boolean,
)

/** Hasil memakai favorit: transaksi yang dicatat dan keadaan favorit sebelumnya, bahan Urungkan. */
data class FavoriteUse(val transaction: MoneyTransaction, val previous: QuickFavorite)

/**
 * Favorit (S13, S24). Memakai favorit mencatat pengeluaran hari itu lewat [LedgerService], jadi
 * semua aturan Catat (akun, ruang, dan kategori aktif) tetap berlaku. Urutan tampil otomatis menurut
 * pemakaian; menyeret untuk mengurutkan tidak disediakan karena skema belum punya kolom urutan.
 */
class FavoriteService(
    private val favorites: FavoriteRepository,
    private val accounts: AccountRepository,
    private val rooms: RoomRepository,
    private val ledger: LedgerService,
    private val newId: () -> String,
    private val nowMillis: () -> Long,
) {
    suspend fun list(): List<FavoriteRow> {
        val allRooms = rooms.allRooms().associateBy { it.id }
        val allCategories = rooms.allCategories().associateBy { it.id }
        val allAccounts = accounts.allAccounts().associateBy { it.id }
        return favorites.all().map { f ->
            val room = allRooms[f.roomId]
            val category = allCategories[f.categoryId]
            val account = allAccounts[f.accountId]
            FavoriteRow(
                favorite = f,
                roomName = room?.name.orEmpty(),
                categoryName = category?.name.orEmpty(),
                accountName = account?.name.orEmpty(),
                usable = room != null && !room.archived && category != null && !category.archived && account != null && !account.archived,
            )
        }
    }

    suspend fun create(name: String, amount: Money, roomId: RoomId, categoryId: CategoryId, accountId: AccountId): LedgerResult<QuickFavorite> {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > QuickFavorite.NAME_MAX) return failure(LedgerError.INVALID_NAME)
        if (!amount.isPositive) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        val existing = favorites.all()
        if (existing.any { it.name.equals(clean, ignoreCase = true) }) return failure(LedgerError.NAME_TAKEN)
        if (existing.size >= QuickFavorite.MAX_COUNT) return failure(LedgerError.FAVORITE_LIMIT_REACHED)

        val room = rooms.find(roomId) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        if (room.archived) return failure(LedgerError.ROOM_ARCHIVED)
        val category = rooms.findCategory(categoryId)
        if (category == null || category.roomId != roomId || category.archived) return failure(LedgerError.CATEGORY_NOT_IN_ROOM)
        val account = accounts.find(accountId) ?: return failure(LedgerError.ACCOUNT_NOT_FOUND)
        if (account.archived) return failure(LedgerError.ACCOUNT_ARCHIVED)
        if (account.currency != amount.currency) return failure(LedgerError.CURRENCY_MISMATCH)

        val favorite = QuickFavorite(newId(), clean, amount, roomId, categoryId, accountId)
        favorites.save(favorite)
        return LedgerResult.Success(favorite)
    }

    /** Mengubah nama dan nominal; tujuan (ruang, kategori, akun) tetap. */
    suspend fun update(id: String, name: String, amount: Money): LedgerResult<QuickFavorite> {
        val favorite = favorites.find(id) ?: return failure(LedgerError.FAVORITE_NOT_FOUND)
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > QuickFavorite.NAME_MAX) return failure(LedgerError.INVALID_NAME)
        if (!amount.isPositive) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        if (favorites.all().any { it.id != id && it.name.equals(clean, ignoreCase = true) }) return failure(LedgerError.NAME_TAKEN)
        val updated = favorite.copy(name = clean, amount = amount)
        favorites.save(updated)
        return LedgerResult.Success(updated)
    }

    suspend fun delete(id: String) = favorites.delete(id)

    /** Mencatat favorit sebagai pengeluaran pada [date]; nama favorit menjadi catatannya. */
    suspend fun use(id: String, date: LocalDate): LedgerResult<FavoriteUse> {
        val favorite = favorites.find(id) ?: return failure(LedgerError.FAVORITE_NOT_FOUND)
        val recorded = ledger.recordExpense(NewExpense(favorite.amount, favorite.accountId, favorite.roomId, favorite.categoryId, date, favorite.name))
        val transaction = when (recorded) {
            is LedgerResult.Success -> recorded.value
            is LedgerResult.Failure -> return recorded
        }
        favorites.save(favorite.copy(useCount = favorite.useCount + 1, lastUsedAtMillis = nowMillis()))
        return LedgerResult.Success(FavoriteUse(transaction, favorite))
    }

    /** Urungkan: menghapus transaksinya dan mengembalikan hitungan pemakaian favorit. */
    suspend fun undoUse(use: FavoriteUse) {
        ledger.delete(use.transaction.id)
        if (favorites.find(use.previous.id) != null) favorites.save(use.previous)
    }
}
