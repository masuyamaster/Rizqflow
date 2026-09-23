# Riset Produk: Aplikasi Pencatat Keuangan Pribadi/Keluarga yang Paling Dibutuhkan (Indonesia & Global, 2024–2026)

Yang paling dibutuhkan user adalah pencatatan yang hampir tanpa usaha: transaksi masuk sendiri atau cukup lewat satu chat/foto, lalu aplikasi memberi tahu "masih aman belanja berapa" — bukan sekadar grafik; karena di Indonesia sinkronisasi bank/e-wallet otomatis masih sulit dan tidak andal, pemenang realistis untuk pemain baru adalah aplikasi "input super cepat + AI (chat/voice/foto struk/screenshot mutasi) + mode keluarga", dengan harga ramah kantong dan opsi sekali bayar.

## TL;DR
- **Masalah nomor satu di seluruh dunia adalah kelelahan input manual dan data yang tidak akurat**, bukan kurangnya fitur. Aplikasi yang menang secara global (Monarch, Copilot, YNAB) menjual otomatisasi, kolaborasi pasangan, dan metode anggaran yang jelas dengan harga ±US$95–109/tahun; di Indonesia, sinkronisasi bank/e-wallet belum menjadi jalur yang andal untuk startup kecil, sehingga pemain lokal (Finku, Sribuu) justru bergeser ke input via AI, e-statement, dan screenshot.
- **Peluang terbesar di Indonesia: "family finance" berbasis peran** (istri sebagai pengelola kas, suami memberi "uang bulanan", anggota keluarga ikut mencatat) + input via WhatsApp/chat dalam bahasa sehari-hari ("bakso 25rb") + dukungan uang tunai, QRIS, dan banyak dompet (GoPay/DANA/OVO/ShopeePay). Kategori bot WhatsApp sedang ramai (Monly, CatatAja, Jurnal Cuan, CatatWA, CashMind), tetapi masih berfokus pada individu/UMKM dan belum ada yang benar-benar menguasai rumah tangga.
- **Model bisnis yang disarankan:** freemium dengan paywall yang tampil sejak onboarding, uji coba 7–14 hari, harga Indonesia Rp15–39 ribu/bulan atau ±Rp150–250 ribu/tahun plus opsi lifetime (±Rp299–349 ribu, mengikuti patokan Money Lover); untuk pasar global, US$4–8/bulan dengan diferensiasi privasi (local-first) dan paket keluarga tanpa biaya per-orang.

## Key Findings

### 1. Kebutuhan & pain point pengguna (global + Indonesia)

**a. Input manual adalah penyebab utama berhenti.** Hampir semua analisis retensi menyebut hal yang sama: setiap transaksi yang butuh beberapa langkah (buka aplikasi → pilih kategori → ketik nominal → simpan) menciptakan friksi yang lama-lama dihindari otak. Reviewer umum juga mengaku berhenti memakai aplikasi manual (EveryDollar) setelah dua minggu karena lupa mencatat pembelian kecil. Beberapa blog vendor mengutip angka seperti "Day-30 retention rata-rata 38%" dan "aplikasi manual kehilangan user 3x lebih cepat daripada auto-sync" — **angka ini berasal dari blog pemasaran (Strategia-X, SpendTrak, BudgetSmart) yang sumber primernya tidak dapat diverifikasi**, jadi perlakukan sebagai indikasi arah, bukan fakta keras. Arah temuannya konsisten: friksi input = churn.

**b. Data yang salah/terlambat menghancurkan kepercayaan.** Ketika sinkronisasi gagal atau kategori salah, user meragukan seluruh sistem. Di AS, bahkan dengan Plaid, keluhan "koneksi putus/perlu login ulang" tetap sering muncul. Implikasinya: sinkronisasi yang tidak andal bisa lebih buruk daripada tidak ada sinkronisasi sama sekali.

