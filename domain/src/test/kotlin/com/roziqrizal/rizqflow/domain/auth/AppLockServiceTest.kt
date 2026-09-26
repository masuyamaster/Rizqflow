package com.roziqrizal.rizqflow.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppLockServiceTest {

    private class MemorySecurityStore : SecurityStore {
        var pin: String? = null
        var biometric = false
        var mode = AutoLockMode.IMMEDIATELY
        var attempts = 0
        var lockedUntil: Long? = null

        override suspend fun pinHash() = pin

        override suspend fun savePinHash(hash: String?) {
            pin = hash
        }

        override suspend fun biometricEnabled() = biometric

        override suspend fun saveBiometricEnabled(enabled: Boolean) {
            biometric = enabled
        }

        override suspend fun autoLockMode() = mode

        override suspend fun saveAutoLockMode(mode: AutoLockMode) {
            this.mode = mode
        }

        override suspend fun failedAttempts() = attempts

        override suspend fun saveFailedAttempts(count: Int) {
            attempts = count
        }

        override suspend fun lockedUntilMillis() = lockedUntil

        override suspend fun saveLockedUntilMillis(until: Long?) {
            lockedUntil = until
        }
    }

    /** Pengolah tiruan: cepat, tanpa PBKDF2 sungguhan. */
    private class FakeHasher : PasswordHasher {
        override fun hash(password: String) = "hash($password)"

        override fun verify(password: String, encoded: String) = encoded == "hash($password)"
    }

    private val store = MemorySecurityStore()
    private var now = 1_000_000L
    private val service = AppLockService(store, FakeHasher()) { now }

    // ------------------------------------------------------------------ atur PIN

    @Test
    fun `belum ada PIN secara bawaan`() {
        assertFalse(runSuspend { service.isPinSet() })
    }

    @Test
    fun `PIN 6 angka tersimpan sebagai hash`() {
        val hasil = runSuspend { service.setPin("123456") }

        assertEquals(SetPinResult.Success, hasil)
        assertTrue(runSuspend { service.isPinSet() })
        assertEquals("hash(123456)", store.pin)
    }

    @Test
    fun `PIN bukan 6 angka ditolak`() {
        assertEquals(SetPinResult.InvalidFormat, runSuspend { service.setPin("12345") })
        assertEquals(SetPinResult.InvalidFormat, runSuspend { service.setPin("1234567") })
        assertEquals(SetPinResult.InvalidFormat, runSuspend { service.setPin("12a456") })
        assertFalse(runSuspend { service.isPinSet() })
    }

    @Test
    fun `menghapus PIN juga mematikan biometrik`() {
        runSuspend { service.setPin("123456") }
        runSuspend { service.setBiometricEnabled(true) }

        runSuspend { service.clearPin() }

        assertFalse(runSuspend { service.isPinSet() })
        assertFalse(runSuspend { service.isBiometricEnabled() })
    }

    // ------------------------------------------------------------------ verifikasi PIN

    @Test
    fun `PIN benar berhasil dan mengembalikan percobaan gagal`() {
        runSuspend { service.setPin("123456") }
        store.attempts = 3

        val hasil = runSuspend { service.verifyPin("123456") }

        assertEquals(UnlockResult.Success, hasil)
        assertEquals(0, store.attempts)
    }

    @Test
    fun `PIN salah mengurangi percobaan tersisa`() {
        runSuspend { service.setPin("123456") }

        val hasil = runSuspend { service.verifyPin("000000") }

        assertEquals(UnlockResult.WrongPin(4), hasil)
        assertEquals(1, store.attempts)
    }

    @Test
    fun `lima kali salah menahan sementara tanpa menghapus PIN`() {
        runSuspend { service.setPin("123456") }

        repeat(4) { runSuspend { service.verifyPin("000000") } }
        val hasil = runSuspend { service.verifyPin("000000") }

        assertIs<UnlockResult.LockedOut>(hasil)
        assertEquals(now + AppLockService.LOCKOUT_MILLIS, hasil.untilMillis)
        assertTrue(runSuspend { service.isPinSet() }, "PIN tidak boleh terhapus setelah menahan sementara")
    }

    @Test
    fun `terkunci sementara menolak PIN benar sekalipun sebelum waktunya habis`() {
        runSuspend { service.setPin("123456") }
        repeat(5) { runSuspend { service.verifyPin("000000") } }

        val hasil = runSuspend { service.verifyPin("123456") }

        assertIs<UnlockResult.LockedOut>(hasil)
    }

    @Test
    fun `PIN benar berhasil lagi setelah waktu tahan lewat`() {
        runSuspend { service.setPin("123456") }
        repeat(5) { runSuspend { service.verifyPin("000000") } }
        now += AppLockService.LOCKOUT_MILLIS

        val hasil = runSuspend { service.verifyPin("123456") }

        assertEquals(UnlockResult.Success, hasil)
    }

    // ------------------------------------------------------------------ biometrik

    @Test
    fun `biometrik tidak bisa aktif tanpa PIN`() {
        val hasil = runSuspend { service.setBiometricEnabled(true) }

        assertEquals(SetBiometricResult.NoPinSet, hasil)
        assertFalse(runSuspend { service.isBiometricEnabled() })
    }

    @Test
    fun `biometrik bisa aktif setelah ada PIN`() {
        runSuspend { service.setPin("123456") }

        val hasil = runSuspend { service.setBiometricEnabled(true) }

        assertEquals(SetBiometricResult.Success, hasil)
        assertTrue(runSuspend { service.isBiometricEnabled() })
    }

    // ------------------------------------------------------------------ kapan layar kunci tampil

    @Test
    fun `tanpa PIN tidak pernah terkunci`() {
        assertFalse(runSuspend { service.shouldLock(null) })
        assertFalse(runSuspend { service.shouldLock(now - 1) })
    }

    @Test
    fun `start dingin selalu terkunci bila ada PIN`() {
        runSuspend { service.setPin("123456") }

        assertTrue(runSuspend { service.shouldLock(null) })
    }

    @Test
    fun `mode segera terkunci begitu kembali dari latar belakang`() {
        runSuspend { service.setPin("123456") }
        runSuspend { service.setAutoLockMode(AutoLockMode.IMMEDIATELY) }

        assertTrue(runSuspend { service.shouldLock(now) })
    }

    @Test
    fun `mode setelah 1 menit tidak terkunci sebelum jarak waktunya lewat`() {
        runSuspend { service.setPin("123456") }
        runSuspend { service.setAutoLockMode(AutoLockMode.AFTER_1_MINUTE) }
        val backgroundedAt = now

        now += 30_000L
        assertFalse(runSuspend { service.shouldLock(backgroundedAt) })

        now = backgroundedAt + AutoLockMode.AFTER_1_MINUTE.millis
        assertTrue(runSuspend { service.shouldLock(backgroundedAt) })
    }
}
