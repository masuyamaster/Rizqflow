package com.roziqrizal.rizqflow.domain.role

import com.roziqrizal.rizqflow.domain.ledger.SettingsRepository
import com.roziqrizal.rizqflow.domain.model.RoomId
import java.time.LocalDate
import java.time.YearMonth

/** Satu jadwal DCA yang perlu disapa hari ini: tanggalnya tiba dan nominal bulan ini belum tercatat. */
data class DcaReminder(val roomId: RoomId, val view: DcaView, val month: YearMonth)

/**
 * Pengingat jadwal DCA yang menumpang jadwal harian pengingat malam (S26), seperti
 * `HaulReminderService`: bukan penjadwal baru. Menyapa sekali per bulan per ruang lewat penanda
 * tersimpan (`dca_reminder_notified_<ruang>` = bulannya), jadi tidak berulang tiap hari; mencatat
 * DCA-nya tidak perlu menghapus penanda karena `dueDca` sendiri berhenti menyebut jadwal yang selesai.
 */
class DcaReminderService(
    private val roles: RoleService,
    private val settings: SettingsRepository,
) {
    suspend fun dueReminders(today: LocalDate): List<DcaReminder> {
        val month = YearMonth.from(today)
        return roles.dueDca(today).mapNotNull { view ->
            val roomId = view.plan.roomId
            if (settings.get(keyFor(roomId)) == month.toString()) return@mapNotNull null
            DcaReminder(roomId, view, month)
        }
    }

    suspend fun markNotified(reminder: DcaReminder) {
        settings.put(keyFor(reminder.roomId), reminder.month.toString())
    }

    private fun keyFor(roomId: RoomId) = "$KEY_PREFIX${roomId.value}"

    private companion object {
        const val KEY_PREFIX = "dca_reminder_notified_"
    }
}