**c. Tampilan rumit dan "rasa bersalah".** Antarmuka yang padat membuat user kewalahan; melihat pengeluaran melebihi anggaran memicu rasa malu dan menghindar. Sistem yang kaku tanpa jalur "pulih lalu lanjut" (misalnya saat ada pengeluaran darurat) menjadi alasan user berhenti. Laporan CFPB Februari 2017 "Consumer insights on managing spending" menemukan bahwa "even those consumers who have a budget generally do not benchmark their spending to their budget frequently or regularly", sementara "over 90 percent of the consumers we talked to were interested in using a tool or mobile application that gave them such [real-time spending] feedback" (sampel tidak representatif secara statistik) — artinya masalahnya adalah kebiasaan, bukan pengetahuan, dan fitur "sisa aman belanja" real-time memang dicari.

**d. Keluhan khas di Indonesia** (dari ulasan editorial dan listing aplikasi):
- Iklan mengganggu di versi gratis dan fitur penting dikunci premium (Money Lover).
- Fitur multi-dompet (misalnya memisahkan GoPay, OVO, tunai) dikunci di premium — padahal user Indonesia umumnya memegang banyak dompet.
- Aplikasi global tidak berbahasa Indonesia atau tidak memahami pola lokal (misalnya beberapa aplikasi dengan sinkronisasi bank hanya berbahasa Inggris).
- Sinkronisasi e-wallet rapuh: Sribuu sendiri menjelaskan bahwa GoPay/OVO hanya memberi **1 sesi login per akun**, sehingga menghubungkan e-wallet ke aplikasi pencatat membuat user harus login ulang di aplikasi e-wallet; pengambilan riwayat bank bisa memakan waktu ~10 menit dan hanya berjalan lewat internet banking (bukan mobile banking).
- Kekhawatiran keamanan data karena memberikan kredensial bank ke pihak ketiga.

### 2. Lanskap Indonesia

**Perilaku pembayaran: sangat digital, tetapi tunai belum hilang.**
- Visa Consumer Payment Attitudes Study 2023 (1.000 responden): e-wallet dipakai 92% responden, internet banking 81%, **uang tunai masih 80%** (turun dari 87% pada 2021 dan 84% pada 2022); baby boomers masih 92% memakai tunai, Gen Z 78%.
- QRIS: menurut Bank Indonesia, per Semester I 2025 QRIS menjangkau 57 juta pengguna dan 39,3 juta merchant (93,16% UMKM), dengan 6,05 miliar transaksi senilai Rp579 triliun; per Agustus 2025 merchant mencapai 40 juta dan pengguna 57,6 juta; laporan akhir 2025 menyebut 59,53 juta pengguna dan 42,75 juta merchant dengan pertumbuhan volume 139,9% yoy.
- E-wallet paling dipakai (Jakpat, November 2025, 1.945 responden): DANA teratas di semua generasi (75% Gen Z, 70% Milenial), GoPay 60%, ShopeePay 46%, OVO 27%, LinkAja 5%. Survei Ipsos 2026 menempatkan ShopeePay sebagai top-of-mind (41%). **Survei berbeda memberi peringkat berbeda** — kesimpulan praktisnya: user rata-rata memegang 2–4 e-wallet, sehingga multi-dompet adalah fitur wajib, bukan premium.
- Implikasi produk: aplikasi harus menangani **tunai + beberapa e-wallet + beberapa rekening bank + transfer antar-dompet (top-up)** tanpa menghitung ganda. Menurut GoodStats berdasarkan data Bank Indonesia hingga November 2025, reload/top-up mendominasi transaksi uang elektronik dengan nilai Rp1,52 kuadriliun atau setara 50% dari total (belanja di posisi kedua Rp894,46 triliun/30%, transfer antar-uang elektronik Rp559,98 triliun), sehingga deteksi "transfer internal" sangat penting agar laporan tidak menggelembung.

