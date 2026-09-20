# Rizqflow

Aplikasi finansial berbasis "hak": rezeki yang masuk dialirkan ke ruang-ruang (Memberi, Diri, Keluarga, dan peran lain) lewat aturan alokasi. Produk dari bagian jurnal di **Ruang Finansial** (Notion). Tujuan: portofolio dan monetisasi (freemium). Bagian dari tema situs roziqrizal.com: satu orang, banyak ruang, satu sistem.

## Baca dulu sebelum mengerjakan apa pun

- [docs/konsep.md](docs/konsep.md) — posisi produk, MVP, arsitektur, model data, keputusan (sudah dan terbuka)
- [docs/monetisasi.md](docs/monetisasi.md) — pembagian Gratis / Pro / Sync
- [docs/roadmap.md](docs/roadmap.md) — 10 tahap pengerjaan
- [docs/ui-flow.md](docs/ui-flow.md) — layar S01–S28 dan flow F1–F9
- [docs/design/README.md](docs/design/README.md) — design tokens (`tokens.css`) dan prototipe klik; sumber tunggal warna, font, dan jarak

## Prinsip yang tidak boleh dilanggar

- **Inti universal, Islami sebagai modul opsional** (zakat/haul). Penamaan internal kode netral (`allocation`, `room`, `giving-module`), bukan istilah Islami.
- **Uang = bilangan bulat dalam satuan terkecil** (value object `Money`). Tidak pernah floating point.
- **Android saja**: UI mengikuti konvensi Android (bottom navigation, FAB, bottom sheet, snackbar); pembelian lewat Google Play Billing.
- **Offline-first**: data lokal. Backup terenkripsi wajib ada.
- **Semua penguncian fitur lewat satu lapisan entitlement**, tidak ada pengecekan Pro yang tersebar.
- **Selalu gratis**: keamanan (PIN/biometrik), ekspor data, dan dasar zakat. **Tanpa iklan.**
- **Tenang, bukan panik**: peringatan lembut, tidak pernah memblokir; status selalu ikon plus teks, bukan hanya warna.
- Asumsi fikih ditampilkan di layar beserta disclaimer: aplikasi adalah bantuan hitung, bukan fatwa.

## Proyek Android

- Application ID: `com.roziqrizal.rizqflow`. Tetap setelah terbit di Play Store (diputuskan 2026-09-20).
- Modul Gradle: `:domain` (Kotlin/JVM murni, tanpa Android), `:data` (Room; masih kerangka), `:app` (Compose). `:app` dan `:data` boleh bergantung pada `:domain`, tidak sebaliknya.
- Versi: Gradle 9.7.1, AGP 9.4.1 (Kotlin bawaan AGP, tanpa plugin `kotlin-android`), Kotlin 2.4.20, compileSdk dan targetSdk 37, min SDK 26. Semua versi ada di `gradle/libs.versions.toml`.
- Build dari terminal butuh **JDK 17** lewat `JAVA_HOME` (bukan Java 8 yang ada di PATH). Path Android SDK ada di `local.properties`, yang tidak di-commit. Perintah: `./gradlew :domain:test` dan `./gradlew :app:assembleDebug`.
- Android Studio harus versi terbaru: Chipmunk (2021.2) tidak bisa membuka proyek dengan AGP 9.4.
- Tes `:domain`: JUnit Jupiter + kotlin.test, dengan `allWarningsAsErrors` menyala. Kalkulasi murni diuji dengan golden test tanpa mock, mengikuti `docs/strategi-unit-test.md` di alkaukabaandroid.

## Status dan tracking

- Notion: hub **Rizqflow** di dalam Ruang Finansial, berisi database "🧩 Tahap Rizqflow" (bagian-bagian besar) dan "🚀 Pengembangan Rizqflow" (tugas dan log). Hub: https://app.notion.com/p/3e0edbb47c0b8184a091ddcf591766fd
- Catat progres lewat skill **`/pengembangan-rizqflow`** (Notion + dokumentasi repo). Detail mapping dan ID database ada di skill itu.
- Platform: **Android saja**, dirilis di Google Play Store. Stack: **Kotlin + Jetpack Compose + Room** (diputuskan 2026-09-19). Web tidak dibuat.
- Arah visual: **opsi A**, pakai ulang identitas homepage roziqrizal.com (diputuskan 2026-09-19); warna dan font disimpan sebagai design tokens, angka besar selalu Manrope.
- Detail stack **disetujui 2026-09-20** (`docs/konsep.md`): modul `:domain`/`:data`/`:app`, min SDK 26, DI manual, backup AES-GCM, tanpa SQLCipher untuk v1. Masih terbuka: kalender Hijriyah untuk haul dan verifikasi asumsi fikih dengan kitab. Jangan memutuskan sendiri; tanyakan dulu.

## Konvensi

- Bahasa dokumen dan pesan commit: **Indonesia**.
- Perubahan dokumentasi ditulis di `docs/`; jangan biarkan Notion dan repo saling bertentangan (spesifikasi UI penuh di `docs/ui-flow.md`, ringkasannya di halaman Tahap Notion).
- Commit langsung setelah selesai satu permintaan; stage file satu per satu (bukan `git add -A`). **Jangan push** tanpa diminta.
