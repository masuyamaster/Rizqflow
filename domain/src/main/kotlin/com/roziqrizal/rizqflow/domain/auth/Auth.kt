package com.roziqrizal.rizqflow.domain.auth

/** Cara pengguna masuk. [DEBUG] hanya ada di build debug untuk menguji alur tanpa akun Google. */
enum class AuthProviderType { GOOGLE, DEBUG }

/**
 * Riwayat masuk yang disimpan di ponsel. Tidak menyimpan token atau sandi: sesi hanya penanda
 * bahwa pengguna sudah pernah masuk, supaya berikutnya langsung ke menu utama.
 * [gmailConnected] menandai izin baca Gmail sudah diberikan; aplikasi belum membaca email apa pun.
 */
data class AuthSession(
    val accountId: String,
    val email: String,
    val displayName: String?,
    val provider: AuthProviderType,
    val gmailConnected: Boolean = false,
) {
    init {
        require(accountId.isNotBlank()) { "Pengenal akun tidak boleh kosong" }
        require(email.isNotBlank()) { "Email tidak boleh kosong" }
    }
}

/** Penyimpanan sesi. Implementasinya di lapisan aplikasi (SharedPreferences). */
interface SessionStore {
    suspend fun current(): AuthSession?

    suspend fun save(session: AuthSession)

    suspend fun clear()
}

enum class StartDestination { LOGIN, MAIN }

/** Menentukan halaman setelah splash: sudah punya riwayat masuk langsung ke menu utama. */
object StartRouter {
    fun decide(session: AuthSession?): StartDestination =
        if (session == null) StartDestination.LOGIN else StartDestination.MAIN
}

/** Hasil masuk dengan Google. */
sealed interface SignInResult {
    data class Success(val session: AuthSession) : SignInResult

    /** Pengguna menutup dialog pilih akun. Bukan galat, tidak perlu pesan. */
    data object Cancelled : SignInResult

    /** Build ini belum diberi client ID Google (lihat docs/auth-google.md). */
    data object NotConfigured : SignInResult

    data class Failed(val reason: String) : SignInResult
}

/** Hasil meminta izin baca Gmail. */
sealed interface GmailConnectResult {
    data object Connected : GmailConnectResult

    /** Pengguna menolak izin. Aplikasi tetap bisa dipakai tanpa Gmail. */
    data object Denied : GmailConnectResult

    data object NotConfigured : GmailConnectResult

    data class Failed(val reason: String) : GmailConnectResult
}
