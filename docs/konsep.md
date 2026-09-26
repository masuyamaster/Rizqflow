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
- Kemudahan mencatat: catat kilat dan favorit, pengingat malam, koreksi saldo (lihat bagian Disiplin mencatat)

Sengaja tidak masuk (agar tidak melebar):
- Sinkronisasi rekening bank
- Fitur investasi lengkap / portofolio saham
- Akun pengguna dan sinkronisasi server (baru di fase 2, sebagai langganan Sync)

## Disiplin mencatat

Masalah nyata dari pemilik proyek, dan alasan aplikasi ini layak dipakai sendiri: pengeluaran kecil sering terlewat karena lupa atau malas, baik yang tunai, QRIS, maupun e-wallet. Aplikasi tidak bisa memaksa disiplin. Yang bisa dilakukan adalah membuat mencatat lebih murah daripada lupa, dan membuat yang terlewat mudah dikejar tanpa rasa bersalah.

| Mekanisme | Mengatasi | Layar |
|---|---|---|
| **Catat kilat**: pintasan ikon, tile Quick Settings, dan favorit (nominal, kategori, ruang, akun) yang tersimpan dengan satu ketukan | Terlalu banyak langkah | S24 |
| **Pengingat malam**: satu notifikasi per hari dengan kolom balasan (misalnya `kopi 25000`) dan tindakan "Tidak ada" | Tidak ada pemicu | S26 |
| **Koreksi saldo**: pengguna memasukkan saldo sebenarnya per akun; selisihnya dicatat sebagai satu baris "Tak terlacak" | Malas membereskan yang bolong | S25 |
| **Petunjuk hari kosong**: teks lembut bila kemarin belum ada catatan pengeluaran dan belum ditandai "Tidak ada" | Tidak sadar ada yang bolong | S08 |

Karena kasusnya bercampur (tunai, QRIS, e-wallet), tidak ada mekanisme yang boleh bergantung pada satu jenis pembayaran. Koreksi saldo bekerja per akun untuk semua jenis: tunai, e-wallet, dan bank (QRIS memotong saldo akun yang dipakai membayar). Inilah jaring pengaman universalnya.

**Tangkap otomatis** (disetujui 2026-09-20; dikerjakan di versi 1.1 setelah rilis; masuk Pro): aplikasi membaca notifikasi pembayaran dari aplikasi bank dan e-wallet di perangkat, lalu menyiapkan **draf** transaksi yang tinggal disetujui. Ini bukan sinkronisasi rekening bank (yang tetap tidak dibuat). Keterbatasannya: hanya menjangkau yang digital, bukan tunai (Koreksi saldo tetap jaring pengamannya), izin akses notifikasi itu sensitif, dan parser tiap aplikasi perlu dirawat. Karena itu rancangannya berhati-hati (usulan, dimatangkan saat tahapnya tiba):

- Selalu berupa draf; tidak pernah menyimpan transaksi tanpa persetujuan pengguna.
- Hanya notifikasi dari daftar aplikasi keuangan yang didukung yang diproses; notifikasi lain diabaikan dan tidak dibaca lebih jauh.
- Yang disimpan hanya nominal, nama tujuan, waktu, dan aplikasi sumber; teks notifikasi asli dibuang.
- Diproses di perangkat, tidak dikirim ke mana pun.
- Versi pertama hanya pengeluaran; pemasukan tetap lewat S06 dan S07 karena harus dialirkan.
- Draf yang mirip transaksi manual (nominal dan akun sama dalam rentang waktu dekat) ditandai "Mungkin sudah dicatat".
- Izin dijelaskan dulu sebelum diminta, dan bisa dicabut kapan saja tanpa merusak data.

Semua mekanisme mengikuti prinsip "tenang, bukan panik": tanpa streak, tanpa warna merah, dan bisa dimatikan.

## Arsitektur (arah)

- **Rule engine alokasi** dengan strategi yang bisa ditukar. Modul Memberi punya dua strategi (`zakat-haul-hijri`, `percentage`), dan ruang lain bisa ditambah tanpa mengubah inti.
- **Offline-first**, data disimpan lokal. Data keuangan itu sensitif, dan untuk portofolio pendekatan ini menghindari urusan keamanan server.
- Penamaan internal kode **netral** (`allocation`, `room`, `giving-module`), bukan istilah Islami, supaya konsisten dengan posisi "inti universal + modul".
- Logika inti (alokasi, nisab, haul) dibuat bisa diuji unit, dengan pendekatan yang sama seperti alkaukabaandroid.

## Draft model data

