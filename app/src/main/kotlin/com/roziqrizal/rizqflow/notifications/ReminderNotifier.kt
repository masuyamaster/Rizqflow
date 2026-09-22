package com.roziqrizal.rizqflow.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.roziqrizal.rizqflow.MainActivity
import com.roziqrizal.rizqflow.R

/** Menyusun dan menampilkan notifikasi pengingat malam (S26) beserta aksi Balas dan Tidak ada. */
object ReminderNotifier {
    const val CHANNEL_ID = "reminder_daily"
    const val NOTIFICATION_ID = 2601
    const val EXTRA_ACCOUNT_ID = "account_id"
    const val ACTION_REPLY = "com.roziqrizal.rizqflow.action.REMINDER_REPLY"
    const val ACTION_DISMISS = "com.roziqrizal.rizqflow.action.REMINDER_DISMISS"
    const val REMOTE_INPUT_KEY = "reminder_reply"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.reminder_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, accountId: String) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val openIntent = openIntent(context)
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel(context.getString(R.string.reminder_reply_hint)).build()
        val replyIntent = Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_REPLY).putExtra(EXTRA_ACCOUNT_ID, accountId)
        val replyPending = PendingIntent.getBroadcast(context, 1, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val replyAction = NotificationCompat.Action.Builder(0, context.getString(R.string.reminder_reply_action), replyPending)
            .addRemoteInput(remoteInput)
            .build()

        val dismissIntent = Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ACCOUNT_ID, accountId)
        val dismissPending = PendingIntent.getBroadcast(context, 2, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismissAction = NotificationCompat.Action.Builder(0, context.getString(R.string.reminder_dismiss_action), dismissPending).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_catat)
            .setContentTitle(context.getString(R.string.reminder_notif_title))
            .setContentText(context.getString(R.string.reminder_notif_body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(replyAction)
            .addAction(dismissAction)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Menimpa notifikasi yang sama dengan versi tanpa aksi setelah balasan berhasil dicatat.
     * `cancel()` biasa tidak cukup: sistem menahan notifikasi berbalasan langsung (flag
     * `LIFETIME_EXTENDED_BY_DIRECT_REPLY`) sampai kontennya diperbarui, bukan sekadar dibatalkan.
     */
    fun showReplySaved(context: Context, message: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_catat)
            .setContentTitle(message)
            .setContentIntent(openIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
