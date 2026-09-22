package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.ledger.RoomRepository
import com.roziqrizal.rizqflow.domain.ledger.SettingsRepository
import com.roziqrizal.rizqflow.domain.model.RoomId
import java.time.LocalDate

/** Satu ruang Memberi bermode zakat yang perlu disapa hari ini: haul genap atau mendekati genap. */
data class HaulReminder(val roomId: RoomId, val status: HaulStatus)

/**
 * Pemicu khusus haul di infrastruktur notifikasi malam (S26, Tahap 6): bukan sistem notifikasi
 * baru, hanya pengecekan tambahan yang menumpang jadwal harian yang sudah ada (lihat roadmap
 * Tahap 5 dan 6 — sengaja tidak diduplikasi). Menyapa sekali per ambang batas per ruang, bukan
 * tiap hari, lewat penanda tersimpan (`haul_reminder_notified_<ruang>`); begitu haul baru mulai
 * (tunaikan, atau reset di bawah nisab), jatuh temponya berubah sehingga ambang lama disapa lagi.
 */
class HaulReminderService(
    private val rooms: RoomRepository,
    private val zakat: ZakatService,
    private val settings: SettingsRepository,
) {
    suspend fun dueReminders(today: LocalDate): List<HaulReminder> {
        val candidates = rooms.activeRooms().filter { it.givingMode == ZakatHaulHijriStrategy.ID }
        return candidates.mapNotNull { room ->
            val status = (zakat.overview(room.id, today)?.status as? GivingStatus.Zakat)?.haul ?: return@mapNotNull null
            val marker = markerFor(status) ?: return@mapNotNull null
            if (settings.get(keyFor(room.id)) == marker) return@mapNotNull null
            HaulReminder(room.id, status)
        }
    }

    suspend fun markNotified(reminder: HaulReminder) {
        val marker = markerFor(reminder.status) ?: return
        settings.put(keyFor(reminder.roomId), marker)
    }

    private fun markerFor(status: HaulStatus): String? = when (status) {
        is HaulStatus.Completed -> "completed:${status.due}"
        is HaulStatus.Running -> if (status.daysLeft <= APPROACHING_THRESHOLD_DAYS) "approaching:${status.due}" else null
        HaulStatus.BelowNisab -> null
    }

    private fun keyFor(roomId: RoomId) = "$KEY_PREFIX${roomId.value}"

    companion object {
        const val APPROACHING_THRESHOLD_DAYS = 7L
        private const val KEY_PREFIX = "haul_reminder_notified_"
    }
}
