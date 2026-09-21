package com.roziqrizal.rizqflow.auth

import com.roziqrizal.rizqflow.domain.auth.AuthProviderType
import com.roziqrizal.rizqflow.domain.auth.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionRecordTest {

    private fun roundTrip(s: AuthSession) = SessionRecord.decode(SessionRecord.encode(s))

    @Test
    fun `sesi Google kembali utuh`() {
        val s = AuthSession("roziq@example.com", "roziq@example.com", "Roziq", AuthProviderType.GOOGLE, gmailConnected = true)
        assertEquals(s, roundTrip(s))
    }

    @Test
    fun `sesi akun lokal tanpa email kembali utuh dengan nama penggunanya`() {
        // Regresi: dulu email wajib, jadi akun lokal dianggap belum masuk setiap aplikasi dimulai ulang.
        val s = AuthSession("uuid-1", null, "Roziq", AuthProviderType.PASSWORD, username = "roziq.test")
        assertEquals(s, roundTrip(s))
        assertEquals("roziq.test", roundTrip(s)!!.username)
    }

    @Test
    fun `sesi uji dan nama kosong kembali utuh`() {
        val s = AuthSession("debug", "uji@rizqflow.local", null, AuthProviderType.DEBUG)
        assertEquals(s, roundTrip(s))
    }

    @Test
    fun `catatan kosong atau tanpa pengenal dibaca sebagai belum masuk`() {
        assertNull(SessionRecord.decode(emptyMap<String, Any?>()))
        assertNull(SessionRecord.decode(mapOf(SessionRecord.KEY_PROVIDER to "GOOGLE", SessionRecord.KEY_EMAIL to "a@b.c")))
    }

    @Test
    fun `jenis masuk tidak dikenal atau hilang dibaca sebagai belum masuk`() {
        assertNull(SessionRecord.decode(mapOf(SessionRecord.KEY_ID to "x", SessionRecord.KEY_PROVIDER to "FACEBOOK")))
        assertNull(SessionRecord.decode(mapOf(SessionRecord.KEY_ID to "x")))
    }

    @Test
    fun `catatan yang tidak lengkap untuk jenisnya dibaca sebagai belum masuk`() {
        // Akun Google wajib punya email; akun lokal wajib punya nama pengguna.
        assertNull(SessionRecord.decode(mapOf(SessionRecord.KEY_ID to "x", SessionRecord.KEY_PROVIDER to "GOOGLE")))
        assertNull(SessionRecord.decode(mapOf(SessionRecord.KEY_ID to "x", SessionRecord.KEY_PROVIDER to "PASSWORD")))
    }

    @Test
    fun `penanda Gmail bawaannya mati`() {
        val hasil = SessionRecord.decode(mapOf(SessionRecord.KEY_ID to "x", SessionRecord.KEY_PROVIDER to "DEBUG"))
        assertEquals(false, hasil!!.gmailConnected)
    }

    @Test
    fun `nilai berjenis salah tidak melempar galat`() {
        val hasil = SessionRecord.decode(
            mapOf(SessionRecord.KEY_ID to "x", SessionRecord.KEY_PROVIDER to "DEBUG", SessionRecord.KEY_GMAIL to "ya", SessionRecord.KEY_NAME to 5),
        )
        assertEquals(false, hasil!!.gmailConnected)
        assertNull(hasil.displayName)
    }
}
