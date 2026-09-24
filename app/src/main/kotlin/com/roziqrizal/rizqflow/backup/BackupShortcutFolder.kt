package com.roziqrizal.rizqflow.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Pintasan folder cadangan (S20, Tahap 10, Gratis): folder yang dipilih sekali lewat Storage Access
 * Framework, lalu dipakai lagi setiap Cadangkan tanpa membuka dialog "Simpan sebagai" berulang
 * kali. Tanpa API Google Drive: SAF bekerja dengan penyedia dokumen mana pun yang terpasang di
 * perangkat (Drive yang sudah masuk akun tampil sebagai salah satu pilihan folder), jadi tidak ada
 * OAuth atau kredensial tambahan yang disimpan aplikasi.
 */
object BackupShortcutFolder {

    /** Menyimpan izin baca-tulis folder secara permanen (bertahan lewat perangkat dinyalakan ulang). */
    fun persist(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    /** Melepas izin yang tersimpan; dipanggil sebelum mengganti atau menghapus pintasan. Aman dipanggil walau izinnya sudah tidak ada. */
    fun release(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** Nama folder untuk ditampilkan; null bila izinnya sudah tidak berlaku (folder dihapus, atau dicabut dari luar aplikasi). */
    fun displayName(context: Context, treeUri: Uri): String? =
        runCatching { DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.isDirectory && it.canWrite() }?.name }.getOrNull()

    /** Membuat berkas baru [name] di dalam folder dan menuliskan [bytes]; false bila gagal (folder tidak lagi bisa ditulis). */
    fun write(context: Context, treeUri: Uri, name: String, bytes: ByteArray): Boolean = runCatching {
        val folder = DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.isDirectory && it.canWrite() } ?: return false
        val file = folder.createFile("application/octet-stream", name) ?: return false
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) } != null
    }.getOrDefault(false)
}
