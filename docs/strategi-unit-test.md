# Rizqflow — Strategi unit test

Ditulis dari nol untuk Rizqflow (2026-09-21). Tujuan: logika yang menyangkut uang dan kewajiban agama (alokasi, nisab, haul) harus **terbukti benar** oleh tes yang cepat, deterministik, dan mudah dibaca, sebelum ada layar yang memakainya.

## Prinsip

1. **Logika bisnis diuji di JVM murni.** Semua yang menyangkut uang, alokasi, kalender, zakat, dan entitlement ada di `:domain` (Kotlin/JVM, tanpa Android), sehingga tesnya berjalan dalam hitungan detik tanpa emulator.
2. **Fake, bukan mock, untuk logika murni.** Kalkulasi murni diuji dengan masukan dan keluaran nyata (golden test). Mock hanya untuk batas sistem yang benar-benar tidak bisa dibuat nyata; di domain sejauh ini tidak dibutuhkan.
3. **Uang selalu `Money`.** Tes tidak memakai `Double` atau `Float`, bahkan untuk menghitung nilai yang diharapkan. Nilai harapan ditulis sebagai bilangan bulat rupiah.
4. **Waktu masuk sebagai parameter.** Fungsi menerima `today: LocalDate`, tidak memanggil jam sistem. Tes tidak pernah memakai "hari ini", jadi tidak akan gagal karena tanggal berganti.
5. **Tidak bergantung pada lingkungan.** Tanpa bergantung pada zona waktu, Locale, urutan tes, atau jaringan. Format tampilan (spasi tak-terputus di "Rp 1.000") diuji di lapisan tampilan, bukan di domain.
6. **Tes adalah dokumentasi.** Nama tes berupa kalimat Indonesia dalam backtick yang menyebut perilaku, bukan nama fungsi: ``fun `haul genap tepat pada tanggal jatuh tempo`() ``. Satu perilaku per tes, dengan susunan Given (siapkan), When (jalankan), Then (periksa) yang dipisah baris kosong.
7. **Peringatan compiler adalah galat** (`allWarningsAsErrors`), termasuk di kode tes.
8. **Bug dimulai dari tes.** Bug yang ditemukan ditulis dulu sebagai tes yang gagal, baru diperbaiki.

## Jenis tes

| Lapisan | Alat | Isi | Status |
|---|---|---|---|
| `:domain` | JUnit Jupiter + kotlin.test, JVM | Golden test, kasus tepi, sifat umum dengan `Random` bersemai tetap | Berjalan (82 tes) |
| `:data` | Room in-memory, uji migrasi (`MigrationTestHelper`) | DAO, kueri agregat per bulan, migrasi antarversi skema | Belum; mulai saat skema Room dibuat |
| `:app` ViewModel | JUnit + repositori palsu | Perubahan state, alur F1 sampai F9 tanpa layar | Belum; Tahap 3 |
| `:app` UI | Compose UI test | Alur utama, empty state, font 200%, mode gelap | Belum; Tahap 3 dan 4 |
| Instrumentasi | Emulator di CI atau lokal | Notifikasi, izin, PIN dan biometrik | Belum; Tahap 6 |

## Yang wajib ada di setiap fitur domain

- **Golden test** yang angkanya berasal dari dokumen desain (contoh S07: Rp 8.500.000 dibagi 10/30/60; contoh S16: nisab 85 g x Rp 1.660.000). Bila dokumen dan kode berbeda, salah satunya salah dan harus dibereskan.
- **Kasus tepi**: nol, satu satuan terkecil, nilai sangat besar, negatif (ditolak atau ditangani eksplisit), duplikat, daftar kosong, batas tepat (tepat sama dengan nisab, tepat pada hari jatuh tempo), dan urutan masukan terbalik.
- **Sifat umum** untuk kalkulasi yang punya invarian. Contoh alokasi: jumlah semua bagian ditambah yang belum dialirkan selalu persis sama dengan nominal, dan tiap bagian hanya boleh lantai atau lantai + 1 dari nilai persisnya. Diuji pada ribuan masukan acak dengan bibit tetap, supaya bila gagal bisa diulang.
- **Uji penolakan**: masukan tidak sah (nominal negatif, total aturan di atas 100%, ruang ganda) melempar `IllegalArgumentException` dan ada tesnya.

## Cakupan per area (saat ini)

| Area | Tes | Yang dijaga |
|---|---|---|
| `Money` | 11 | Aritmetika eksak, luapan melempar galat, mata uang tidak tercampur |
| Alokasi | 20 | Sisa terbesar dengan prioritas sebagai pemutus seri, total di bawah 100%, angka besar, 2.000 kasus acak |
| Kalender Umm al-Qura | 9 | Tanggal acuan, konversi bolak-balik, tahun Hijriyah 354 atau 355 hari, pemangkasan hari ke-30 |
| Nisab dan zakat | 12 | Nisab 85 g, emas pecahan gram, pembulatan setengah ke atas, harta bersih negatif |
| Haul | 16 | Mulai, genap, putus, kedua kebijakan fikih, zakat ditunaikan, kejadian tak berurutan |
| Strategi Memberi | 8 | Dua mode, masukan belum lengkap, haul berjalan atau genap |
| Entitlement | 6 | Paket Pro dan Sync, batas ruang gratis |

## Yang perlu ditambah saat fiturnya dibuat

- **Backup dan restore**: pulang-pergi (backup lalu restore menghasilkan data identik), sandi salah, berkas rusak atau terpotong, format versi lebih baru, data tidak tersentuh saat gagal.
- **Impor CSV Transaksi Harian**: berdasarkan pola data nyata (lihat model-data.md): pasangan baris "Transfer to X (Out)" dan "(In)" menjadi satu transfer, tanda nominal tidak konsisten (pengeluaran kadang positif kadang negatif), deskripsi berhierarki "Kategori > Sub", dan catatan panjang.
- **Parser balasan notifikasi** ("kopi 25000", "parkir 3rb", "makan siang 35k") dan **parser notifikasi bank atau e-wallet** (v1.1), dengan contoh teks nyata yang dianonimkan.
- **Koreksi saldo**: selisih kurang, lebih, dan nol; ruang tempat selisih dicatat.
- **Kueri agregat bulanan**: jatah dan terpakai per ruang, status per tipe ruang, batas bulan (transaksi pukul 23.59 dan 00.00), dan status ruang tepat di ambang 85%.

## Cara menjalankan

```
./gradlew :domain:test    # cepat, tanpa emulator
./gradlew test            # semua tes unit
```

Butuh JDK 17 (lihat CLAUDE.md). CI menjalankannya di setiap push dan pull request.

## Ukuran cakupan

Belum ada alat pengukur cakupan. Usulan: pasang Kover untuk `:domain` dengan target awal 90% garis dan cabang, sebagai laporan dulu, bukan gerbang CI, sampai angkanya stabil.
