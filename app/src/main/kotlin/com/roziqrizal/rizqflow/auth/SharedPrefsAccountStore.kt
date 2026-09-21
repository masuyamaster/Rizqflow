package com.roziqrizal.rizqflow.auth

import android.content.Context
import com.roziqrizal.rizqflow.domain.auth.AccountStore
import com.roziqrizal.rizqflow.domain.auth.LocalAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Menyimpan akun lokal di SharedPreferences privat aplikasi, terpisah dari riwayat masuk supaya
 * "Keluar" tidak menghapus akun. Yang tersimpan hanya hash sandi (PBKDF2 dengan garam), bukan
 * sandinya. `allowBackup` mati, jadi berkas ini tidak ikut cadangan otomatis dan tidak berpindah
 * ke ponsel lain. Nama pengguna sudah dibakukan dan hanya berisi a-z, 0-9, titik, garis bawah, strip,
 * sehingga aman menjadi bagian nama kunci.
 */
class SharedPrefsAccountStore(context: Context) : AccountStore {
    private val prefs = context.applicationContext.getSharedPreferences("accounts", Context.MODE_PRIVATE)
    private val lock = Mutex()

    override suspend fun find(username: String): LocalAccount? = withContext(Dispatchers.IO) {
        read(username)
    }

    override suspend fun add(account: LocalAccount): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            if (read(account.username) != null) return@withContext false
            prefs.edit()
                .putString(key(account.username, ID), account.accountId)
                .putString(key(account.username, NAME), account.displayName)
                .putString(key(account.username, HASH), account.passwordHash)
                .commit()
        }
    }

    private fun read(username: String): LocalAccount? {
        val id = prefs.getString(key(username, ID), null) ?: return null
        val hash = prefs.getString(key(username, HASH), null) ?: return null
        return LocalAccount(id, username, prefs.getString(key(username, NAME), null), hash)
    }

    private fun key(username: String, field: String) = "$username|$field"

    private companion object {
        const val ID = "id"
        const val NAME = "name"
        const val HASH = "hash"
    }
}