**Literasi & peran keluarga.**
- SNLIK 2025 (OJK–BPS): indeks literasi keuangan 66,46%, inklusi 80,51% — ada jurang ±14 poin antara "punya akses" dan "paham mengelola". Literasi keuangan syariah hanya 43,42% dan inklusi syariah 13,41%.
- Pengelola kas rumah tangga umumnya perempuan: artikel perencana keuangan (QM Financial) mengutip SUSENAS BPS 2020 bahwa sekitar 60% rumah tangga dikelola perempuan (**angka sekunder; sumber primer BPS tidak kami temukan langsung**). Model yang lazim: "suami memberi jatah/uang bulanan", "bagi tugas" (suami cicilan & sekolah, istri dapur & anak), atau "satu pintu".
- Implikasi: fitur keluarga harus mendukung **peran dan alokasi** (penerima jatah, pos dapur/anak, laporan ke pasangan), bukan hanya "berbagi akun". Kebutuhan lokal lain yang belum dilayani dengan baik oleh aplikasi global: arisan, zakat/infak/sedekah, THR dan pengeluaran Lebaran/mudik, kiriman ke orang tua, cicilan/paylater.

**Status open banking/SNAP BI — realitas teknis bagi developer baru.**
- SNAP (Standar Nasional Open API Pembayaran) ditetapkan BI pada 2021; pengelolaannya dialihkan ke ASPI sejak 1 September 2023. Secara teknis SNAP mencakup Balance Inquiry, Transaction History, dan Bank Statement dengan OAuth "account binding" dan persetujuan konsumen.
- Namun aksesnya tidak terbuka untuk sembarang aplikasi: dokumentasi BRI menyebut API Bank Statement dapat diakses oleh "Non-PJP Service Users, atau PJP PIAS yang telah disertifikasi oleh PJP AIS", artinya aplikasi harus berlisensi Penyedia Jasa Pembayaran (kategori layanan informasi akun) atau bermitra dengan pihak berlisensi, plus perjanjian per bank. Kami tidak menemukan daftar resmi BI tentang bank mana saja yang sudah membuka akses informasi rekening bagi pihak ketiga di produksi, dan tidak ada bukti e-wallet (GoPay/OVO/DANA) membuka riwayat transaksi lewat API.
- Ekosistem agregator lokal menyusut: Brick kini berfokus pada infrastruktur pembayaran dan hanya menawarkan "OCR Bank Statement" serta validasi rekening (tidak ada produk agregasi login bank); Finantier dilaporkan tutup (DealStreetAsia, 21 September 2023, berbasis sumber anonim); Ayoconnect memegang lisensi PJP Kategori 1 dan memasarkan "Open Finance APIs", tetapi kini diposisikan terutama sebagai API pembayaran.
- Regulasi: UU PDP 27/2022 mewajibkan dasar pemrosesan yang sah (persetujuan eksplisit), rahasia bank diatur UU Perbankan, dan POJK 21/2023 mewajibkan bank mengawasi mitra pihak ketiga. Tidak ada aturan yang secara eksplisit melarang screen-scraping, tetapi tekanan regulasi dan akademik bergerak menjauh dari praktik berbasis kredensial.
- **Bukti paling kuat dari pasar:** Finku — yang dulu menonjolkan koneksi ke KlikBCA, Livin' Mandiri, BRI, BNI, GoPay, dll. — pada listing App Store terbarunya (v2.1.0, 30 Agustus 2026) hanya mendaftar input "AI, upload e-statement, scan struk, scan screenshot transaksi e-wallet dan bank, atau manual". Sribuu juga kini memimpin dengan "catat dari e-statement dan struk". Kesimpulan: **untuk startup/developer independen, jangan menjadikan auto-sync bank sebagai fondasi MVP di Indonesia**; gunakan OCR e-statement/screenshot, parsing notifikasi, dan input chat/AI.

**Pemain lokal & harga (Indonesia).**

