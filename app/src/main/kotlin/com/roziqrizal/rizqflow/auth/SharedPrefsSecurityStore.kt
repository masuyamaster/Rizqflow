package com.roziqrizal.rizqflow.auth

import android.content.Context
import com.roziqrizal.rizqflow.domain.auth.AutoLockMode
import com.roziqrizal.rizqflow.domain.auth.SecurityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menyimpan kunci aplikasi (S19) di SharedPreferences privat aplikasi, terpisah dari riwayat
 * masuk dan akun lokal: kunci berlaku untuk aplikasi, bukan satu akun, dan bertahan lewat Keluar.
 * `allowBackup` mati, jadi berkas ini tidak ikut cadangan otomatis.
 */
class SharedPrefsSecurityStore(context: Context) : SecurityStore {
    private val prefs = context.applicationContext.getSharedPreferences("security", Context.MODE_PRIVATE)

    override suspend fun pinHash(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY_PIN_HASH, null) }

    override suspend fun savePinHash(hash: String?) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_PIN_HASH, hash).commit()
        Unit
    }

    override suspend fun biometricEnabled(): Boolean = withContext(Dispatchers.IO) { prefs.getBoolean(KEY_BIOMETRIC, false) }

    override suspend fun saveBiometricEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).commit()
        Unit
    }

    override suspend fun autoLockMode(): AutoLockMode = withContext(Dispatchers.IO) {
        prefs.getString(KEY_AUTO_LOCK, null)?.let { raw -> AutoLockMode.entries.firstOrNull { it.name == raw } } ?: AutoLockMode.IMMEDIATELY
    }

    override suspend fun saveAutoLockMode(mode: AutoLockMode) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_AUTO_LOCK, mode.name).commit()
        Unit
    }

    override suspend fun failedAttempts(): Int = withContext(Dispatchers.IO) { prefs.getInt(KEY_ATTEMPTS, 0) }

    override suspend fun saveFailedAttempts(count: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(KEY_ATTEMPTS, count).commit()
        Unit
    }

    override suspend fun lockedUntilMillis(): Long? = withContext(Dispatchers.IO) {
        prefs.getLong(KEY_LOCKED_UNTIL, 0L).takeIf { it > 0L }
    }

    override suspend fun saveLockedUntilMillis(until: Long?) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_LOCKED_UNTIL, until ?: 0L).commit()
        Unit
    }

    private companion object {
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_AUTO_LOCK = "auto_lock_mode"
        const val KEY_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_until"
    }
}
