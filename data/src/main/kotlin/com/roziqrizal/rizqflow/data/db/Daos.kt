package com.roziqrizal.rizqflow.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.roziqrizal.rizqflow.domain.model.TransactionKind

/** Jumlah per ruang, hasil agregat bulanan. */
data class RoomTotal(
    @ColumnInfo(name = "room_id") val roomId: String,
    val total: Long,
)

@Dao
interface AccountDao {
    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun find(id: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM account")
    suspend fun count(): Int

    @Query("SELECT * FROM account WHERE archived = 0 ORDER BY sort_order")
    suspend fun active(): List<AccountEntity>

    @Query("SELECT * FROM account ORDER BY sort_order")
    suspend fun all(): List<AccountEntity>

    /** Saldo menurut catatan: saldo awal + pemasukan - pengeluaran +/- transfer. */
    @Query(
        """
        SELECT a.opening_balance
             + COALESCE((SELECT SUM(amount) FROM money_transaction WHERE kind = 'INCOME' AND account_id = a.id), 0)
             - COALESCE((SELECT SUM(amount) FROM money_transaction WHERE kind = 'EXPENSE' AND account_id = a.id), 0)
             - COALESCE((SELECT SUM(amount) FROM money_transaction WHERE kind = 'TRANSFER' AND account_id = a.id), 0)
             + COALESCE((SELECT SUM(amount) FROM money_transaction WHERE kind = 'TRANSFER' AND to_account_id = a.id), 0)
        FROM account a WHERE a.id = :accountId
        """,
    )
    suspend fun balance(accountId: String): Long
}

@Dao
interface RoomDao {
    @Upsert
    suspend fun upsert(room: RoomEntity)

    @Upsert
    suspend fun upsertAll(rooms: List<RoomEntity>)

    @Query("SELECT COUNT(*) FROM room")
    suspend fun count(): Int

    @Query("SELECT * FROM room WHERE id = :id")
    suspend fun find(id: String): RoomEntity?

    @Query("SELECT * FROM room WHERE archived = 0 ORDER BY sort_order")
    suspend fun active(): List<RoomEntity>

    @Query("SELECT * FROM room ORDER BY sort_order")
    suspend fun all(): List<RoomEntity>

    @Query("SELECT * FROM category ORDER BY room_id, sort_order")
    suspend fun allCategories(): List<CategoryEntity>

    @Upsert
    suspend fun upsertRule(rule: AllocationRuleEntity)

    @Upsert
    suspend fun upsertRules(rules: List<AllocationRuleEntity>)

    /** Aturan ruang aktif saja; aturan ruang terarsip tidak disentuh supaya persentase lamanya tidak hilang. */
    @Query("DELETE FROM allocation_rule WHERE room_id IN (SELECT id FROM room WHERE archived = 0)")
    suspend fun clearActiveRules()

    @Query("SELECT r.* FROM allocation_rule r JOIN room ON room.id = r.room_id WHERE room.archived = 0 ORDER BY room.sort_order")
    suspend fun rules(): List<AllocationRuleEntity>

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun findCategory(id: String): CategoryEntity?

    @Query("SELECT * FROM category WHERE room_id = :roomId AND archived = 0 ORDER BY sort_order")
    suspend fun categories(roomId: String): List<CategoryEntity>
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM money_transaction WHERE id = :id")
    suspend fun find(id: String): TransactionEntity?

    /** Alokasi ikut terhapus lewat ON DELETE CASCADE. */
    @Query("DELETE FROM money_transaction WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<AllocationEntryEntity>)

    @Query("DELETE FROM allocation_entry WHERE income_id = :incomeId")
    suspend fun deleteEntries(incomeId: String)

    @Query("SELECT * FROM money_transaction ORDER BY created_at DESC, rowid DESC LIMIT 1")
    suspend fun latest(): TransactionEntity?

    @Query("SELECT * FROM money_transaction WHERE kind = :kind ORDER BY created_at DESC, rowid DESC LIMIT 1")
    suspend fun latestOfKind(kind: TransactionKind): TransactionEntity?

    @Query("SELECT * FROM money_transaction WHERE occurred_on BETWEEN :fromDay AND :toDay ORDER BY occurred_on DESC, created_at DESC")
    suspend fun between(fromDay: Long, toDay: Long): List<TransactionEntity>

    /** Berurutan menurut prioritas ruang, supaya hitung ulang memakai pemutus seri yang sama. */
    @Query(
        """
        SELECT e.* FROM allocation_entry e JOIN room ON room.id = e.room_id
        WHERE e.income_id = :incomeId
        ORDER BY room.sort_order
        """,
    )
    suspend fun entriesOf(incomeId: String): List<AllocationEntryEntity>

    /** Jatah per ruang: jumlah alokasi dari pemasukan yang terjadi antara dua hari (inklusif). */
    @Query(
        """
        SELECT e.room_id AS room_id, SUM(e.amount) AS total
        FROM allocation_entry e JOIN money_transaction t ON t.id = e.income_id
        WHERE t.occurred_on BETWEEN :fromDay AND :toDay
        GROUP BY e.room_id
        """,
    )
    suspend fun allocatedPerRoom(fromDay: Long, toDay: Long): List<RoomTotal>

    /** Terpakai per ruang: jumlah pengeluaran antara dua hari (inklusif). */
    @Query(
        """
        SELECT room_id AS room_id, SUM(amount) AS total
        FROM money_transaction
        WHERE kind = 'EXPENSE' AND room_id IS NOT NULL AND occurred_on BETWEEN :fromDay AND :toDay
        GROUP BY room_id
        """,
    )
    suspend fun spentPerRoom(fromDay: Long, toDay: Long): List<RoomTotal>
}

@Dao
interface SettingsDao {
    @Upsert
    suspend fun put(setting: AppSettingEntity)

    @Query("SELECT value FROM app_setting WHERE key = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun markDayChecked(check: DayCheckEntity)

    @Query("SELECT COUNT(*) FROM day_check WHERE day = :day")
    suspend fun isDayChecked(day: Long): Int
}
