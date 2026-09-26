package com.roziqrizal.rizqflow.auth

import com.roziqrizal.rizqflow.domain.auth.AuthProviderType
import com.roziqrizal.rizqflow.domain.auth.AuthSession

/**
 * Bentuk simpanan sesi sebagai pasangan kunci dan nilai (teks atau boolean). Dipisah dari
 * SharedPreferences supaya bisa dites tanpa Android. Sesi yang rusak atau tidak lengkap dibaca
 * sebagai "belum masuk", bukan sebagai galat.
 *
 * Akun Google punya email; akun lokal punya nama pengguna dan **tanpa email**. Keduanya harus
 * bisa kembali utuh, kalau tidak akun lokal terlempar ke halaman masuk tiap aplikasi dimulai ulang.
 */
object SessionRecord {
    const val KEY_ID = "account_id"
    const val KEY_EMAIL = "email"
    const val KEY_NAME = "display_name"
    const val KEY_PROVIDER = "provider"
    const val KEY_GMAIL = "gmail_connected"
    const val KEY_USERNAME = "username"

    fun encode(session: AuthSession): Map<String, Any?> = mapOf(
        KEY_ID to session.accountId,
        KEY_EMAIL to session.email,
        KEY_NAME to session.displayName,
        KEY_PROVIDER to session.provider.name,
        KEY_GMAIL to session.gmailConnected,
        KEY_USERNAME to session.username,
    )

    fun decode(fields: Map<String, *>): AuthSession? {
        val id = fields[KEY_ID] as? String ?: return null
        val provider = (fields[KEY_PROVIDER] as? String)?.let { name -> AuthProviderType.entries.firstOrNull { it.name == name } } ?: return null
        return try {
            AuthSession(
                accountId = id,
                email = fields[KEY_EMAIL] as? String,
                displayName = fields[KEY_NAME] as? String,
                provider = provider,
                gmailConnected = fields[KEY_GMAIL] as? Boolean ?: false,
                username = fields[KEY_USERNAME] as? String,
            )
        } catch (_: IllegalArgumentException) {
            null // catatan tidak lengkap untuk jenis masuknya (misalnya akun Google tanpa email)
        }
    }
}
