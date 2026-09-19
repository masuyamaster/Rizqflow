# Rizqflow — Konsep

> Rezeki mengalir, setiap hak terpenuhi.

Dokumen ini merangkum keputusan awal proyek supaya tidak perlu diulang dari nol.

## Tujuan

Aplikasi finansial untuk **portofolio**. Yang dinilai adalah cara berpikir produk dan kualitas teknisnya, bukan bersaing dengan aplikasi finansial yang sudah ada di pasaran. Aplikasi pencatat pengeluaran generik sengaja tidak dibangun.

## Masalah & sudut pandang

Aplikasi finansial biasa bertanya: *"uangmu habis ke mana?"*

Rizqflow bertanya: *"apakah setiap hak sudah ditunaikan?"*

Hidup dipandang sebagai kumpulan peran/kewajiban. Tiap peran punya jatah uang dan aturan sendiri, dan aplikasi memastikan tidak ada yang terabaikan. Ini sejalan dengan tema situs [roziqrizal.com](https://roziqrizal.com): satu orang, banyak ruang, satu sistem.

## Posisi produk: inti universal + modul opsional

Inti aplikasi **tidak Islami**. Ini *role-based / values-based budgeting* yang bisa dipakai siapa saja. Sisi Islami hadir sebagai modul yang bisa dinyalakan atau dimatikan.

| Hak (sumber ide) | Ruang (label netral) | Isi contoh |
|---|---|---|
| Hak Rabb | Memberi | zakat, sedekah, donasi |
| Hak diri | Diri | investasi, dana darurat, belajar, kesehatan |
| Hak keluarga | Keluarga | nafkah, tabungan bersama, tanggungan |

Peran spesifik (Investor, Trader, Husband, dll.) menjadi ruang tambahan dengan sistemnya sendiri.

Saat menulis README dan case study: **buka dengan masalah umum** ("uang habis tanpa sadar apakah semua tanggung jawab terpenuhi"), lalu tunjukkan sisi Islami sebagai contoh implementasi yang mendalam.

## Pembeda utama

**Tracker zakat mal dengan haul kalender Hijriyah.** Nisab dari harga emas, haul dihitung dalam tahun Hijriyah. Aplikasi zakat yang ada umumnya hanya kalkulator. Keahlian hisab/kalender Hijriyah dari proyek Al-Kaukaba menjadi keunggulan yang sulit ditiru.

## Cakupan MVP

Masuk:
- Input transaksi (pemasukan, pengeluaran)
- Aturan alokasi pemasukan ke ruang-ruang (mis. persentase per ruang)
- Dashboard satu layar: kondisi semua ruang, mana yang belum terpenuhi
- Modul Memberi: mode zakat/haul Hijriyah **atau** persentase donasi biasa

Sengaja tidak masuk (agar tidak melebar):
- Sinkronisasi rekening bank
- Fitur investasi lengkap / portofolio saham
- Akun pengguna dan sinkronisasi server

## Arsitektur (arah)

- **Rule engine alokasi** dengan strategi yang bisa ditukar. Modul Memberi punya dua strategi (`zakat-haul-hijri`, `percentage`), dan ruang lain bisa ditambah tanpa mengubah inti.
- **Offline-first**, data disimpan lokal. Data keuangan itu sensitif, dan untuk portofolio pendekatan ini menghindari urusan keamanan server.
- Penamaan internal kode **netral** (`allocation`, `room`, `giving-module`), bukan istilah Islami, supaya konsisten dengan posisi "inti universal + modul".
- Logika inti (alokasi, nisab, haul) dibuat bisa diuji unit, dengan pendekatan yang sama seperti alkaukabaandroid.

## Draft model data

- **Room** — ruang/peran (nama, jenis, aturan)
- **AllocationRule** — cara membagi pemasukan ke ruang (persentase, prioritas)
- **Transaction** — pemasukan/pengeluaran, terkait ke Room
- **HaulRecord** — pelacakan nisab dan haul (tanggal mulai Hijriyah, nisab saat itu, status)

## Risiko

- **Cakupan melebar.** Jaga MVP tetap sempit.
- **Perbedaan pendapat fikih** soal nisab dan haul. Tulis asumsi yang dipakai secara eksplisit, atau sediakan pilihan.
- **Data sensitif.** Simpan lokal, jangan kirim ke mana pun tanpa alasan kuat.

## Keputusan yang sudah diambil

- Nama: **Rizqflow** (akar r-z-q dari "Roziq" = rezeki, ditambah "flow" untuk aliran alokasi). Cadangan: *Rizqly*.
- Posisi: inti universal + modul Islami opsional.
- Data: offline-first.

## Keputusan yang masih terbuka

- **Platform:** Android (fondasi dari alkaukabaandroid, cocok dengan offline-first) atau web Laravel (bagian dari roziqrizal.com).
- **Cek ketersediaan nama:** Play Store, domain, hasil pencarian Google.
- **Sumber harga emas** untuk nisab (API atau input manual).
