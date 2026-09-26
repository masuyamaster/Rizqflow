package com.roziqrizal.rizqflow.domain.backup

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BackupCryptoTest {
    // Garam dan IV acak per pemanggilan; tes memakai SecureRandom sungguhan tapi hanya
    // memeriksa hasil dekripsi, bukan byte acak itu sendiri.
    private val random = SecureRandom()

    @Test
    fun `bolak-balik dengan sandi yang benar mengembalikan data asli`() {
        val plaintext = "isi database contoh".toByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "sandi-cadangan", random)
        val decrypted = BackupCrypto.decrypt(encrypted, "sandi-cadangan")
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `berkas kosong tetap bolak-balik`() {
        val encrypted = BackupCrypto.encrypt(ByteArray(0), "sandi", random)
        assertContentEquals(ByteArray(0), BackupCrypto.decrypt(encrypted, "sandi"))
    }

    @Test
    fun `dua cadangan dari data yang sama menghasilkan berkas berbeda`() {
        val plaintext = "sama".toByteArray()
        val first = BackupCrypto.encrypt(plaintext, "sandi", random)
        val second = BackupCrypto.encrypt(plaintext, "sandi", random)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `sandi salah melempar galat, bukan data sampah`() {
        val encrypted = BackupCrypto.encrypt("rahasia".toByteArray(), "sandi-benar", random)
        assertFailsWith<BackupCorruptOrWrongPassword> { BackupCrypto.decrypt(encrypted, "sandi-salah") }
    }

    @Test
    fun `berkas rusak (byte diubah) melempar galat`() {
        val encrypted = BackupCrypto.encrypt("rahasia".toByteArray(), "sandi", random)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()
        assertFailsWith<BackupCorruptOrWrongPassword> { BackupCrypto.decrypt(encrypted, "sandi") }
    }

    @Test
    fun `berkas terlalu pendek atau bukan cadangan Rizqflow melempar galat`() {
        assertFailsWith<BackupCorruptOrWrongPassword> { BackupCrypto.decrypt(ByteArray(4), "sandi") }
        assertFailsWith<BackupCorruptOrWrongPassword> { BackupCrypto.decrypt("bukan cadangan rizqflow sama sekali".toByteArray(), "sandi") }
    }
}
