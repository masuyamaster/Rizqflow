package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.LocalTime

/** Pengaturan pengingat malam (S26): jam pengingat dan aktif atau tidak. Aktif secara bawaan (keputusan produk). */
data class ReminderSettings(val enabled: Boolean, val hour: Int, val minute: Int) {
    val time: LocalTime get() = LocalTime.of(hour, minute)

    companion object {
        val DEFAULT = ReminderSettings(enabled = true, hour = 20, minute = 0)
    }
}

/** Balasan notifikasi malam semacam "kopi 25000" diuraikan jadi catatan dan nominal. */
data class ParsedQuickReply(val note: String, val amount: Money)

/**
 * Pengingat malam (S26): jam pengingat, hari yang ditandai "Tidak ada" tanpa perlu mencatat apa
 * pun, dan mencatat balasan notifikasi semacam "kopi 25000" sebagai pengeluaran. Ruang dan kategori
 * mengikuti pengeluaran terakhir (dasar yang sama dengan S24 Catat kilat); tanpa pengeluaran
 * sebelumnya, ruang bertipe Mencukupi (dasar yang sama dengan S25 Koreksi saldo). Tidak tahu apa
 * pun soal Android: kapan dan bagaimana notifikasi ditampilkan adalah urusan lapisan app.
 *
 * Juga memutuskan petunjuk hari kosong di S08 (Tahap 6): keduanya berbagi penanda hari yang sama
 * (`day_check`), hanya jalan masuknya beda — notifikasi malam untuk hari ini, petunjuk S08 untuk
 * kemarin.
 */
class ReminderService(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val rooms: RoomRepository,
    private val transactions: TransactionRepository,
    private val ledger: LedgerService,
) {
    suspend fun settings(): ReminderSettings {
        val enabled = settings.get(KEY_ENABLED)?.toBooleanStrictOrNull() ?: ReminderSettings.DEFAULT.enabled
        val hour = settings.get(KEY_HOUR)?.toIntOrNull() ?: ReminderSettings.DEFAULT.hour
        val minute = settings.get(KEY_MINUTE)?.toIntOrNull() ?: ReminderSettings.DEFAULT.minute
        return ReminderSettings(enabled, hour, minute)
    }

    suspend fun updateSettings(enabled: Boolean, hour: Int, minute: Int): LedgerResult<Unit> {
        if (hour !in 0..23 || minute !in 0..59) return failure(LedgerError.INVALID_REMINDER_TIME)
        settings.put(KEY_ENABLED, enabled.toString())
        settings.put(KEY_HOUR, hour.toString())
        settings.put(KEY_MINUTE, minute.toString())
        return LedgerResult.Success(Unit)
    }

    suspend fun isTodayDismissed(today: LocalDate): Boolean = settings.isDayChecked(today)

    /** Tombol "Tidak ada": menandai hari ini sudah dicek tanpa mencatat apa pun. */
    suspend fun dismissToday(today: LocalDate) = settings.markDayChecked(today)

    /**
     * Petunjuk hari kosong (S08): benar bila kemarin tidak ada pengeluaran tercatat dan kemarin
     * belum ditandai "Tidak ada" — dari notifikasi malam atau dari petunjuk ini sendiri, keduanya
     * memakai penanda hari yang sama. Tidak pernah tampil sebelum transaksi pertama, supaya akun
     * baru tidak langsung disapa petunjuk.
     */
    suspend fun shouldShowEmptyDayHint(today: LocalDate): Boolean {
        if (transactions.latest(null) == null) return false
        val yesterday = today.minusDays(1)
        if (settings.isDayChecked(yesterday)) return false
        return transactions.between(yesterday, yesterday).none { it.kind == TransactionKind.EXPENSE }
    }

    /** Tombol "Tidak ada" pada petunjuk hari kosong (S08): menandai KEMARIN sudah dicek. */
    suspend fun dismissEmptyDayHint(today: LocalDate) = settings.markDayChecked(today.minusDays(1))

    /**
     * Izin notifikasi Android 13+ diminta sekali, setelah transaksi pertama disimpan (bukan di
     * awal onboarding). Bendera ini mencegah layar memintanya berulang-ulang.
     */
    suspend fun hasRequestedNotificationPermission(): Boolean = settings.get(KEY_PERMISSION_ASKED) == "true"

    suspend fun markNotificationPermissionRequested() = settings.put(KEY_PERMISSION_ASKED, "true")

    /**
     * "kopi 25000" -> catatan "kopi", nominal 25000; titik pemisah ribuan boleh dipakai. Null bila
     * tidak berbentuk "catatan nominal" (tanpa catatan, tanpa nominal, atau nominal nol/negatif).
     */
    fun parseQuickReply(text: String): ParsedQuickReply? {
        val match = QUICK_REPLY_PATTERN.find(text.trim()) ?: return null
        val note = match.groupValues[1].trim()
        val amount = match.groupValues[2].replace(".", "").toLongOrNull()?.takeIf { it > 0 } ?: return null
        return ParsedQuickReply(note, Money.rupiah(amount))
    }

    /** Mencatat balasan notifikasi sebagai pengeluaran; menandai hari itu sudah dicek juga. */
    suspend fun recordQuickReply(text: String, today: LocalDate): LedgerResult<TransactionId> {
        val parsed = parseQuickReply(text) ?: return failure(LedgerError.QUICK_REPLY_UNREADABLE)
        val accountId = transactions.latest(null)?.accountId
            ?: accounts.activeAccounts().firstOrNull()?.id
            ?: return failure(LedgerError.ACCOUNT_NOT_FOUND)
        val lastExpense = transactions.latest(TransactionKind.EXPENSE)
        val active = rooms.activeRooms()
        val roomId = lastExpense?.roomId
            ?: active.firstOrNull { it.kind == RoomKind.MENCUKUPI }?.id
            ?: active.firstOrNull()?.id
            ?: return failure(LedgerError.ROOM_NOT_FOUND)
        val categoryId = lastExpense?.categoryId?.takeIf { rooms.findCategory(it)?.roomId == roomId }
            ?: rooms.categories(roomId).firstOrNull { !it.isSystem }?.id
            ?: return failure(LedgerError.CATEGORY_NOT_FOUND)

        return when (val result = ledger.recordExpense(NewExpense(parsed.amount, accountId, roomId, categoryId, today, note = parsed.note))) {
            is LedgerResult.Success -> {
                settings.markDayChecked(today)
                LedgerResult.Success(result.value.id)
            }

            is LedgerResult.Failure -> result
        }
    }

    companion object {
        private const val KEY_ENABLED = "reminder_enabled"
        private const val KEY_HOUR = "reminder_hour"
        private const val KEY_MINUTE = "reminder_minute"
        private const val KEY_PERMISSION_ASKED = "reminder_permission_asked"
        private val QUICK_REPLY_PATTERN = Regex("""^(.+?)\s+([\d.]+)$""")
    }
}
