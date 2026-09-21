package com.roziqrizal.rizqflow.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialPolicyTest {

    @Test
    fun `nama pengguna dibakukan menjadi huruf kecil tanpa spasi tepi`() {
        assertEquals("roziq.rizal", CredentialPolicy.normalizeUsername("  Roziq.Rizal "))
    }

    @Test
    fun `nama pengguna yang wajar diterima`() {
        listOf("roziq", "abc", "a1b", "user_name-1.x", "0dimulai").forEach {
            assertEquals(emptyList(), CredentialPolicy.validateUsername(it), it)
        }
    }

    @Test
    fun `nama pengguna terlalu pendek atau terlalu panjang ditolak`() {
        assertEquals(listOf(CredentialIssue.USERNAME_TOO_SHORT), CredentialPolicy.validateUsername("ab"))
        assertEquals(listOf(CredentialIssue.USERNAME_TOO_SHORT), CredentialPolicy.validateUsername(""))
        assertEquals(listOf(CredentialIssue.USERNAME_TOO_LONG), CredentialPolicy.validateUsername("a".repeat(33)))
        assertEquals(emptyList(), CredentialPolicy.validateUsername("a".repeat(32)))
    }

    @Test
    fun `nama pengguna hanya boleh huruf angka titik garis bawah dan strip`() {
        listOf("ab cd", "abc@d", "abc!", "nama/x", "caféx", "日本語abc").forEach {
            assertTrue(
                CredentialIssue.USERNAME_INVALID_CHARACTERS in CredentialPolicy.validateUsername(it),
                "seharusnya ditolak: $it",
            )
        }
    }

    @Test
    fun `nama pengguna tidak boleh diawali tanda baca`() {
        listOf(".abc", "_abc", "-abc").forEach {
            assertTrue(CredentialIssue.USERNAME_INVALID_CHARACTERS in CredentialPolicy.validateUsername(it), it)
        }
    }

    @Test
    fun `sandi minimal delapan karakter`() {
        assertEquals(listOf(CredentialIssue.PASSWORD_TOO_SHORT), CredentialPolicy.validatePassword("1234567", "roziq"))
        assertEquals(emptyList(), CredentialPolicy.validatePassword("12345678", "roziq"))
    }

    @Test
    fun `sandi berisi spasi saja ditolak walau panjangnya cukup`() {
        assertEquals(listOf(CredentialIssue.PASSWORD_TOO_SHORT), CredentialPolicy.validatePassword("        ", "roziq"))
    }

    @Test
    fun `spasi di dalam sandi dipertahankan dan dihitung`() {
        assertEquals(emptyList(), CredentialPolicy.validatePassword("satu dua tiga", "roziq"))
    }

    @Test
    fun `sandi terlalu panjang ditolak`() {
        assertEquals(listOf(CredentialIssue.PASSWORD_TOO_LONG), CredentialPolicy.validatePassword("x".repeat(129), "roziq"))
        assertEquals(emptyList(), CredentialPolicy.validatePassword("x".repeat(128), "roziq"))
    }

    @Test
    fun `sandi tidak boleh sama dengan nama pengguna walau beda huruf besar`() {
        assertEquals(
            listOf(CredentialIssue.PASSWORD_SAME_AS_USERNAME),
            CredentialPolicy.validatePassword("Roziq.Rizal", " roziq.rizal "),
        )
    }

    @Test
    fun `konfirmasi yang berbeda ditolak`() {
        assertEquals(
            listOf(CredentialIssue.PASSWORD_MISMATCH),
            CredentialPolicy.validateRegistration("roziq", "sandi-panjang-1", "sandi-panjang-2"),
        )
    }

    @Test
    fun `pendaftaran yang benar tidak punya masalah`() {
        assertEquals(emptyList(), CredentialPolicy.validateRegistration("roziq", "sandi-panjang-1", "sandi-panjang-1"))
    }

    @Test
    fun `semua masalah dilaporkan sekaligus`() {
        assertEquals(
            listOf(CredentialIssue.USERNAME_TOO_SHORT, CredentialIssue.PASSWORD_TOO_SHORT, CredentialIssue.PASSWORD_MISMATCH),
            CredentialPolicy.validateRegistration("a", "123", "456"),
        )
    }
}
