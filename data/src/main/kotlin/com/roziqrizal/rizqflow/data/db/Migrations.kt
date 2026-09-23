package com.roziqrizal.rizqflow.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versi 1 ke 2 (2026-09-23): menambah `allocation_rule.cap_amount` (rupiah, boleh kosong) untuk
 * aturan alokasi lanjutan (Pro, docs/monetisasi.md). Aditif saja: baris lama tidak tersentuh,
 * kolom baru selalu null sampai pengguna mengatur batas atas.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE allocation_rule ADD COLUMN cap_amount INTEGER DEFAULT NULL")
    }
}
