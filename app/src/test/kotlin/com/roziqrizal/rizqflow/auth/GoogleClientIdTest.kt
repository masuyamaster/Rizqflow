package com.roziqrizal.rizqflow.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleClientIdTest {

    private val contoh = "123456789012-abcdefghijklmnopqrstuvwxyz012345.apps.googleusercontent.com"

    @Test
    fun `client ID Google yang wajar diterima`() {
        assertTrue(GoogleClientId.isValid(contoh))
    }

    @Test
    fun `kosong ditolak`() {
        assertFalse(GoogleClientId.isValid(""))
        assertFalse(GoogleClientId.isValid("   "))
    }

    @Test
    fun `bentuk yang salah tempel ditolak`() {
        listOf(
            "roziq@example.com",
            "abcdefghijklmnopqrstuvwxyz012345.apps.googleusercontent.com", // tanpa nomor proyek
            "123456789012-abc.googleusercontent.com", // tanpa apps
            "\"$contoh\"", // sisa tanda kutip
            "$contoh extra",
            "http://$contoh",
            "123456789012-ABC.apps.googleusercontent.com", // huruf besar bukan bentuk Google
        ).forEach { assertFalse(GoogleClientId.isValid(it), it) }
    }

    @Test
    fun `spasi dan baris baru di tepi dibuang oleh clean lalu diterima`() {
        assertEquals(contoh, GoogleClientId.clean("  $contoh\n"))
        assertTrue(GoogleClientId.isValid(GoogleClientId.clean("\n    $contoh\n  ")))
    }

    @Test
    fun `isValid sendiri tidak memaafkan spasi supaya salah tempel ketahuan`() {
        assertFalse(GoogleClientId.isValid(" $contoh"))
    }
}
