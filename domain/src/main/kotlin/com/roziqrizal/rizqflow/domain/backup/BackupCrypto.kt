package com.roziqrizal.rizqflow.domain.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Sandi salah, atau berkas cadangan rusak atau bukan berkas Rizqflow (S20). Dilempar tanpa membedakan penyebabnya: pesannya sengaja tidak spesifik supaya tidak membantu tebakan sandi. */
class BackupCorruptOrWrongPassword : Exception("Sandi salah atau berkas rusak")

/**
 * Mengenkripsi dan mendekripsi berkas cadangan (F7, S20). AES-256-GCM dengan kunci turunan
 * PBKDF2-HMAC-SHA256 dari sandi pengguna (garam acak per cadangan) — pola turunan kunci yang sama
 * dengan [com.roziqrizal.rizqflow.domain.auth.Pbkdf2PasswordHasher], tapi hasilnya kunci AES,
 * bukan hash untuk disimpan.
 *
 * Bentuk berkas: `MAGIC (6 byte) | garam (16 byte) | IV (12 byte) | teks sandi+tag GCM`.
 */
object BackupCrypto {
    private val MAGIC = "RZQBK1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 300_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128

    fun encrypt(plaintext: ByteArray, password: String, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return MAGIC + salt + iv + cipher.doFinal(plaintext)
    }

    /** @throws BackupCorruptOrWrongPassword bila sandi salah, berkas rusak, atau bukan cadangan Rizqflow. */
    fun decrypt(data: ByteArray, password: String): ByteArray {
        val headerSize = MAGIC.size + SALT_BYTES + IV_BYTES
        if (data.size <= headerSize || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupCorruptOrWrongPassword()
        }
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = data.copyOfRange(MAGIC.size + SALT_BYTES, headerSize)
        val ciphertext = data.copyOfRange(headerSize, data.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw BackupCorruptOrWrongPassword()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val chars = Normalizer.normalize(password, Normalizer.Form.NFKC).toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_BYTES * 8)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
            chars.fill(' ')
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
