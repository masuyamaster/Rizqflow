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
- Backup dan restore lokal terenkripsi (wajib: data hanya di perangkat, tanpa backup ganti ponsel berarti kehilangan riwayat)
- Mode demo dengan data contoh (penilai portofolio bisa mencoba tanpa data keuangan asli)

Sengaja tidak masuk (agar tidak melebar):
- Sinkronisasi rekening bank
- Fitur investasi lengkap / portofolio saham
- Akun pengguna dan sinkronisasi server (baru di fase 2, sebagai langganan Sync)

## Arsitektur (arah)

- **Rule engine alokasi** dengan strategi yang bisa ditukar. Modul Memberi punya dua strategi (`zakat-haul-hijri`, `percentage`), dan ruang lain bisa ditambah tanpa mengubah inti.
- **Offline-first**, data disimpan lokal. Data keuangan itu sensitif, dan untuk portofolio pendekatan ini menghindari urusan keamanan server.
- Penamaan internal kode **netral** (`allocation`, `room`, `giving-module`), bukan istilah Islami, supaya konsisten dengan posisi "inti universal + modul".
- Logika inti (alokasi, nisab, haul) dibuat bisa diuji unit, dengan pendekatan yang sama seperti alkaukabaandroid.

## Draft model data

- **Room** — ruang/peran (nama, tipe ruang, target bulanan)
- **AllocationRule** — cara membagi pemasukan ke ruang (persentase, prioritas)
- **Account** — dompet/rekening tempat uang berada (bank, dompet digital, tunai), seperti tabel Akun di jurnal Ruang Finansial
- **Category** — pos pengeluaran/pemasukan di dalam sebuah ruang, seperti tabel Kategori di jurnal Ruang Finansial
- **Transaction** — pemasukan/pengeluaran/transfer, terkait ke Account, Category, dan Room
- **AssetProfile** — profil harta yang dihitung zakatnya (emas, tabungan, investasi, piutang, pengurang)
- **HaulRecord** — pelacakan nisab dan haul (tanggal mulai Hijriyah, nisab saat itu, status)

Semua nominal disimpan sebagai **bilangan bulat dalam satuan terkecil** (value object `Money`), tidak pernah floating point.

## Tipe ruang: arti "hak terpenuhi"

"Terpenuhi" tidak berarti sama untuk semua ruang. Usulan tiga tipe (**perlu dikonfirmasi** sebelum logika status Denah dibuat):

| Tipe ruang | Contoh | Terpenuhi bila |
|---|---|---|
| Menunaikan | Memberi | Dana yang tersalurkan mencapai target bulan itu (atau zakat sudah ditunaikan saat haul genap) |
| Menumbuhkan | Diri (investasi, dana darurat) | Dana yang benar-benar masuk ke akun tujuan mencapai target |
| Mencukupi | Keluarga (nafkah, kebutuhan) | Kebutuhan bulan itu tertutup dan pengeluaran tidak melebihi jatah |

## Dokumen terkait

- [monetisasi.md](monetisasi.md) — model bisnis Gratis / Pro / Sync
- [roadmap.md](roadmap.md) — 10 tahap pengerjaan
- [ui-flow.md](ui-flow.md) — layar S01–S23 dan flow F1–F7

## Risiko

- **Cakupan melebar.** Jaga MVP tetap sempit.
- **Perbedaan pendapat fikih** soal nisab dan haul. Tulis asumsi yang dipakai secara eksplisit, atau sediakan pilihan.
- **Data sensitif.** Simpan lokal, jangan kirim ke mana pun tanpa alasan kuat.

## Keputusan yang sudah diambil

- Nama: **Rizqflow** (akar r-z-q dari "Roziq" = rezeki, ditambah "flow" untuk aliran alokasi). Cadangan: *Rizqly*.
- Posisi: inti universal + modul Islami opsional.
- Data: offline-first.
- Monetisasi: freemium; Pro sekali bayar; Sync sebagai langganan fase 2; tanpa iklan. Detail di [monetisasi.md](monetisasi.md).
- Keamanan, ekspor data, dan dasar zakat selalu gratis.
- Platform: **Android saja**, dirilis di Google Play Store (2026-09-19). Web tidak dibuat; landing page dan case study tetap di roziqrizal.com. Pembelian lewat Google Play Billing.

## Keputusan yang masih terbuka

- **Stack Android** (**blocker utama** untuk Tahap 2). Usulan, belum diputuskan:
  - Kotlin + **Jetpack Compose** (Material 3): cocok untuk kartu ruang, cincin progres, dan animasi aliran, dan lebih bernilai di portofolio. Harganya kurva belajar, karena alkaukabaandroid memakai Views/XML dengan ViewBinding.
  - Penyimpanan lokal: **Room** (SQLite). Backup dienkripsi dengan kata sandi; enkripsi database diputuskan terpisah.
  - **Modul domain Kotlin murni** (tanpa dependensi Android) untuk alokasi, nisab, dan haul, supaya unit test berjalan cepat di JVM; modul `app` untuk UI. Ikuti `docs/strategi-unit-test.md` di alkaukabaandroid (JUnit + MockK).
  - Target SDK mengikuti syarat Google Play terbaru; min SDK ditentukan bersama stack.
  - DI: alkaukabaandroid tanpa framework DI; untuk Rizqflow bisa manual atau Hilt/Koin.
- **Cek ketersediaan nama:** Play Store, domain, GitHub, hasil pencarian Google.
- **Sumber harga emas** untuk nisab (API atau input manual).
- **Kalender Hijriyah untuk haul:** hisab Al-Kaukaba, Umm al-Qura, atau kriteria Kemenag; selisih satu hari memengaruhi tanggal haul.
- **Asumsi fikih default zakat mal** (nisab 85 gram emas, 2,5%, haul 1 tahun Hijriyah) dan cara menampilkannya beserta disclaimer.
- **Arti "terpenuhi" per tipe ruang** (lihat bagian Tipe ruang).
- **Arah visual:** pakai ulang identitas homepage roziqrizal.com atau identitas produk sendiri.
- **Pengingat haul:** tetap gratis untuk satu profil atau masuk Pro.
- **Harga final** Pro dan Sync, berdasarkan uji minat.