| Aplikasi | Posisi | Harga (Rp) | Catatan |
|---|---|---|---|
| Money Lover (Finsify, Vietnam) | Pencatat manual populer, multi-bahasa & multi-mata uang | Premium lifetime Rp349 rb (diskon Rp299/169 rb); Linked Wallet Rp35–45 rb/bulan atau Rp359–379 rb/tahun; Budget Plus Rp49 rb/bulan | Lifetime tidak termasuk sinkronisasi bank; keluhan iklan & fitur terkunci |
| Finku | PFM lokal, pivot ke AI (FinGPT), OCR e-statement/struk/screenshot | Premium digratiskan untuk semua user hingga Maret 2027 | Klaim 1 juta+ pengguna; "Best Hidden Gem App 2025"; FinGPT memakai OpenAI, Anthropic, Gemini |
| Sribuu | PFM lokal, sinkron bank/e-wallet + konsultasi perencana keuangan | Annual Rp250.000 (harga normal), uji coba 7 hari | Klaim 500 ribu+ anggota; kini memimpin dengan input e-statement & struk |
| Finansialku | Perencanaan keuangan (CFP), target finansial | Rp35.000/bulan atau Rp350.000/tahun | Lebih ke perencanaan daripada pencatatan harian |
| Wallet by BudgetBakers | Global, sinkron bank (klaim 15.000+ bank), berbahasa Indonesia | Premium ±€4,49/bulan (bervariasi per wilayah) | Kuat untuk multi-mata uang |
| Monly AI (WhatsApp) | Bot pencatat via WA, rilis Maret 2025 | Gratis 30 perintah/bulan; Premium Rp39.000/bulan; Enterprise Rp45.000/bulan | Klaim 1.000+ pengguna aktif |
| CatatAja, Jurnal Cuan, CatatWA, CashMind, Arusuang, Rekafin (WhatsApp) | Bot AI: teks, voice note, foto struk | CatatWA mulai Rp9–15 rb/bulan; CashMind mulai Rp15 rb/bulan; Arusuang Exclusive Rp19 rb/bulan; Jurnal Cuan uji coba 7 hari | Semua berbasis input manual/chat — tidak ada yang tersambung otomatis ke bank; banyak menyasar UMKM |

Interpretasi: harga pasar Indonesia untuk pencatat keuangan berada di **Rp15–45 ribu/bulan atau Rp250–380 ribu/tahun/lifetime**. Finku yang menggratiskan premium hingga 2027 menandakan tekanan harga yang kuat — sulit bersaing di "fitur umum", harus menang di segmen spesifik.

### 3. Lanskap Global

**Pasca-Mint.** Intuit mengumumkan penutupan Mint pada November 2023; awalnya Intuit menyebut Mint akan ditutup di awal 2024, lalu tanggalnya dimundurkan ke 23 Maret 2024 (Wikipedia "Intuit Mint"; WalletHub: "Mint shut down on March 23, 2024 and is no longer available"), dan user diarahkan ke Credit Karma yang **tidak memiliki anggaran bulanan dan kategori kustom**. Bloomberg (1 November 2023) mencatat "Mint service had 3.6 million monthly active users in 2021" (sebagai pembanding, Wikipedia menyebut 13 juta pengguna terdaftar pada 2020). CEO Monarch Val Agostino (mantan PM pertama Mint) mengatakan kepada Fast Company (November 2023): "Our signup volume has gone up 20 times since the announcement", dan co-founder Ozzie Osman mengatakan kepada TechCrunch bahwa 1 November 2023 "was our biggest day in terms of new users since we launched the app." Pelajaran dari Mint: model gratis yang dibiayai iklan produk keuangan rapuh; user yang pindah bersedia membayar demi (1) budgeting dan kategori kustom, (2) impor riwayat data lama (Monarch menyediakan importer CSV Mint), (3) tidak ada iklan/penjualan data.

