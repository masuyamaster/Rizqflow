package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.data.repo.LocalAccountRepository
import com.roziqrizal.rizqflow.data.repo.LocalFavoriteRepository
import com.roziqrizal.rizqflow.data.repo.LocalRoomRepository
import com.roziqrizal.rizqflow.data.repo.LocalTransactionRepository
import com.roziqrizal.rizqflow.data.repo.LocalWorkspaceRepository
import com.roziqrizal.rizqflow.domain.auth.AccountStorage

/**
 * Ruang kerja data satu akun: satu database beserta repositorinya (diputuskan 2026-09-21).
 * Berganti akun berarti menutup ini dan membuka yang lain; akun yang sama selalu kembali ke
 * berkas yang sama, dan Keluar tidak menghapus berkas.
 */
class LocalLedger(val db: RizqflowDatabase) : AutoCloseable {
    val workspace = LocalWorkspaceRepository(db)
    val accounts = LocalAccountRepository(db)
    val rooms = LocalRoomRepository(db)
    val transactions = LocalTransactionRepository(db)
    val favorites = LocalFavoriteRepository(db)

    override fun close() = db.close()

    companion object {
        /**
         * Membuka (atau membuat) database milik [accountId]. Tidak memakai migrasi destruktif: skema
         * yang naik versi wajib punya migrasi (docs/model-data.md).
         */
        fun open(context: Context, accountId: String): LocalLedger {
            val db = Room.databaseBuilder(
                context.applicationContext,
                RizqflowDatabase::class.java,
                AccountStorage.databaseName(accountId),
            ).build()
            return LocalLedger(db)
        }
    }
}
