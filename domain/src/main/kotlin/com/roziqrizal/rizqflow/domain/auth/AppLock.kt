package com.roziqrizal.rizqflow.domain.auth

/** Kapan kunci otomatis diminta lagi setelah aplikasi ke latar belakang. */
enum class AutoLockMode(val millis: Long) {
    IMMEDIATELY(0L),
    AFTER_1_MINUTE(60_000L),
    AFTER_5_MINUTES(5 * 60_000L),
    AFTER_15_MINUTES(15 * 60_000L),
}

/**
 * Penyimpanan PIN (hanya hash-nya, tidak pernah PIN asli), biometrik, dan kunci otomatis.
 * Implementasinya di lapisan aplikasi (SharedPreferences). Berlaku untuk aplikasi, bukan satu
 * akun: satu PIN menutup akses ke aplikasi apa pun akun yang sedang masuk.
 */
interface SecurityStore {
    suspend fun pinHash(): String?

    suspend fun savePinHash(hash: String?)

    suspend fun biometricEnabled(): Boolean

    suspend fun saveBiometricEnabled(enabled: Boolean)

    suspend fun autoLockMode(): AutoLockMode

    suspend fun saveAutoLockMode(mode: AutoLockMode)

    suspend fun failedAttempts(): Int

    suspend fun saveFailedAttempts(count: Int)

    suspend fun lockedUntilMillis(): Long?

    suspend fun saveLockedUntilMillis(until: Long?)
}

sealed interface SetPinResult {
    data object Success : SetPinResult

    /** Bukan 6 angka. */
    data object InvalidFormat : SetPinResult
}

sealed interface UnlockResult {
    data object Success : UnlockResult

    data class WrongPin(val attemptsLeft: Int) : UnlockResult

    data class LockedOut(val untilMillis: Long) : UnlockResult
}

sealed interface SetBiometricResult {
    data object Success : SetBiometricResult

    /** Sidik jari hanya bisa aktif setelah ada PIN. */
    data object NoPinSet : SetBiometricResult
}

/**
 * Kunci aplikasi (S19): PIN 6 angka, biometrik, dan kunci otomatis. Lima kali PIN salah menahan
 * sementara ([LOCKOUT_MILLIS]); data tidak pernah dihapus. Menghapus PIN ikut mematikan
 * biometrik, karena biometrik tidak bisa berdiri sendiri tanpa PIN sebagai cadangan.
 */
class AppLockService(
    private val store: SecurityStore,
    private val hasher: PasswordHasher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun isPinSet(): Boolean = store.pinHash() != null

    suspend fun setPin(pin: String): SetPinResult {
        if (!isValidPinFormat(pin)) return SetPinResult.InvalidFormat
        store.savePinHash(hasher.hash(pin))
        store.saveFailedAttempts(0)
        store.saveLockedUntilMillis(null)
        return SetPinResult.Success
    }

    /** Mematikan kunci sepenuhnya: PIN dan biometrik. */
    suspend fun clearPin() {
        store.savePinHash(null)
        store.saveBiometricEnabled(false)
        store.saveFailedAttempts(0)
        store.saveLockedUntilMillis(null)
    }

    suspend fun verifyPin(pin: String): UnlockResult {
        val lockedUntil = store.lockedUntilMillis()
        if (lockedUntil != null && lockedUntil > nowMillis()) return UnlockResult.LockedOut(lockedUntil)

        val hash = store.pinHash() ?: return UnlockResult.Success
        if (hasher.verify(pin, hash)) {
            store.saveFailedAttempts(0)
            store.saveLockedUntilMillis(null)
            return UnlockResult.Success
        }

        val attempts = store.failedAttempts() + 1
        val attemptsLeft = MAX_ATTEMPTS - attempts
        return if (attemptsLeft <= 0) {
            val until = nowMillis() + LOCKOUT_MILLIS
            store.saveFailedAttempts(0)
            store.saveLockedUntilMillis(until)
            UnlockResult.LockedOut(until)
        } else {
            store.saveFailedAttempts(attempts)
            UnlockResult.WrongPin(attemptsLeft)
        }
    }

    suspend fun isBiometricEnabled(): Boolean = store.biometricEnabled()

    suspend fun setBiometricEnabled(enabled: Boolean): SetBiometricResult {
        if (enabled && !isPinSet()) return SetBiometricResult.NoPinSet
        store.saveBiometricEnabled(enabled)
        return SetBiometricResult.Success
    }

    suspend fun autoLockMode(): AutoLockMode = store.autoLockMode()

    suspend fun setAutoLockMode(mode: AutoLockMode) = store.saveAutoLockMode(mode)

    /**
     * Benar bila layar kunci harus tampil sekarang: tanpa PIN tidak pernah terkunci; start dingin
     * ([lastBackgroundedAtMillis] null, mis. proses baru) selalu terkunci; kalau tidak, dibandingkan
     * dengan jarak waktu kunci otomatis sejak aplikasi terakhir ke latar belakang.
     */
    suspend fun shouldLock(lastBackgroundedAtMillis: Long?): Boolean {
        if (!isPinSet()) return false
        if (lastBackgroundedAtMillis == null) return true
        return nowMillis() - lastBackgroundedAtMillis >= autoLockMode().millis
    }

    companion object {
        const val PIN_LENGTH = 6
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L

        fun isValidPinFormat(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all(Char::isDigit)
    }
}
