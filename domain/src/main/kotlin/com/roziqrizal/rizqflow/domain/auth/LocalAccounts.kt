package com.roziqrizal.rizqflow.domain.auth

/**
 * Akun lokal: ada hanya di ponsel ini dan tidak dikirim ke mana pun. [passwordHash] adalah hasil
 * [PasswordHasher.hash], bukan sandi. [username] sudah dalam bentuk baku ([CredentialPolicy.normalizeUsername]).
 */
data class LocalAccount(
    val accountId: String,
    val username: String,
    val displayName: String?,
    val passwordHash: String,
)

/** Penyimpanan akun lokal. Implementasinya di lapisan aplikasi. */
interface AccountStore {
    suspend fun find(username: String): LocalAccount?

    /** Menambah akun; `false` bila nama pengguna itu sudah dipakai (pemeriksaan dan simpan satu langkah). */
    suspend fun add(account: LocalAccount): Boolean
}

sealed interface RegisterResult {
    data class Success(val session: AuthSession) : RegisterResult

    data class Invalid(val issues: List<CredentialIssue>) : RegisterResult

    data object UsernameTaken : RegisterResult
}

sealed interface PasswordSignInResult {
    data class Success(val session: AuthSession) : PasswordSignInResult

    /**
     * Nama pengguna tidak ada atau sandi salah. Sengaja tidak dibedakan agar orang lain tidak bisa
     * menebak nama pengguna mana yang terdaftar di ponsel ini.
     */
    data object WrongCredentials : PasswordSignInResult
}

/**
 * Daftar dan masuk dengan nama pengguna dan sandi. Tanpa server: ini pintu masuk lokal, bukan
 * enkripsi data. Sandi yang lupa tidak bisa dipulihkan karena tidak ada tempat untuk memulihkannya.
 */
class LocalAccountService(
    private val store: AccountStore,
    private val hasher: PasswordHasher,
    private val newAccountId: () -> String,
) {
    /** Hash pembanding untuk nama pengguna yang tidak ada, supaya waktu balasnya sama dengan sandi salah. */
    private val decoy: String by lazy { hasher.hash("decoy-password") }

    suspend fun register(
        displayName: String?,
        rawUsername: String,
        password: String,
        confirmation: String,
    ): RegisterResult {
        val issues = CredentialPolicy.validateRegistration(rawUsername, password, confirmation)
        if (issues.isNotEmpty()) return RegisterResult.Invalid(issues)

        val username = CredentialPolicy.normalizeUsername(rawUsername)
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        val account = LocalAccount(newAccountId(), username, name, hasher.hash(password))
        if (!store.add(account)) return RegisterResult.UsernameTaken
        return RegisterResult.Success(account.toSession())
    }

    suspend fun signIn(rawUsername: String, password: String): PasswordSignInResult {
        val account = store.find(CredentialPolicy.normalizeUsername(rawUsername))
        if (account == null) {
            hasher.verify(password, decoy)
            return PasswordSignInResult.WrongCredentials
        }
        return if (hasher.verify(password, account.passwordHash)) {
            PasswordSignInResult.Success(account.toSession())
        } else {
            PasswordSignInResult.WrongCredentials
        }
    }

    private fun LocalAccount.toSession() = AuthSession(
        accountId = accountId,
        email = null,
        displayName = displayName,
        provider = AuthProviderType.PASSWORD,
        username = username,
    )
}
