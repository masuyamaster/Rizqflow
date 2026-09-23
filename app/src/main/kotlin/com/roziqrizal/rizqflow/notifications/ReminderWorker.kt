package com.roziqrizal.rizqflow.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.roziqrizal.rizqflow.auth.SharedPrefsSessionStore
import com.roziqrizal.rizqflow.domain.auth.AccountStorage
import com.roziqrizal.rizqflow.workspace.AccountWorkspace
import java.time.LocalDate

/**
 * Sekali sehari: membuka ruang kerja akun yang sedang masuk, menampilkan notifikasi bila
 * pengingat aktif dan hari ini belum ditandai "Tidak ada", lalu menjadwalkan lagi untuk besok.
 * Tidak melakukan apa pun bila belum ada akun yang masuk, atau sedang di mode demo.
 *
 * Pengingat haul (Tahap 6) menumpang di sini juga, bukan worker terpisah: setiap kali pekerjaan
 * ini berjalan, ruang Memberi bermode zakat dicek juga (lihat `HaulReminderService`). Efeknya,
 * pengingat haul hanya jalan bila pengingat malam aktif — sengaja, supaya tidak menduplikasi
 * infrastruktur penjadwalan yang sudah ada (lihat roadmap Tahap 6). Jadwal DCA (sistem per peran,
 * Pro) menumpang dengan cara dan batasan yang sama (lihat `DcaReminderService`).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val accountId = SharedPrefsSessionStore(applicationContext).current()?.accountId ?: return Result.success()
        if (accountId == AccountStorage.DEMO_ACCOUNT_ID) return Result.success()

        AccountWorkspace.open(applicationContext, accountId).use { workspace ->
            val settings = workspace.reminder.settings()
            val today = LocalDate.now()
            if (settings.enabled && !workspace.reminder.isTodayDismissed(today) && hasPermission()) {
                ReminderNotifier.show(applicationContext, accountId)
            }
            if (settings.enabled && hasPermission()) {
                workspace.haulReminder.dueReminders(today).forEach { reminder ->
                    ReminderNotifier.showHaul(applicationContext, reminder.roomId.value, reminder.profileId, reminder.profileName.takeIf { reminder.showProfileName }, reminder.status)
                    workspace.haulReminder.markNotified(reminder)
                }
                workspace.dcaReminder.dueReminders(today).forEach { reminder ->
                    val roomName = workspace.repositories.rooms.find(reminder.roomId)?.name.orEmpty()
                    ReminderNotifier.showDca(applicationContext, reminder.roomId.value, roomName, reminder.view)
                    workspace.dcaReminder.markNotified(reminder)
                }
            }
            if (settings.enabled) ReminderScheduler.scheduleNext(applicationContext, settings)
        }
        return Result.success()
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
