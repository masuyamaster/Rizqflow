# Rizqflow — Masuk dengan Google dan Gmail

Ditulis 2026-09-21. Berkas ini menjelaskan apa yang sudah dibangun untuk halaman masuk, apa yang **harus Anda siapkan** di Google Cloud supaya tombol Google bekerja, dan risiko yang perlu diketahui sebelum rilis.

## Keputusan

Pemilik meminta alur: **splash, lalu halaman masuk dengan Google connect dan Gmail; yang sudah punya riwayat masuk langsung ke menu utama** (2026-09-21). Ini **mengubah keputusan sebelumnya** "tanpa akun" ([konsep.md](konsep.md)). Yang tetap berlaku: data keuangan disimpan di ponsel dan tidak dikirim ke server; tidak ada backend Rizqflow.

## Yang sudah dibangun

| Bagian | Isi |
|---|---|
| Splash | Splash sistem Android dengan ikon aplikasi dan latar token; tetap tampil sampai riwayat masuk terbaca, jadi tidak ada kedipan halaman masuk |
| S30 Masuk | Logo tiga ruang, tagline, tiga jaminan, tombol **Lanjutkan dengan Google**, tombol **Hubungkan Gmail** (opsional), pesan lembut bila gagal |
| Alur | Tanpa riwayat: halaman masuk. Ada riwayat: langsung menu utama. **Keluar** di tab Lainnya menghapus riwayat |
| Riwayat masuk | Profil singkat di SharedPreferences privat (pengenal, email, nama, penanda Gmail). **Tidak ada token atau sandi yang disimpan** |
| Gmail | Izin `gmail.readonly` diminta lewat AuthorizationClient, bisa sekalian saat masuk atau belakangan dari tab Lainnya. **Aplikasi belum membaca email apa pun**; fitur ini baru menyiapkan izinnya |
| Masuk uji | Tombol khusus **build debug** untuk melewati Google saat pengembangan. Tidak ada di build rilis |
| Tes | `AuthController` 16 tes, `StartRouter` dan sesi 5 tes |

Kode: `app/src/main/kotlin/.../auth/` (logika, penyimpanan, Google) dan `ui/login/` (layar); logika murni (`AuthSession`, `StartRouter`) di `:domain`.

## Yang harus Anda siapkan di Google Cloud

Tombol Google menampilkan "belum disiapkan" sampai ini selesai. Saya tidak bisa membuatnya untuk Anda karena butuh akun Google Anda.

1. Buka [console.cloud.google.com](https://console.cloud.google.com), buat proyek (misalnya `rizqflow`).
2. **APIs & Services, OAuth consent screen**: tipe **External**, isi nama aplikasi, email dukungan, dan tambahkan akun Anda sebagai **Test user**. Biarkan status **Testing**.
3. **Credentials, Create credentials, OAuth client ID**, dua buah:
   - Tipe **Web application** (nama bebas). Salin **Client ID**-nya, lalu tempel ke `app/src/main/res/values/google_auth.xml` pada `google_web_client_id`. Ini yang dipakai aplikasi.
   - Tipe **Android**: package `com.roziqrizal.rizqflow` dan SHA-1 kunci debug di bawah. Nanti tambahkan SHA-1 kunci rilis (dari Play Console, App signing).
4. Untuk Gmail: **Library, Gmail API, Enable**, lalu di consent screen tambahkan scope `.../auth/gmail.readonly`.
5. Uji di emulator: image `google_apis` memuat layanan Google, tetapi Anda tetap perlu menambahkan akun Google Anda di Pengaturan emulator. Bila Credential Manager menolak di emulator, uji di ponsel asli.

SHA-1 kunci debug di PC ini (`~/.android/debug.keystore`): `8A:63:D7:61:0E:42:F4:71:45:01:87:22:F0:42:A9:5A:6C:22:7F:95`. Untuk PC lain, jalankan:

```
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Client ID adalah pengenal publik, bukan rahasia, jadi aman masuk ke git.

## Risiko yang perlu diketahui sebelum rilis

1. **Izin baca Gmail adalah cakupan "restricted" di Google.** Selama proyek berstatus Testing, hanya sampai 100 pengguna uji yang terdaftar, dan izinnya kedaluwarsa tiap 7 hari. Untuk rilis publik, Google mewajibkan **verifikasi OAuth dan penilaian keamanan tahunan** oleh penilai pihak ketiga, yang butuh waktu dan biaya. Ini bisa menunda rilis pertama. Saran: rilis pertama **tanpa** tombol Gmail (sudah terpisah dari tombol Google), Gmail menyusul setelah verifikasi.
2. **Play Store**: Data safety dan kebijakan privasi harus menyebut akun Google dan, bila Gmail diaktifkan, akses email. Kebijakan Play tentang data pengguna sensitif berlaku ketat untuk aplikasi finansial.
3. **Identitas ini hanya lokal.** Tidak ada server Rizqflow, jadi ID token Google tidak diverifikasi siapa pun dan riwayat masuk hanyalah penanda di ponsel. Jangan dipakai untuk mengotorisasi Sync (fase 2) tanpa backend yang memverifikasi token.
4. **Butuh internet dan layanan Google** untuk masuk pertama kali. Ponsel tanpa Google Play services (misalnya sebagian perangkat Huawei) tidak bisa masuk.
5. **Prinsip produk**: janji "Datamu tetap di ponselmu" di halaman masuk tetap benar selama tidak ada sync. Ubah teksnya bila Sync dibuat.
6. **Tombol Google** sebaiknya mengikuti pedoman merek Google (logo G, warna) sebelum rilis; saat ini tombolnya teks biasa.

## Yang belum dibangun

- Membaca email (parser email bank, draf transaksi dari email), dan cara mencabut izin Gmail dari dalam aplikasi.
- Hapus akun dan data saat keluar (sekarang Keluar hanya menghapus riwayat masuk; data keuangan tidak disentuh).
- Menyambungkan akun ke Sync (fase 2).
