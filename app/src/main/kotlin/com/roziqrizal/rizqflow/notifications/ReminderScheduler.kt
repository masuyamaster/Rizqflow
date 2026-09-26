package com.roziqrizal.rizqflow.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.roziqrizal.rizqflow.domain.ledger.ReminderSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Menjadwalkan pengingat malam (S26) lewat WorkManager: satu pekerjaan yang, saat berjalan,
 * menjadwalkan lagi pekerjaan berikutnya untuk besok (rantai harian). WorkManager sendiri yang
 * menjaga jadwal ini tetap hidup lewat reboot perangkat.
 */
object ReminderScheduler {
    const val WORK_NAME = "reminder-daily"

    fun scheduleNext(context: Context, settings: ReminderSettings, from: LocalDateTime = LocalDateTime.now()) {
        if (!settings.enabled) {
            cancel(context)
            return
        }
        val delay = delayUntil(settings.time, from)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Jarak waktu ke kemunculan [time] berikutnya dari [from]: hari ini bila belum lewat, kalau tidak besok. */
    internal fun delayUntil(time: LocalTime, from: LocalDateTime): Duration {
        var next = from.toLocalDate().atTime(time)
        if (!next.isAfter(from)) next = next.plusDays(1)
        return Duration.between(from, next)
    }
}
