# Rizqflow

Aplikasi finansial berbasis "hak": rezeki yang masuk dialirkan ke ruang-ruang (Memberi, Diri, Keluarga, dan peran lain) lewat aturan alokasi. Produk dari bagian jurnal di **Ruang Finansial** (Notion). Tujuan: portofolio dan monetisasi (freemium). Bagian dari tema situs roziqrizal.com: satu orang, banyak ruang, satu sistem.

## Baca dulu sebelum mengerjakan apa pun

- [docs/konsep.md](docs/konsep.md) — posisi produk, MVP, arsitektur, model data, keputusan (sudah dan terbuka)
- [docs/monetisasi.md](docs/monetisasi.md) — pembagian Gratis / Pro / Sync
- [docs/roadmap.md](docs/roadmap.md) — 10 tahap pengerjaan
- [docs/ui-flow.md](docs/ui-flow.md) — layar S01–S23 dan flow F1–F7

## Prinsip yang tidak boleh dilanggar

- **Inti universal, Islami sebagai modul opsional** (zakat/haul). Penamaan internal kode netral (`allocation`, `room`, `giving-module`), bukan istilah Islami.
- **Uang = bilangan bulat dalam satuan terkecil** (value object `Money`). Tidak pernah floating point.
- **Android saja**: UI mengikuti konvensi Android (bottom navigation, FAB, bottom sheet, snackbar); pembelian lewat Google Play Billing.
- **Offline-first**: data lokal. Backup terenkripsi wajib ada.
- **Semua penguncian fitur lewat satu lapisan entitlement**, tidak ada pengecekan Pro yang tersebar.
- **Selalu gratis**: keamanan (PIN/biometrik), ekspor data, dan dasar zakat. **Tanpa iklan.**
- **Tenang, bukan panik**: peringatan lembut, tidak pernah memblokir; status selalu ikon plus teks, bukan hanya warna.
- Asumsi fikih ditampilkan di layar beserta disclaimer: aplikasi adalah bantuan hitung, bukan fatwa.

## Status dan tracking

- Notion: hub **Rizqflow** di dalam Ruang Finansial, berisi database "🧩 Tahap Rizqflow" (bagian-bagian besar) dan "🚀 Pengembangan Rizqflow" (tugas dan log). Hub: https://app.notion.com/p/3e0edbb47c0b8184a091ddcf591766fd
- Catat progres lewat skill **`/pengembangan-rizqflow`** (Notion + dokumentasi repo). Detail mapping dan ID database ada di skill itu.
- Platform: **Android saja**, dirilis di Google Play Store. Stack: **Kotlin + Jetpack Compose + Room** (diputuskan 2026-09-19). Web tidak dibuat.
- Arah visual: **opsi A**, pakai ulang identitas homepage roziqrizal.com (diputuskan 2026-09-19); warna dan font disimpan sebagai design tokens, angka besar selalu Manrope.
- Masih terbuka: **detail stack** (modul domain terpisah, min/target SDK, DI, enkripsi database; usulan di `docs/konsep.md`). Jangan memutuskan sendiri; tanyakan dulu.

## Konvensi

- Bahasa dokumen dan pesan commit: **Indonesia**.
- Perubahan dokumentasi ditulis di `docs/`; jangan biarkan Notion dan repo saling bertentangan (spesifikasi UI penuh di `docs/ui-flow.md`, ringkasannya di halaman Tahap Notion).
- Commit langsung setelah selesai satu permintaan; stage file satu per satu (bukan `git add -A`). **Jangan push** tanpa diminta.
