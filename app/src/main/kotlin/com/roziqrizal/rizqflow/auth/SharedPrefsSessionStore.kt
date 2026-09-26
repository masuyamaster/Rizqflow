package com.roziqrizal.rizqflow.auth

import android.content.Context
import com.roziqrizal.rizqflow.domain.auth.AuthSession
import com.roziqrizal.rizqflow.domain.auth.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menyimpan riwayat masuk di SharedPreferences privat aplikasi. Hanya profil singkat; tidak ada
 * token, sandi, atau isi email. `allowBackup` mati, jadi berkas ini tidak ikut cadangan otomatis.
 * Bentuk isinya diatur [SessionRecord] (dan dites di sana).
 */
class SharedPrefsSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)

    override suspend fun current(): AuthSession? = withContext(Dispatchers.IO) { SessionRecord.decode(prefs.all) }

    override suspend fun save(session: AuthSession) {
        withContext(Dispatchers.IO) {
            val editor = prefs.edit().clear()
            SessionRecord.encode(session).forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is Boolean -> editor.putBoolean(key, value)
                    else -> editor.putString(key, value.toString())
                }
            }
            editor.commit()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
    }
}
