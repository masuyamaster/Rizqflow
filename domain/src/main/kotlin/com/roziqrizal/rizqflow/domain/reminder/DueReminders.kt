package com.roziqrizal.rizqflow.domain.reminder

import com.roziqrizal.rizqflow.domain.ledger.SettingsRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Tahap pengingat satu jatuh tempo; urutannya menentukan apakah sapaan baru perlu (hanya naik, tidak pernah turun). */
enum class DueStage { APPROACHING, DUE_TODAY, OVERDUE }

/**
 * Aturan sapaan jatuh tempo yang dipakai bersama tagihan (`BillReminderService`) dan utang-piutang
 * (`DebtReminderService`): tiga hari sebelumnya, pada harinya, dan sekali saat terlambat, lalu diam.
 * Penanda tersimpan di pengaturan (`<awalan><pengenal>` = `<jatuh tempo>:<tahap>`) terikat pada
 * jatuh temponya, jadi jatuh tempo yang maju atau diubah memulai daur baru tanpa menghapus apa pun,
 * dan tahap yang sudah terlewat tidak disapa mundur. Penanda yang rusak dianggap belum pernah disapa.
 */
object DueReminders {
    /** Jatuh tempo dalam sekian hari ke depan sudah dianggap dekat. */
    const val SOON_DAYS = 3L

    /** Sisa hari (negatif bila terlambat) dan tahapnya; null bila jatuh tempo masih jauh. */
    fun stageOf(today: LocalDate, due: LocalDate): Pair<DueStage, Int>? {
        val daysLeft = ChronoUnit.DAYS.between(today, due).toInt()
        val stage = when {
            daysLeft < 0 -> DueStage.OVERDUE
            daysLeft == 0 -> DueStage.DUE_TODAY
            daysLeft <= SOON_DAYS -> DueStage.APPROACHING
            else -> return null
        }
        return stage to daysLeft
    }

    suspend fun alreadyNotified(settings: SettingsRepository, key: String, due: LocalDate, stage: DueStage): Boolean {
        val (savedDue, savedStage) = settings.get(key)?.split(':')?.takeIf { it.size == 2 }?.let { it[0].toLongOrNull() to it[1].toIntOrNull() } ?: return false
        return savedDue == due.toEpochDay() && savedStage != null && savedStage >= stage.ordinal
    }

    suspend fun markNotified(settings: SettingsRepository, key: String, due: LocalDate, stage: DueStage) {
        settings.put(key, "${due.toEpochDay()}:${stage.ordinal}")
    }
}
