package com.roziqrizal.rizqflow.domain.auth

import java.util.Locale

/** Alasan isian pendaftaran ditolak. Layar memetakannya ke pesan di bawah kolom yang bersangkutan. */
enum class CredentialIssue {
    USERNAME_TOO_SHORT,
    USERNAME_TOO_LONG,
    USERNAME_INVALID_CHARACTERS,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG,
    PASSWORD_SAME_AS_USERNAME,
    PASSWORD_MISMATCH,
}

/**
 * Aturan nama pengguna dan sandi untuk akun lokal.
 *
 * Nama pengguna tidak membedakan huruf besar-kecil (disimpan huruf kecil), 3 sampai 32 karakter,
 * hanya huruf a-z, angka, titik, garis bawah, dan strip, dan diawali huruf atau angka.
 * Sandi minimal 8 karakter dan tidak dipotong spasinya, karena spasi boleh menjadi bagian sandi.
 * Tidak ada aturan "wajib simbol dan huruf besar": panjang lebih berguna daripada komposisi.
 */
object CredentialPolicy {
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 32
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 128

    private val usernameShape = Regex("[a-z0-9][a-z0-9._-]*")

    /** Bentuk baku nama pengguna: spasi tepi dibuang dan diubah ke huruf kecil. */
    fun normalizeUsername(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    fun validateUsername(raw: String): List<CredentialIssue> {
        val username = normalizeUsername(raw)
        return buildList {
            if (username.length < USERNAME_MIN) add(CredentialIssue.USERNAME_TOO_SHORT)
            if (username.length > USERNAME_MAX) add(CredentialIssue.USERNAME_TOO_LONG)
            if (username.isNotEmpty() && !usernameShape.matches(username)) add(CredentialIssue.USERNAME_INVALID_CHARACTERS)
        }
    }

    fun validatePassword(password: String, rawUsername: String): List<CredentialIssue> = buildList {
        if (password.isBlank() || password.length < PASSWORD_MIN) add(CredentialIssue.PASSWORD_TOO_SHORT)
        if (password.length > PASSWORD_MAX) add(CredentialIssue.PASSWORD_TOO_LONG)
        val username = normalizeUsername(rawUsername)
        if (username.isNotEmpty() && password.lowercase(Locale.ROOT) == username) add(CredentialIssue.PASSWORD_SAME_AS_USERNAME)
    }

    fun validateRegistration(rawUsername: String, password: String, confirmation: String): List<CredentialIssue> =
        validateUsername(rawUsername) +
            validatePassword(password, rawUsername) +
            if (password != confirmation) listOf(CredentialIssue.PASSWORD_MISMATCH) else emptyList()
}
