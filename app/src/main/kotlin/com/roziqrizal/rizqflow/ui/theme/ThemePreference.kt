package com.roziqrizal.rizqflow.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pilihan tema layar Tampilan: ikuti sistem, atau paksa terang/gelap terlepas dari sistem. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Menyimpan pilihan tema di SharedPreferences privat aplikasi, terpisah dari riwayat masuk dan
 * akun: berlaku untuk aplikasi, bukan satu akun, dan bertahan lewat Keluar. `allowBackup` mati,
 * jadi berkas ini tidak ikut cadangan otomatis.
 */
class ThemePreference(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    private fun readMode(): ThemeMode =
        prefs.getString(KEY_MODE, null)?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: ThemeMode.SYSTEM

    private companion object {
        const val KEY_MODE = "theme_mode"
    }
}
