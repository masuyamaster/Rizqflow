package com.roziqrizal.rizqflow.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Menerima ketukan aksi notifikasi pengingat malam (balasan kilat dan "Tidak ada") dan langsung
 * meneruskannya ke [ReminderActionWorker] lewat WorkManager; tidak melakukan pekerjaan Room di sini
 * sendiri (lihat catatan di [ReminderActionWorker]).
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(ReminderNotifier.EXTRA_ACCOUNT_ID) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ReminderNotifier.REMOTE_INPUT_KEY)?.toString()

        val request = OneTimeWorkRequestBuilder<ReminderActionWorker>()
            .setInputData(
                workDataOf(
                    ReminderActionWorker.KEY_ACCOUNT_ID to accountId,
                    ReminderActionWorker.KEY_ACTION to intent.action,
                    ReminderActionWorker.KEY_REPLY_TEXT to text,
                ),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }
}
