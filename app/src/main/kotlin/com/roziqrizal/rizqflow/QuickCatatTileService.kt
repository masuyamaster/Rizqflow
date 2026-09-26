package com.roziqrizal.rizqflow

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tindakan yang membuka Catat kilat (S24): dari pintasan ikon, tile Quick Settings, atau notifikasi. */
const val ACTION_QUICK_CATAT = "com.roziqrizal.rizqflow.QUICK_CATAT"

/**
 * Tile Quick Settings "Catat kilat". Mengetuknya menutup panel dan membuka aplikasi langsung di
 * sheet Catat kilat. Aplikasi yang belum masuk menampilkan halaman masuk lebih dulu; sheet terbuka
 * setelah masuk.
 */
class QuickCatatTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_QUICK_CATAT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