**Pemimpin pasar & harga (2026):**
- **YNAB:** US$109/tahun atau US$14,99/bulan, tanpa tier gratis, trial 34 hari; zero-based budgeting; berbagi hingga 6 orang dalam satu langganan. Kekuatan: mengubah perilaku; kelemahan: kurva belajar tinggi.
- **Monarch Money:** Core US$99,99/tahun, Plus US$199/tahun (dua tier sejak 2026); pasangan/rumah tangga termasuk tanpa biaya tambahan; AI assistant, rekap mingguan. Kelemahan: mahal, bisa terlalu banyak fitur untuk pemula.
- **Copilot Money:** US$95/tahun, Apple-first (web terbatas), tanpa Android; kategorisasi AI dinilai terbaik; pasangan butuh tier Family US$155/tahun atau dua langganan.
- **Rocket Money:** premium "bayar sesukamu" US$7–14/bulan; kuat di pembatalan langganan dan negosiasi tagihan.
- **PocketGuard:** US$74,99/tahun atau US$149,99 lifetime; **Goodbudget** (envelope, manual, cocok untuk pasangan) US$5,99–7,99/bulan; **EveryDollar** US$79,99/tahun; **Cleo** (chat-first) US$44,99/tahun.
- Aplikasi manual/offline seperti Realbyte Money Manager, Bluecoins, Monefy, dan Money Lover tetap populer di pasar non-AS karena bank sync tidak tersedia/kurang andal dan user menyukai pembelian sekali bayar.

Interpretasi: pasar AS/Eropa sudah padat di segmen "agregator bank premium ±US$100/tahun". Celah global yang lebih realistis untuk developer independen adalah **privacy-first/local-first, manual yang dipercepat AI, pasar non-AS (tanpa Plaid), dan keluarga lintas platform (Android + iOS)**.

**Ukuran pasar — angka sangat tidak konsisten.** Estimasi "personal finance apps market" berkisar dari US$31,7 miliar (Research Nester, 2025) hingga US$165,9 miliar (The Business Research Company, 2025), dengan CAGR 18–25%; sementara "personal finance software" hanya ±US$1,9 miliar (TBRC, 2025) dengan CAGR ~7%. Perbedaan ratusan kali lipat ini menunjukkan definisi yang berbeda (sebagian memasukkan pembayaran/investasi). **Jangan gunakan angka ini untuk keputusan bisnis**; yang konsisten hanya arah: tumbuh, Asia-Pasifik tercepat, segmen keluarga/rumah tangga disebut sebagai segmen dengan pertumbuhan tercepat (Verified Market Research).

### 4. Monetisasi

- **Benchmark RevenueCat (State of Subscription Apps 2026):** konversi trial-to-paid Day-35 median **10,7% untuk hard paywall vs 2,1% untuk freemium**; retensi tahunan hampir sama (27% vs 28%); pendapatan per install Day-60 hard paywall 8x lebih tinggi (US$3,09 vs US$0,38). Laporan 2025: 80%+ trial dimulai pada hari pertama membuka aplikasi — paywall harus ada di onboarding.
- Di India/Asia Tenggara, **29,2% aplikasi menggabungkan langganan + lifetime** (tertinggi dibanding wilayah lain) — relevan dengan Indonesia, di mana user menyukai "sekali bayar" (contoh: Money Lover lifetime).
- Aplikasi AI tidak berkonversi lebih baik, tetapi menghasilkan pendapatan lebih tinggi per pelanggan (RevenueCat 2025) — AI layak menjadi fitur premium, dengan model kuota (seperti Monly: 30 perintah gratis, 200 pada Premium) untuk mengendalikan biaya LLM.
- Pembayaran lokal: kartu kredit rendah penetrasinya; pastikan pembelian lewat Google Play Billing (yang mendukung pulsa/e-wallet) atau payment gateway lokal (QRIS/e-wallet/VA). Bot WhatsApp kecil yang mengandalkan transfer manual dan konfirmasi admin (misalnya CashMind) menunjukkan titik gesekan yang bisa diperbaiki.
- **Fitur yang terbukti bisa dijual:** bank/e-wallet sync (Money Lover memungut biaya terpisah), multi-dompet & multi-anggaran, laporan lanjutan & ekspor (Excel/PDF/Google Sheets), OCR struk, kuota AI, berbagi keluarga, tanpa iklan.

