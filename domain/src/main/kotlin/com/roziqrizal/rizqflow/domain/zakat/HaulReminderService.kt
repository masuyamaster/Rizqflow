package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.ledger.RoomRepository
import com.roziqrizal.rizqflow.domain.ledger.SettingsRepository
import com.roziqrizal.rizqflow.domain.model.RoomId
import java.time.LocalDate

/**
 * Satu profil harta di ruang Memberi bermode zakat yang perlu disapa hari ini: haul genap atau
 * mendekati genap. [showProfileName] benar bila ada lebih dari satu profil aktif, jadi notifikasi
 * perlu menyebut profil mana yang dimaksud.
 */
data class HaulReminder(
    val roomId: RoomId,
    val profileId: String,
    val profileName: String,
    val showProfileName: Boolean,
    val status: HaulStatus,
)

/**
 * Pemicu khusus haul di infrastruktur notifikasi malam (S26, Tahap 6): bukan sistem notifikasi
 * baru, hanya pengecekan tambahan yang menumpang jadwal harian yang sudah ada (lihat roadmap
 * Tahap 5 dan 6 — sengaja tidak diduplikasi). Menyapa sekali per ambang batas per ruang **dan per
 * profil harta**, bukan tiap hari, lewat penanda tersimpan (`haul_reminder_notified_<ruang>_<profil>`);
 * begitu haul baru mulai (tunaikan, atau reset di bawah nisab), jatuh temponya berubah sehingga ambang
 * lama disapa lagi. Profil yang diarsipkan tidak disapa. Tanpa Pro (belum atau tidak lagi), hanya
 * profil pertama yang disapa; profil lain tetap tersimpan dan bisa dipakai, hanya pengingatnya diam.
 */
class HaulReminderService(
    private val rooms: RoomRepository,
    private val zakat: ZakatService,
    private val settings: SettingsRepository,
) {
    suspend fun dueReminders(today: LocalDate): List<HaulReminder> {
        val candidates = rooms.activeRooms().filter { it.givingMode == ZakatHaulHijriStrategy.ID }
        val due = mutableListOf<HaulReminder>()
        for (room in candidates) {
            val first = zakat.overview(room.id, today) ?: continue
            // Pengingat untuk banyak profil adalah Pro; tanpa Pro hanya profil pertama yang disapa (docs/monetisasi.md).
            val reminded = if (zakat.multiProfileEnabled()) first.profiles else first.profiles.take(1)
            for (profile in reminded) {
                val overview = if (profile.id == first.profile?.id) first else zakat.overview(room.id, today, profile.id) ?: continue
                val status = (overview.status as? GivingStatus.Zakat)?.haul ?: continue
                val marker = markerFor(status) ?: continue
                if (settings.get(keyFor(room.id, profile.id)) == marker) continue
                due += HaulReminder(room.id, profile.id, profile.name, first.profiles.size > 1, status)
            }
        }
        return due
    }

    suspend fun markNotified(reminder: HaulReminder) {
        val marker = markerFor(reminder.status) ?: return
        settings.put(keyFor(reminder.roomId, reminder.profileId), marker)
    }

    private fun markerFor(status: HaulStatus): String? = when (status) {
        is HaulStatus.Completed -> "completed:${status.due}"
        is HaulStatus.Running -> if (status.daysLeft <= APPROACHING_THRESHOLD_DAYS) "approaching:${status.due}" else null
        HaulStatus.BelowNisab -> null
    }

    private fun keyFor(roomId: RoomId, profileId: String) = "$KEY_PREFIX${roomId.value}_$profileId"

    companion object {
        const val APPROACHING_THRESHOLD_DAYS = 7L
        private const val KEY_PREFIX = "haul_reminder_notified_"
    }
}
