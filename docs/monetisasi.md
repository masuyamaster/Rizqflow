# Rizqflow — Monetisasi

Dokumen ini mencatat model bisnis yang sudah disepakati (2026-09-19). Angka harga bersifat **indikatif untuk diuji**, bukan final.

## Keputusan

**Freemium dengan Pro sekali bayar.** Langganan hanya muncul untuk Sync (fase 2), karena hanya di situ ada biaya server yang berulang.

## Prinsip pembagian

1. **Versi gratis harus membuktikan janji utama.** Pengguna gratis bisa membagi rezeki ke hak-hak dan melihat denahnya. Kalau tidak, konsepnya tidak pernah terasa.
2. **Yang berbayar adalah skala dan kenyamanan, bukan kewajiban.** Zakat itu kewajiban agama, jadi perhitungan dasarnya (nisab dan haul satu profil) tetap gratis. Menaruhnya di balik paywall merusak brand.
3. **Batasi dimensi, jangan jumlah transaksi.** Membatasi "maksimal 50 transaksi" membuat pengguna kesal. Membatasi jumlah ruang atau kerumitan aturan terasa wajar.

## Pembagian fitur

| Tingkat | Isi | Harga indikatif |
|---|---|---|
| **Gratis** | Tiga ruang inti (Memberi, Diri, Keluarga) plus beberapa ruang peran; transaksi tak terbatas; aturan alokasi persentase dasar; dashboard Denah; nisab dan haul untuk satu profil harta (harga emas input manual); mode donasi persentase; PIN/biometrik; ekspor CSV; backup lokal terenkripsi | Rp 0 |
| **Pro** (sekali bayar) | Ruang peran tak terbatas dengan sistem khusus per peran (Trader: batas risiko per trade, Investor: jadwal DCA); aturan alokasi lanjutan (prioritas, batas atas, sisa mengalir ke ruang lain); multi-profil haul dengan pengingat; harga emas otomatis; laporan dan insight bulanan/tahunan (PDF); multi-mata uang; widget dan tema | Rp 99–149 ribu |
| **Sync** (langganan, fase 2) | Backup dan sinkron antar-perangkat terenkripsi; ruang keluarga bersama (suami-istri) | Rp 15–25 ribu per bulan atau Rp 149 ribu per tahun |

Yang **sengaja tidak** dipaywall:

- **Keamanan** (PIN/biometrik): data keuangan yang tidak terlindungi bukan fitur premium.
- **Ekspor data dan backup lokal**: data itu milik pengguna, dan ini membangun kepercayaan.
- **Dasar zakat** (nisab, haul satu profil, tunaikan zakat).

## Kenapa sekali bayar dulu, bukan langganan

- Aplikasi offline-first dan tanpa server, jadi tidak ada biaya berulang yang membenarkan langganan. Pengguna akan bertanya kenapa membayar tiap bulan untuk sesuatu yang berjalan di ponselnya sendiri.
- Pengguna Indonesia sensitif terhadap tagihan berulang; pembelian sekali bayar lebih mudah dijual.
- Langganan baru masuk akal untuk Sync: ada biaya server dan nilai berkelanjutan. **Ruang keluarga bersama** adalah kandidat terkuat karena paling terasa bagi peran Keluarga.

## Yang dihindari

- **Iklan.** Aplikasi yang menyimpan data keuangan dan zakat, ditambah iklan, merusak kepercayaan; penghasilannya juga kecil.
- **Paywall di fitur inti zakat atau keamanan.**

## Dampak arsitektur

Rancang **lapisan entitlement** dari awal: satu tempat yang menjawab "fitur ini boleh dipakai user ini?". Batas ruang, aturan lanjutan, dan multi-profil haul semuanya bertanya ke sana, sehingga model harga bisa berubah tanpa membongkar kode. Ini juga selaras dengan arsitektur modul yang bisa ditukar, dan menjadi bukti desain di portofolio.

Konsekuensi yang diterima: karena aplikasi offline, penguncian di sisi klien bisa dilewati oleh pengguna yang mahir. Untuk harga sekali bayar dengan taruhan kecil, ini bisa diterima; verifikasi cukup lewat status pembelian dari toko.

## Catatan realistis

Aplikasi niche seperti ini kemungkinan menghasilkan uang kecil. Anggap monetisasi sebagai **eksperimen produk yang nyata** sekaligus bahan cerita portofolio (merancang dan menguji model bisnis), bukan sumber penghasilan yang bisa diandalkan.

## Yang masih perlu diputuskan

- [ ] **Pengingat haul.** Usulan: untuk satu profil tetap gratis (konsisten dengan prinsip 2), pengingat multi-profil masuk Pro. Di tabel atas, "multi-profil haul dengan pengingat" ada di Pro, jadi butuh konfirmasi.
- [ ] **Batas ruang gratis.** Usulan: 5 ruang total (3 inti + 2 peran). Angka ini masih tebakan.
- [ ] **Harga final Pro dan Sync**, sebaiknya berdasarkan uji minat (landing page atau daftar tunggu) sebelum banyak kode ditulis.
- [x] **Platform**: Android saja (2026-09-19), jadi pembelian lewat Google Play Billing: produk in-app sekali beli untuk Pro, langganan untuk Sync di fase 2.
- [ ] **Kewajiban di luar kode**: akun developer, profil pembayaran, dan pajak atas pendapatan aplikasi (lihat [roadmap.md](roadmap.md), Tahap 8).

## Titik penguncian (rancangan)

| Aksi pengguna | Gratis | Pro |
|---|---|---|
| Menambah ruang peran | Sampai batas kecil | Tak terbatas, dengan sistem per peran |
| Aturan alokasi | Persentase dasar | Prioritas, batas atas, sisa mengalir ke ruang lain |
| Profil harta zakat | Satu profil | Multi-profil (emas, tabungan, usaha, anggota keluarga) |
| Harga emas | Input manual | Otomatis |
| Laporan | Ringkasan bulanan di aplikasi | Laporan dan insight bulanan/tahunan, PDF |
| Lainnya | – | Multi-mata uang, widget, tema |

Alur saat pengguna menyentuh fitur terkunci ada di [ui-flow.md](ui-flow.md) (flow F6 dan layar S21).
