package com.roziqrizal.rizqflow.auth

import android.content.Context
import com.roziqrizal.rizqflow.domain.auth.AuthProviderType
import com.roziqrizal.rizqflow.domain.auth.AuthSession
import com.roziqrizal.rizqflow.domain.auth.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menyimpan riwayat masuk di SharedPreferences privat aplikasi. Hanya profil singkat; tidak ada
 * token, sandi, atau isi email. `allowBackup` mati, jadi berkas ini tidak ikut cadangan otomatis.
 */
class SharedPrefsSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)

    override suspend fun current(): AuthSession? = withContext(Dispatchers.IO) {
        val id = prefs.getString(KEY_ID, null) ?: return@withContext null
        val email = prefs.getString(KEY_EMAIL, null) ?: return@withContext null
        val provider = runCatching { AuthProviderType.valueOf(prefs.getString(KEY_PROVIDER, "") ?: "") }.getOrNull()
            ?: return@withContext null
        AuthSession(
            accountId = id,
            email = email,
            displayName = prefs.getString(KEY_NAME, null),
            provider = provider,
            gmailConnected = prefs.getBoolean(KEY_GMAIL, false),
        )
    }

    override suspend fun save(session: AuthSession) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_ID, session.accountId)
                .putString(KEY_EMAIL, session.email)
                .putString(KEY_NAME, session.displayName)
                .putString(KEY_PROVIDER, session.provider.name)
                .putBoolean(KEY_GMAIL, session.gmailConnected)
                .commit()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
    }

    private companion object {
        const val KEY_ID = "account_id"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "display_name"
        const val KEY_PROVIDER = "provider"
        const val KEY_GMAIL = "gmail_connected"
    }
}