- **Room** — ruang/peran (nama, tipe ruang, target bulanan, ikon, slot warna 1–8 atau kosong untuk netral)
- **AllocationRule** — cara membagi pemasukan ke ruang (persentase, prioritas)
- **Account** — dompet/rekening tempat uang berada (bank, dompet digital, tunai), seperti tabel Akun di jurnal Ruang Finansial
- **Category** — pos pengeluaran/pemasukan di dalam sebuah ruang, seperti tabel Kategori di jurnal Ruang Finansial
- **Transaction** — pemasukan/pengeluaran/transfer, terkait ke Account, Category, dan Room
- **QuickEntry** — favorit untuk catat kilat (nama, nominal, Category, Room, Account, urutan pemakaian)
- **BalanceCheck** — hasil koreksi saldo (Account, saldo menurut catatan, saldo sebenarnya, selisih, Transaction yang dihasilkan bila ada)
- **CaptureDraft** — draf dari notifikasi, fitur v1.1 (aplikasi sumber, nominal, nama tujuan, waktu, Account usulan, status menunggu/disetujui/diabaikan)
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
- [ui-flow.md](ui-flow.md) — layar S01–S28 dan flow F1–F9

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
- Stack: **Kotlin + Jetpack Compose + Room** (2026-09-19). Compose dipilih untuk kartu ruang, cincin progres, dan animasi aliran; alkaukabaandroid memakai Views/XML, jadi ada kurva belajar yang diterima.
- Arah visual: **opsi A**, pakai ulang identitas homepage roziqrizal.com (palet sage Material 3, Manrope, Libre Caslon Text), dengan warna dan font disimpan sebagai design tokens (2026-09-19). Detail di [ui-flow.md](ui-flow.md).
- Widget, termasuk widget catat kilat, masuk **Pro** (2026-09-20). Pintasan ikon, tile Quick Settings, dan balasan notifikasi tetap gratis, karena mencatat dengan cepat adalah janji utama.
- Selisih Koreksi saldo dicatat di **ruang dari pengeluaran terakhir di akun itu** (2026-09-20). Bila akun belum punya pengeluaran, usulannya ruang bertipe Mencukupi. Selisih adalah transaksi biasa, jadi ikut dihitung dalam jatah ruang itu.
- Pengingat malam **aktif secara bawaan** dan bisa dimatikan (2026-09-20).
- **Tangkap otomatis dari notifikasi** disetujui: dikerjakan di versi 1.1 setelah rilis, dan masuk **Pro** (2026-09-20).
- Detail stack Android (2026-09-26):
  - **Modul domain Kotlin murni** (tanpa dependensi Android) untuk alokasi, nisab, dan haul, supaya unit test berjalan cepat di JVM; modul `app` untuk UI. Ikuti `docs/strategi-unit-test.md` di alkaukabaandroid (JUnit + MockK).
  - **Min SDK 26** (Android 8.0): `java.time` bawaan untuk hitung tanggal dan haul, notification channel untuk pengingat malam. Target SDK mengikuti syarat Google Play terbaru.
  - **DI: Hilt.**
  - **Tanpa SQLCipher di v1.** Perlindungan data lewat sandbox Android, enkripsi file bawaan perangkat, kunci PIN/biometrik, dan backup terenkripsi kata sandi. PIN hanya mengunci UI, bukan file database; enkripsi database bisa ditambahkan di versi berikutnya lewat migrasi.
- **Warna ruang custom** (2026-09-26): ruang custom mendapat 5 slot warna tambahan (slot 4–8, total 8 slot) dari palet yang divalidasi; ruang ke-9+ netral. Warna melekat pada ruang, tidak diputar ulang. Aturan dan validasi di [design/README.md](design/README.md#warna-ruang-custom-slot-4-sampai-8).

## Keputusan yang masih terbuka

- **Komponen UI** (perlu sebelum Tahap 3): basis Material 3 atau kustom mengikuti keputusan arah visual.
- **Cek ketersediaan nama:** Play Store, domain, GitHub, hasil pencarian Google.
- **Sumber harga emas** untuk nisab (API atau input manual).
- **Kalender Hijriyah untuk haul:** hisab Al-Kaukaba, Umm al-Qura, atau kriteria Kemenag; selisih satu hari memengaruhi tanggal haul.
- **Asumsi fikih default zakat mal** (nisab 85 gram emas, 2,5%, haul 1 tahun Hijriyah) dan cara menampilkannya beserta disclaimer.
- **Arti "terpenuhi" per tipe ruang** (lihat bagian Tipe ruang).
- **Pengingat haul:** tetap gratis untuk satu profil atau masuk Pro.
- **Harga final** Pro dan Sync, berdasarkan uji minat.
- **Pengingat malam:** jam bawaan dan kapan izin notifikasi Android 13+ diminta. Usulan: pukul 21.00, dan izin diminta setelah transaksi pertama disimpan, bukan di awal onboarding.
- **Aplikasi bank dan e-wallet yang didukung lebih dulu** untuk tangkap otomatis. Usulan: yang paling sering dipakai, dilihat dari Transaksi Harian di Ruang Finansial.
