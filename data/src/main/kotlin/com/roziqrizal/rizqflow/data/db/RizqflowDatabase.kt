package com.roziqrizal.rizqflow.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Database lokal. Setiap perubahan skema wajib menaikkan [version], menambah migrasi, dan
 * punya tes migrasi; `fallbackToDestructiveMigration` dilarang (docs/model-data.md).
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class RizqflowDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun rooms(): RoomDao
    abstract fun transactions(): TransactionDao
    abstract fun settings(): SettingsDao
    abstract fun favorites(): FavoriteDao
}
