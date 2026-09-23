package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.MIGRATION_1_2
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Menguji migrasi skema Room terhadap berkas skema JSON versi 1 yang di-commit (docs/model-data.md:
 * setiap kenaikan versi wajib punya tes migrasi, tanpa migrasi destruktif).
 *
 * Tidak memakai `MigrationTestHelper` (androidx.room:room-testing): pada Room 2.8.5 di bawah
 * Robolectric, helper itu selalu membuka koneksi lewat `SupportSQLiteDriver` dan melempar
 * `IllegalArgumentException` ("driver dikonfigurasi membuka X tapi Y diminta") sebelum migrasi
 * sempat berjalan, terlepas dari `openFactory` yang diberikan — kemungkinan bug spesifik kombinasi
 * versi ini. Sebagai gantinya, tes ini membangun berkas skema versi 1 apa adanya dari `createSql`
 * di 1.json (bukan menyalin tangan), lalu membukanya lewat jalur produksi sungguhan
 * (`Room.databaseBuilder(...).addMigrations(...)`, sama seperti [LocalLedger.open]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    @Test
    fun `versi 1 ke 2 menambah cap_amount tanpa menghapus data yang ada`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(TEST_DB)
        dbFile.delete()

        buildSchemaVersion1(context, dbFile.path)

        val db = Room.databaseBuilder(context, RizqflowDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            db.openHelper.writableDatabase.query("SELECT share_bp, cap_amount FROM allocation_rule WHERE room_id = 'r1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(10_000, cursor.getInt(0))
                assertTrue(cursor.isNull(1))
            }
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    /** Membangun berkas versi 1 dari `createSql` tiap entitas di 1.json, lalu mengisi satu ruang dan aturannya. */
    private fun buildSchemaVersion1(context: Context, path: String) {
        val schema = JSONObject(
            context.assets.open("$SCHEMA_DIR/1.json").use { it.reader(Charsets.UTF_8).readText() },
        ).getJSONObject("database")
        val entities = schema.getJSONArray("entities")

        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
            }
            raw.execSQL("INSERT INTO room (id, name, kind, icon_key, color_slot, sort_order, archived, giving_mode) VALUES ('r1', 'Keluarga', 'MENCUKUPI', 'home', 1, 0, 0, NULL)")
            raw.execSQL("INSERT INTO allocation_rule (room_id, share_bp) VALUES ('r1', 10000)")
            raw.version = 1
        } finally {
            raw.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val SCHEMA_DIR = "com.roziqrizal.rizqflow.data.db.RizqflowDatabase"
    }
}
