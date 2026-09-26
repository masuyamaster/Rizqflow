package com.roziqrizal.rizqflow.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalAccountServiceTest {

    private class MemoryStore : AccountStore {
        val accounts = linkedMapOf<String, LocalAccount>()

        override suspend fun find(username: String) = accounts[username]

        override suspend fun add(account: LocalAccount): Boolean {
            if (account.username in accounts) return false
            accounts[account.username] = account
            return true
        }
    }

    /** Pengolah tiruan: cepat dan menghitung berapa kali dipakai memeriksa sandi. */
    private class CountingHasher : PasswordHasher {
        var verifyCalls = 0

        override fun hash(password: String) = "hash($password)"

        override fun verify(password: String, encoded: String): Boolean {
            verifyCalls++
            return encoded == "hash($password)"
        }
    }

    private val store = MemoryStore()
    private val hasher = CountingHasher()
    private var counter = 0
    private val service = LocalAccountService(store, hasher) { "acc-${++counter}" }

    private fun register(name: String? = "Roziq", user: String = "roziq", pass: String = "sandi-panjang-1", confirm: String = pass) =
        runSuspend { service.register(name, user, pass, confirm) }

    @Test
    fun `daftar menghasilkan sesi akun lokal tanpa email`() {
        val result = assertIs<RegisterResult.Success>(register())

        assertEquals("acc-1", result.session.accountId)
        assertEquals(AuthProviderType.PASSWORD, result.session.provider)
        assertEquals("roziq", result.session.username)
        assertNull(result.session.email)
        assertEquals("Roziq", result.session.displayName)
        assertEquals("roziq", result.session.identifier)
    }

    @Test
    fun `yang disimpan adalah hash bukan sandi`() {
        register(pass = "sandi-panjang-1")

        val saved = store.accounts.getValue("roziq")
        assertNotEquals("sandi-panjang-1", saved.passwordHash)
        assertEquals("hash(sandi-panjang-1)", saved.passwordHash)
    }

    @Test
    fun `nama pengguna disimpan dalam bentuk baku`() {
        register(user = "  Roziq ")

        assertTrue("roziq" in store.accounts)
    }

    @Test
    fun `nama tampilan kosong atau spasi menjadi null`() {
        val result = assertIs<RegisterResult.Success>(register(name = "   "))
        assertNull(result.session.displayName)
        assertNull(assertIs<RegisterResult.Success>(register(name = null, user = "lain")).session.displayName)
    }

    @Test
    fun `nama pengguna yang sudah dipakai ditolak walau beda huruf besar`() {
        register(user = "roziq")

        assertEquals(RegisterResult.UsernameTaken, register(user = "ROZIQ", pass = "sandi-lain-99"))
        assertEquals(1, store.accounts.size)
    }

    @Test
    fun `isian tidak sah ditolak dan tidak menyimpan apa pun`() {
        val result = assertIs<RegisterResult.Invalid>(register(pass = "pendek"))

        assertTrue(CredentialIssue.PASSWORD_TOO_SHORT in result.issues)
        assertTrue(store.accounts.isEmpty())
    }

    @Test
    fun `konfirmasi berbeda ditolak`() {
        val result = assertIs<RegisterResult.Invalid>(register(pass = "sandi-panjang-1", confirm = "sandi-panjang-2"))
        assertEquals(listOf(CredentialIssue.PASSWORD_MISMATCH), result.issues)
    }

    @Test
    fun `masuk dengan sandi yang benar`() {
        register()

        val result = assertIs<PasswordSignInResult.Success>(runSuspend { service.signIn("roziq", "sandi-panjang-1") })
        assertEquals("acc-1", result.session.accountId)
    }

    @Test
    fun `masuk tidak membedakan huruf besar pada nama pengguna`() {
        register()

        assertIs<PasswordSignInResult.Success>(runSuspend { service.signIn(" ROZIQ ", "sandi-panjang-1") })
    }

    @Test
    fun `sandi tetap membedakan huruf besar`() {
        register()

        assertEquals(PasswordSignInResult.WrongCredentials, runSuspend { service.signIn("roziq", "Sandi-panjang-1") })
    }

    @Test
    fun `sandi salah ditolak`() {
        register()

        assertEquals(PasswordSignInResult.WrongCredentials, runSuspend { service.signIn("roziq", "salah-total-1") })
    }

    @Test
    fun `nama pengguna tak dikenal mendapat hasil yang sama dengan sandi salah`() {
        register()

        val tidakAda = runSuspend { service.signIn("tidak-ada", "sandi-panjang-1") }
        val salah = runSuspend { service.signIn("roziq", "salah-total-1") }
        assertEquals(salah, tidakAda)
    }

    @Test
    fun `nama pengguna tak dikenal tetap memeriksa sandi agar waktunya tidak membocorkan`() {
        register()
        hasher.verifyCalls = 0

        runSuspend { service.signIn("tidak-ada", "apa-saja-123") }

        assertEquals(1, hasher.verifyCalls)
    }

    @Test
    fun `dua akun terpisah tidak saling membuka`() {
        register(user = "satu", pass = "sandi-satu-111")
        register(user = "dua", pass = "sandi-dua-2222")

        assertFalse(runSuspend { service.signIn("satu", "sandi-dua-2222") } is PasswordSignInResult.Success)
        assertTrue(runSuspend { service.signIn("dua", "sandi-dua-2222") } is PasswordSignInResult.Success)
    }
}
