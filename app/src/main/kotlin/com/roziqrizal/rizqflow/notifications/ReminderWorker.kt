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
            if (settings.enabled) ReminderScheduler.scheduleNext(applicationContext, settings)
        }
        return Result.success()
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
