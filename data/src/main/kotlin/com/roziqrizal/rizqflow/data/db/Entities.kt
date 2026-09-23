package com.roziqrizal.rizqflow.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.GoldPriceSource
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.model.WealthKind

/*
 * Skema Room versi 1, mengikuti docs/model-data.md. Aturan yang berlaku di semua tabel:
 * uang berupa Long satuan terkecil, tanggal berupa epochDay, waktu sistem berupa epochMillis,
 * dan pengenal berupa UUID teks. Saldo, jatah, terpakai, dan status haul tidak disimpan;
 * semuanya dihitung dari baris di sini.
 */

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: AccountKind,
    val currency: String,
    @ColumnInfo(name = "opening_balance") val openingBalance: Long,
    val archived: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "last_reconciled_on") val lastReconciledOn: Long?,
)

@Entity(tableName = "room")
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: RoomKind,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "color_slot") val colorSlot: Int,
    /** Juga prioritas: pemutus seri pembulatan alokasi. */
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val archived: Boolean,
    /** Hanya ruang Memberi: `zakat-haul-hijri` atau `percentage`. */
    @ColumnInfo(name = "giving_mode") val givingMode: String?,
)

@Entity(
    tableName = "allocation_rule",
    foreignKeys = [ForeignKey(RoomEntity::class, ["id"], ["room_id"], onDelete = ForeignKey.CASCADE)],
)
data class AllocationRuleEntity(
    @PrimaryKey @ColumnInfo(name = "room_id") val roomId: String,
    /** Basis point, 0 sampai 10.000. */
    @ColumnInfo(name = "share_bp") val shareBp: Int,
    /**
     * Batas atas rupiah untuk mode lanjutan (Pro, versi 2); null = tak terbatas. Independen dari
     * [shareBp] — keduanya bisa terisi sekaligus, tapi hanya salah satu yang dipakai menurut mode
     * aktif (`app_setting`, kunci `allocation_mode`).
     */
    @ColumnInfo(name = "cap_amount", defaultValue = "NULL") val capAmount: Long? = null,
)

@Entity(
    tableName = "category",
    foreignKeys = [ForeignKey(RoomEntity::class, ["id"], ["room_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("room_id")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "room_id") val roomId: String,
    val name: String,
    /** Bobot relatif untuk membagi jatah ruang ke pos; null = ikut jatah ruang. */
    val weight: Int?,
    @ColumnInfo(name = "is_system") val isSystem: Boolean,
    val archived: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/** Nama tabel `money_transaction` karena `TRANSACTION` adalah kata kunci SQL. */
@Entity(
    tableName = "money_transaction",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["id"], ["to_account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(RoomEntity::class, ["id"], ["room_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["category_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index("occurred_on"),
        Index("account_id", "occurred_on"),
        Index("to_account_id"),
        Index("room_id", "occurred_on"),
        Index("category_id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val kind: TransactionKind,
    /** Selalu positif; [kind] menentukan arah. */
    val amount: Long,
    val currency: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: String?,
    @ColumnInfo(name = "room_id") val roomId: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "income_source") val incomeSource: String?,
    @ColumnInfo(name = "occurred_on") val occurredOn: Long,
    val note: String?,
    val origin: TransactionOrigin,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Potret alokasi satu pemasukan; yang belum dialirkan = nominal pemasukan dikurangi jumlah entrinya. */
@Entity(
    tableName = "allocation_entry",
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["id"], ["income_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(RoomEntity::class, ["id"], ["room_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("income_id"), Index("room_id")],
)
data class AllocationEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "income_id") val incomeId: String,
    @ColumnInfo(name = "room_id") val roomId: String,
    @ColumnInfo(name = "share_bp") val shareBp: Int,
    val amount: Long,
    @ColumnInfo(name = "allocated_at") val allocatedAt: Long,
)

@Entity(
    tableName = "quick_favorite",
    foreignKeys = [
        ForeignKey(RoomEntity::class, ["id"], ["room_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["category_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AccountEntity::class, ["id"], ["account_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("room_id"), Index("category_id"), Index("account_id")],
)
data class QuickFavoriteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amount: Long,
    @ColumnInfo(name = "room_id") val roomId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "use_count") val useCount: Int,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?,
)

/** Hari yang ditandai "Tidak ada" (sudah dicek tanpa pengeluaran). */
@Entity(tableName = "day_check")
data class DayCheckEntity(@PrimaryKey val day: Long)

@Entity(tableName = "app_setting")
data class AppSettingEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "zakat_profile")
data class ZakatProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "haul_break_policy") val haulBreakPolicy: String,
    val archived: Boolean,
)

@Entity(
    tableName = "wealth_item",
    foreignKeys = [ForeignKey(ZakatProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profile_id")],
)
data class WealthItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val kind: WealthKind,
    val label: String,
    val value: Long,
    @ColumnInfo(name = "gold_milligrams") val goldMilligrams: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Riwayat harga emas per gram, supaya nisab pada tanggal lama bisa direkonstruksi. */
@Entity(tableName = "gold_price")
data class GoldPriceEntity(
    @PrimaryKey val day: Long,
    @ColumnInfo(name = "per_gram") val perGram: Long,
    val source: GoldPriceSource,
)

/** Setara `HaulEvent.WealthChecked` di domain. */
@Entity(
    tableName = "wealth_check",
    foreignKeys = [ForeignKey(ZakatProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profile_id", "day")],
)
data class WealthCheckEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val day: Long,
    @ColumnInfo(name = "net_wealth") val netWealth: Long,
    val nisab: Long,
)

/** Setara `HaulEvent.ZakatPaid` di domain. */
@Entity(
    tableName = "zakat_payment",
    foreignKeys = [
        ForeignKey(ZakatProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TransactionEntity::class, ["id"], ["transaction_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("profile_id"), Index("transaction_id")],
)
data class ZakatPaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val day: Long,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
)

/**
 * Peran Trader (versi 3, Pro): modal dan batas risiko per trade untuk satu ruang. Ada barisnya
 * berarti modul terpasang; ruang punya paling banyak satu peran (dijaga `RoleService`).
 */
@Entity(
    tableName = "trader_profile",
    foreignKeys = [ForeignKey(RoomEntity::class, ["id"], ["room_id"], onDelete = ForeignKey.CASCADE)],
)
data class TraderProfileEntity(
    @PrimaryKey @ColumnInfo(name = "room_id") val roomId: String,
    val capital: Long,
    /** Basis point modal yang boleh hilang per trade. */
    @ColumnInfo(name = "risk_bp") val riskBp: Int,
)

/** Peran Investor (versi 3, Pro): jadwal DCA bulanan untuk satu ruang. */
@Entity(
    tableName = "dca_plan",
    foreignKeys = [
        ForeignKey(RoomEntity::class, ["id"], ["room_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AccountEntity::class, ["id"], ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["category_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("account_id"), Index("category_id")],
)
data class DcaPlanEntity(
    @PrimaryKey @ColumnInfo(name = "room_id") val roomId: String,
    val amount: Long,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
)
