package com.roziqrizal.rizqflow.domain.auth

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {

    // Putaran rendah supaya tes cepat; nilai bawaan produksi diperiksa di tes tersendiri.
    private val hasher = Pbkdf2PasswordHasher(iterations = 1_000)

    @Test
    fun `sandi yang benar cocok`() {
        assertTrue(hasher.verify("sandi-rahasia-1", hasher.hash("sandi-rahasia-1")))
    }

    @Test
    fun `sandi yang salah tidak cocok`() {
        val encoded = hasher.hash("sandi-rahasia-1")
        assertFalse(hasher.verify("sandi-rahasia-2", encoded))
        assertFalse(hasher.verify("", encoded))
        assertFalse(hasher.verify("Sandi-rahasia-1", encoded))
    }

    @Test
    fun `hash tidak memuat sandi asli`() {
        assertFalse("sandi-rahasia-1" in hasher.hash("sandi-rahasia-1"))
    }

    @Test
    fun `sandi yang sama menghasilkan hash berbeda karena garam acak`() {
        assertNotEquals(hasher.hash("sama"), hasher.hash("sama"))
    }

    @Test
    fun `format menyimpan algoritma dan jumlah putaran`() {
        assertTrue(hasher.hash("x").startsWith("pbkdf2-sha256\$1000\$"))
    }

    @Test
    fun `putaran bawaan mengikuti anjuran OWASP`() {
        assertEquals(600_000, Pbkdf2PasswordHasher.DEFAULT_ITERATIONS)
        assertContains(Pbkdf2PasswordHasher().hash("x"), "\$600000\$")
    }

    @Test
    fun `hash lama dengan putaran berbeda tetap bisa diverifikasi`() {
        val lama = Pbkdf2PasswordHasher(iterations = 2_000).hash("sandi-lama")
        assertTrue(hasher.verify("sandi-lama", lama))
    }

    @Test
    fun `bentuk unicode berbeda dari sandi yang sama tetap cocok`() {
        val komposisi = "café-sandi" // huruf e beraksen sebagai satu karakter
        val dekomposisi = "café-sandi" // huruf e diikuti tanda aksen
        assertTrue(hasher.verify(dekomposisi, hasher.hash(komposisi)))
    }

    @Test
    fun `teks hash rusak dianggap tidak cocok tanpa melempar galat`() {
        listOf(
            "", "bukan-hash", "pbkdf2-sha256\$1000\$abc", "md5\$1000\$AAAA\$AAAA",
            "pbkdf2-sha256\$abc\$AAAA\$AAAA", "pbkdf2-sha256\$10\$AAAA\$AAAA",
            "pbkdf2-sha256\$999999999\$AAAA\$AAAA", "pbkdf2-sha256\$1000\$!!!\$AAAA",
            "pbkdf2-sha256\$1000\$AAAA\$", "pbkdf2-sha256\$1000\$\$AAAA",
        ).forEach { assertFalse(hasher.verify("apa saja", it), it) }
    }

    @Test
    fun `jumlah putaran di luar batas ditolak`() {
        assertFailsWith<IllegalArgumentException> { Pbkdf2PasswordHasher(iterations = 10) }
        assertFailsWith<IllegalArgumentException> { Pbkdf2PasswordHasher(iterations = 50_000_000) }
    }
}
