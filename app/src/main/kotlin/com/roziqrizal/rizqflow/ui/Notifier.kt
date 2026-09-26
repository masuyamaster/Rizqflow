package com.roziqrizal.rizqflow.ui

/**
 * Pemberitahuan singkat (snackbar) yang bisa dipakai layar mana pun tanpa mengenal Scaffold-nya.
 * [show] baru kembali setelah snackbar hilang; hasilnya true bila pengguna menekan tombol aksi
 * (mis. Urungkan). Pemberitahuan baru menggantikan yang masih tampil.
 */
interface Notifier {
    suspend fun show(message: String, actionLabel: String? = null): Boolean
}
