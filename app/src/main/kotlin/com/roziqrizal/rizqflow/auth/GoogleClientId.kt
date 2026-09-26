package com.roziqrizal.rizqflow.auth

/**
 * Pemeriksa bentuk Web client ID Google (`<angka>-<huruf/angka>.apps.googleusercontent.com`).
 * Hanya menangkap salah tempel (spasi, email, sisa tanda kutip, ID kosong); tidak bisa tahu apakah
 * ID itu benar-benar milik proyek Anda atau tipe Web, itu baru ketahuan saat Google menolak.
 */
object GoogleClientId {
    private val shape = Regex("[0-9]+-[a-z0-9]+\\.apps\\.googleusercontent\\.com")

    fun isValid(id: String): Boolean = shape.matches(id)

    /** Bentuk baku dari isi resource: spasi dan baris baru di tepi dibuang. */
    fun clean(raw: String): String = raw.trim()
}
