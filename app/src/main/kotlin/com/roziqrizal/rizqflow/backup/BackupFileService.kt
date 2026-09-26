package com.roziqrizal.rizqflow.backup

import android.content.Context
import com.roziqrizal.rizqflow.domain.auth.AccountStorage
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cadangan dan pemulihan berkas database mentah satu akun (F7, S20). Bukan ekspor terstruktur
 * seperti CSV: ini salinan apa adanya berkas SQLite Room, dienkripsi terpisah lewat
 * [com.roziqrizal.rizqflow.domain.backup.BackupCrypto], supaya restore menghasilkan data yang
 * identik persis (diputuskan 2026-09-21, docs/konsep.md).
 */
object BackupFileService {

    /**
     * Isi mentah database akun yang sedang aktif, siap dienkripsi: checkpoint (lihat
     * [LocalLedger.checkpoint]) lalu salin [LocalLedger.databaseFile] apa adanya. Lokasi berkas
     * diambil dari koneksi yang sedang terbuka, bukan dihitung ulang, supaya pemanggilnya tidak
     * perlu tahu pengenal akun.
     */
    suspend fun snapshot(workspace: AccountWorkspace): ByteArray = withContext(Dispatchers.IO) {
        workspace.repositories.checkpoint()
        workspace.repositories.databaseFile.readBytes()
    }

    /**
     * Menimpa berkas database akun ini dengan [decrypted]. Pemanggil wajib menutup
     * [AccountWorkspace] akun ini lebih dulu (berkas terkunci selama koneksinya terbuka) dan
     * membuka ulang setelah ini selesai.
     */
    suspend fun overwrite(context: Context, accountId: String, decrypted: ByteArray) = withContext(Dispatchers.IO) {
        val file = databaseFile(context, accountId)
        file.parentFile?.mkdirs()
        file.writeBytes(decrypted)
        // Berkas -wal/-shm lama menyimpan perubahan tertunda milik database SEBELUM ditimpa; kalau
        // dibiarkan, Room bisa mengira ada perubahan itu yang belum tersimpan ke berkas baru ini.
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
    }

    private fun databaseFile(context: Context, accountId: String): File =
        context.getDatabasePath(AccountStorage.databaseName(accountId))
}