## Details: Must-have vs Differentiator

| Fitur | Status | Alasan |
|---|---|---|
| Input cepat (≤3 detik, satu layar, kalkulator bawaan, kategori pintar) | **Wajib** | Friksi input = penyebab churn nomor satu |
| Multi-dompet (tunai, bank, e-wallet) + transfer antar-dompet | **Wajib (gratis)** | User Indonesia memegang banyak e-wallet; top-up tidak boleh dihitung ganda |
| Anggaran per kategori + "sisa aman dibelanjakan" | **Wajib** | Tanpa ini aplikasi hanya jadi buku catatan |
| Transaksi berulang & pengingat tagihan/cicilan | **Wajib** | Mengurangi input dan mencegah telat bayar |
| Laporan/grafik sederhana, perbandingan bulan lalu | **Wajib** | Nilai yang paling cepat dirasakan |
| Backup/sinkron cloud + ekspor CSV/Excel | **Wajib** | Ketakutan data hilang; pelajaran Mint soal portabilitas data |
| PIN/biometrik, tanpa jual data | **Wajib** | Kepercayaan; UU PDP |
| Utang-piutang (termasuk "pinjam ke teman/keluarga") | **Wajib (Indonesia)** | Sangat umum; ada di Money Lover dan BukuWarung |
| Input via chat/voice/foto struk/screenshot mutasi (AI, bahasa gaul Indonesia) | **Diferensiator utama** | Menyelesaikan masalah input tanpa bergantung API bank |
| Mode keluarga berbasis peran (pengelola, pemberi jatah, anggota pencatat, anak) | **Diferensiator utama** | Segmen tumbuh cepat, belum dikuasai pemain lokal |
| Bot WhatsApp/Telegram terhubung ke aplikasi | **Diferensiator (Indonesia)** | WhatsApp sudah terpasang di semua HP; pasar sudah terbukti tetapi masih terfragmentasi |
| Pos lokal: arisan, zakat/infak/sedekah, THR/Lebaran, kiriman orang tua, paylater | **Diferensiator (Indonesia)** | Tidak dilayani aplikasi global |
| Metode anggaran (envelope/amplop, zero-based, 50/30/20) | **Diferensiator** | "Amplop" sangat cocok dengan budaya uang bulanan |
| Target tabungan & dana darurat | Semi-wajib | Motivasi jangka panjang |
| Multi-mata uang, mode offline/local-first | Diferensiator (global/traveler/privasi) | Nilai jual untuk pasar privacy-first |
| Auto-sync bank/e-wallet | **Tunda (Indonesia)**, pertimbangkan via mitra berlisensi di fase lanjut | Hambatan lisensi, rapuh, e-wallet 1 sesi |
| Investasi/net worth | Fase lanjut | Lebih relevan untuk segmen menengah atas |

## Recommendations

**Posisi yang disarankan:** "Asisten keuangan keluarga Indonesia yang cukup di-chat" — bukan sekadar pencatat, tetapi sistem amplop keluarga dengan input AI.

**MVP (prioritas, 3–4 bulan):**
1. Input super cepat di aplikasi + input bahasa natural ("gojek 23rb dari gopay", "gaji masuk 8jt").
2. Multi-dompet gratis (tunai/bank/e-wallet) dengan transfer & top-up yang tidak dihitung ganda.
3. Anggaran model "amplop" bulanan + angka "sisa aman hari ini"; alur "pulihkan anggaran" saat overspend tanpa rasa bersalah.
4. Ruang keluarga: undang pasangan, peran pengelola/anggota, amplop "uang bulanan", notifikasi saat anggota mencatat.
5. Pengingat tagihan & cicilan, transaksi berulang, pengingat harian ringan jika belum mencatat.
6. Laporan sederhana + ekspor Excel/CSV; backup cloud; PIN/biometrik; kebijakan privasi yang jelas sesuai UU PDP.

