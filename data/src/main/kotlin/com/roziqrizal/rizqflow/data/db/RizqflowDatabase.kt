package com.roziqrizal.rizqflow.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Database lokal. Setiap perubahan skema wajib menaikkan [version], menambah migrasi, dan
 * punya tes migrasi; `fallbackToDestructiveMigration` dilarang (docs/model-data.md).
 *
 * Versi 2 (2026-09-23): menambah `allocation_rule.cap_amount` untuk aturan alokasi lanjutan
 * (Pro); lihat [MIGRATION_1_2] di Migrations.kt.
 *
 * Versi 3 (2026-09-23): menambah tabel `trader_profile` dan `dca_plan` untuk sistem per peran
 * (Pro); lihat [MIGRATION_2_3].
 */
@Database(
    entities = [
        AccountEntity::class,
        RoomEntity::class,
        AllocationRuleEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        AllocationEntryEntity::class,
        QuickFavoriteEntity::class,
        DayCheckEntity::class,
        AppSettingEntity::class,
        ZakatProfileEntity::class,
        WealthItemEntity::class,
        GoldPriceEntity::class,
        WealthCheckEntity::class,
        ZakatPaymentEntity::class,
        TraderProfileEntity::class,
        DcaPlanEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class RizqflowDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun rooms(): RoomDao
    abstract fun transactions(): TransactionDao
    abstract fun settings(): SettingsDao
    abstract fun favorites(): FavoriteDao
    abstract fun zakat(): ZakatDao
    abstract fun roles(): RoleDao
}
