package com.roziqrizal.rizqflow.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AuthTest {

    private val sesi = AuthSession("id-1", "roziq@example.com", "Roziq", AuthProviderType.GOOGLE)

    @Test
    fun `tanpa riwayat masuk pengguna diarahkan ke halaman masuk`() {
        assertEquals(StartDestination.LOGIN, StartRouter.decide(null))
    }

    @Test
    fun `dengan riwayat masuk pengguna langsung ke menu utama`() {
        assertEquals(StartDestination.MAIN, StartRouter.decide(sesi))
    }

    @Test
    fun `sesi baru belum menghubungkan Gmail`() {
        assertFalse(sesi.gmailConnected)
    }

    @Test
    fun `pengenal akun dan email wajib terisi`() {
        assertFailsWith<IllegalArgumentException> { AuthSession(" ", "a@b.c", null, AuthProviderType.GOOGLE) }
        assertFailsWith<IllegalArgumentException> { AuthSession("id", "", null, AuthProviderType.GOOGLE) }
    }

    @Test
    fun `nama tampilan boleh kosong`() {
        assertEquals(null, AuthSession("id", "a@b.c", null, AuthProviderType.DEBUG).displayName)
    }
}