**Diferensiator fase 2 (berbayar):**
- Bot WhatsApp resmi (WhatsApp Business API) yang tersinkron ke aplikasi — tiap anggota keluarga cukup chat.
- OCR struk + e-statement PDF + screenshot mutasi e-wallet/bank (jalur "semi-otomatis" yang realistis di Indonesia).
- AI insight mingguan dalam bahasa santai ("jajan online naik 40% dibanding bulan lalu").
- Pos lokal: arisan, zakat (termasuk perhitungan nisab sederhana), THR/Lebaran, kiriman orang tua.
- Mode keuangan syariah (tanpa bunga, kategori ZISWAF) — literasi syariah 43% dan inklusi 13% menunjukkan ruang edukasi.

**Harga — Indonesia:** Gratis (pencatatan + multi-dompet + 1 anggota keluarga) → Premium Rp19–29 ribu/bulan atau Rp149–199 ribu/tahun (keluarga hingga 5 orang, OCR, kuota AI, ekspor, tanpa iklan) → Lifetime ±Rp299 ribu tanpa kuota AI tak terbatas (AI dijual sebagai add-on kuota). Tampilkan paywall di onboarding dengan trial 7 hari; gunakan Google Play Billing + QRIS/e-wallet.

**Harga — Global:** US$3,99–5,99/bulan atau US$29–49/tahun (di bawah YNAB/Monarch), lifetime US$79–99; jual dengan pesan "privacy-first, tanpa bank login, AI-assisted, paket keluarga tanpa biaya per orang, Android + iOS". Sasar pasar tanpa Plaid yang andal (Asia Tenggara, Amerika Latin, Timur Tengah) dan user manual yang meninggalkan Mint/Money Manager.

**Catatan eksekusi:**
- Ukur retensi D1/D7/D30 dan "jumlah hari mencatat per minggu" sebagai metrik utama, bukan jumlah download.
- Mulai dari niche (pasangan muda/ibu pengelola keuangan keluarga) di mana pesan pemasaran lebih tajam daripada "aplikasi keuangan untuk semua".
- Kendalikan biaya AI dengan model kecil/murah untuk parsing dan kuota per paket.

## Caveats
- Banyak statistik retensi/churn aplikasi budgeting yang beredar berasal dari blog vendor (Strategia-X, SpendTrak, BudgetSmart, dll.) tanpa metodologi terbuka; arah temuannya konsisten, tetapi angkanya tidak boleh dikutip sebagai fakta.
- Estimasi ukuran pasar global saling bertentangan hingga ratusan kali lipat karena definisi berbeda.
- Survei e-wallet (Jakpat, Ipsos, Populix) memakai metodologi berbeda dan menghasilkan peringkat berbeda.
- Angka "60% rumah tangga dikelola perempuan" adalah kutipan sekunder dari SUSENAS 2020 yang belum kami verifikasi di sumber primer BPS.
- Status akses API informasi rekening di bawah SNAP untuk pihak ketiga, harga terbaru CatatAja/Jurnal Cuan, dan status bisnis Sribuu/Ayoconnect belum dapat dipastikan; lakukan verifikasi langsung (hubungi bank/agregator) sebelum mengambil keputusan teknis.
- Riset ini tidak mencakup scraping ulasan Play Store secara kuantitatif; langkah berikutnya yang disarankan adalah menganalisis 500–1.000 ulasan bintang 1–3 dari Money Lover, Finku, Sribuu, dan Catatan Keuangan Harian untuk memvalidasi prioritas fitur.