package com.roziqrizal.rizqflow.backup

import android.content.Context

/**
 * Kapan cadangan terakhir dibuat di perangkat ini (S20, untuk baris "Cadangan terakhir"). Bukan
 * bagian data akun: tidak ikut tercadangkan atau terpulihkan oleh cadangan itu sendiri.
 */
class BackupPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("backup", Context.MODE_PRIVATE)

    var lastBackupAtMillis: Long?
        get() = prefs.getLong(KEY_LAST_BACKUP, -1L).takeIf { it >= 0 }
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_LAST_BACKUP) else putLong(KEY_LAST_BACKUP, value) }.apply()
        }

    /** Pintasan folder cadangan (Tahap 10): URI folder SAF yang dipilih, atau null bila belum diatur. */
    var shortcutFolderUri: String?
        get() = prefs.getString(KEY_SHORTCUT_FOLDER, null)
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_SHORTCUT_FOLDER) else putString(KEY_SHORTCUT_FOLDER, value) }.apply()
        }

    private companion object {
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_SHORTCUT_FOLDER = "backup_shortcut_folder"
    }
}
