# Rizqflow — Desain

Arah visual **opsi A** (identitas homepage roziqrizal.com), diputuskan 2026-09-19. Folder ini berisi design tokens dan prototipe klik untuk layar kunci. Prototipe ini untuk menilai tampilan dan alur, **bukan kode produksi**.

## Isi folder

| File | Fungsi |
|---|---|
| [tokens.css](tokens.css) | Design tokens: satu tempat untuk warna, font, jarak, bentuk, dan gerak |
| [prototype.html](prototype.html), [prototype.css](prototype.css), [prototype.js](prototype.js) | Prototipe klik (HTML, CSS, dan JavaScript murni, tanpa build) |

## Cara membuka

Klik dua kali `prototype.html`. Font Manrope dan Libre Caslon Text dimuat dari Google Fonts, jadi butuh internet; tanpa internet prototipe tetap jalan dengan font cadangan.

Kontrol di panel kiri: tema (otomatis, terang, gelap), ukuran teks (100%, 150%, 200%), dan reset data contoh.

Langsung ke layar tertentu lewat parameter URL: `prototype.html?screen=denah&theme=dark&scale=2`.

| `screen=` | Layar |
|---|---|
| `denah`, `transaksi`, `ruang`, `lainnya` | Tab utama |
| `alokasi` (tambah `&edit=1` untuk mode Ubah sekali ini) | S07 Pratinjau alokasi |
| `catat`, `catat-keluar` (opsional `&amount=2000000`) | S06 Catat transaksi |
| `detail-memberi`, `detail-diri`, `detail-keluarga` | S11 Detail ruang |
| `haul` | S16 Kartu haul |
| `pro` | S21 Paywall |

## Cakupan prototipe

**Sudah ada:** S05 Denah, S06 Catat (pemasukan dan pengeluaran), S07 Pratinjau alokasi (termasuk Ubah sekali ini), S11 Detail ruang, S16 Kartu haul, S21 Paywall. Versi ringkas: S08 Transaksi, S10 Ruang, S18 Lainnya.

**Flow yang bisa dicoba:**
- **F2** rezeki masuk dialirkan, penuh: bar dan cincin bergerak, status ruang berubah.
- **F3** pengeluaran, penuh: banner lembut saat jatah terlampaui (tidak pernah memblokir) dan snackbar Urungkan.
- **F5** sebagian: ruang Memberi lalu kartu Zakat mal lalu kartu haul.
- **F6** hanya tampilan sheet; tidak ada pembayaran.

**Belum ada:** onboarding S01–S04 (F1), S09, S12–S15, S17, S19, S20, S22, S23; flow F4 dan F7.

**Diuji:** skrip klik otomatis (Chrome headless) menjalankan F2 dan F3 termasuk Urungkan tanpa error konsol; tampilan diperiksa di mode terang, gelap, dan teks 150% dan 200%.

## Keputusan desain

- **Chrome dari homepage, warna data terpisah.** Warna latar, teks, tombol, dan kartu memakai palet sage homepage. Warna identitas ruang (bar alokasi dan cincin; 3 slot inti plus 5 slot custom) datang dari palet data yang divalidasi, karena sage yang pucat tidak cukup untuk membedakan tiga ruang.
- **Tenang, bukan panik.** Peringatan memakai kuning lembut dengan ikon dan teks, bukan merah. Melewati jatah tidak memblokir.
- **Status = ikon + teks.** Terpenuhi (centang), Berjalan (jam), Perlu perhatian (segitiga). Warna hanya penguat.
- **Angka besar selalu Manrope**, bukan serif; Libre Caslon Text hanya untuk judul layar dan judul bagian.
- **Aturan bar dan cincin** (dari panduan visualisasi data): tebal 16px, jeda 2px antar segmen, ujung data membulat 4px, jalur cincin berupa warna ruang yang lebih muda, legenda selalu ada, teks tidak diwarnai warna seri.
- **Denah = grid kartu**, bukan ilustrasi denah harfiah, supaya tetap rapi saat ruangnya banyak.
- **Teks besar (sp) ikut skala, ukuran elemen (dp) tidak.** Angka hero dibatasi 1,25x dan label navigasi 1,3x; kartu ruang menjadi 1 kolom di 150% ke atas. Pengujian 200% menemukan angka hero pecah di tengah, dan ini yang diperbaiki.
- **Status ruang mengikuti tipe ruang** (usulan di [konsep.md](../konsep.md), perlu konfirmasi): Menunaikan dan Menumbuhkan terpenuhi saat penggunaan mencapai jatah; Mencukupi masuk Perlu perhatian saat pemakaian 85% atau lebih dari jatah.

## Hasil validasi warna

Dijalankan dengan `validate_palette.js` (skill visualisasi data), bentuk `--pairs all` karena kartu ruang bisa bersebelahan di grid.

| Mode | Memberi | Diri | Keluarga | Permukaan | Hasil | CVD terburuk | Normal terburuk | Kontras |
|---|---|---|---|---|---|---|---|---|
| Terang | `#3b6fa8` | `#c4653a` | `#2a9a80` | `#ffffff` | Lolos semua | ΔE 9,7 | ΔE 15,9 | semua ≥ 3:1 |
| Gelap | `#4384d0` | `#cf6a3a` | `#22a37a` | `#1e201e` | Lolos semua | ΔE 8,7 | ΔE 18,2 | semua ≥ 3:1 |

Kontras teks (WCAG, target ≥ 4,5:1): teks utama di kartu terang 17,1; teks sekunder 9,4; sage utama di kartu terang 6,5; di mode gelap teks utama 12,8, teks sekunder 9,6, sage utama 9,6. Semua lolos.

Status memakai palet tetap yang reserved (good `#0ca30c`, warning `#fab219`, serious `#ec835a`, critical `#d03b3b`), selalu dengan ikon dan label. Prototipe hanya memakai good dan warning; critical sengaja tidak dipakai.

### Warna ruang custom: slot 4 sampai 8

**Diputuskan 2026-09-26.** Ruang custom (yang dibuat pengguna) mendapat 5 slot warna tambahan, jadi total 8 slot. Hue sengaja menghindari hijau (bentrok dengan sage chrome dan status good) dan merah kritis (status critical).

| Slot | Nama | Terang | Gelap |
|---|---|---|---|
| 4 | Violet | `#af6dec` | `#7547ee` |
| 5 | Plum | `#a04177` | `#aa558d` |
| 6 | Langit | `#099acf` | `#1fa3bb` |
| 7 | Emas tua | `#998214` | `#966c13` |
| 8 | Mawar | `#de4f74` | `#db426d` |

Validasi (`validate_palette.js`, permukaan terang `#ffffff` dan `#faf9f7`, gelap `#1e201e`):

| Cakupan | Mode | Hasil | CVD terburuk | Normal terburuk | Kontras |
|---|---|---|---|---|---|
| Slot 1–5, `--pairs all` | Terang | Lolos semua | ΔE 8,9 | ΔE 15,8 | semua ≥ 3:1 |
| Slot 1–5, `--pairs all` | Gelap | Lolos semua | ΔE 8,4 | ΔE 15,5 | semua ≥ 3:1 |
| Slot 1–8, adjacent | Terang | Lolos semua | ΔE 8,2 | ΔE 18,9 | semua ≥ 3:1 (di kedua permukaan terang) |
| Slot 1–8, adjacent | Gelap | Lolos semua | ΔE 8,6 | ΔE 18,9 | semua ≥ 3:1 |
| Slot 1–8, `--pairs all` | Terang dan gelap | **Gagal** (dua warna bisa sulit dibedakan) | ΔE 1,9 terang, 2,7 gelap | ΔE 10,7 terang, 9,8 gelap | — |

Artinya: slot 1–5 aman bersebelahan di urutan apa pun, dan itu persis batas ruang Gratis (5 ruang). Slot 6–8 hanya aman bila bersebelahan dengan tetangga slotnya. Delapan warna tidak mungkin semuanya lolos all-pairs pada batas ini (batas metode, bukan salah pilih), jadi aturan di bawah wajib diikuti.

**Aturan warna ruang**

1. **Warna melekat pada ruang, bukan pada urutan.** Slot disimpan di ruang itu (`colorSlot`) dan tidak berubah saat ruang lain ditambah, diurutkan, atau diarsipkan.
2. **Ruang inti** memakai slot 1–3 secara tetap (Memberi, Diri, Keluarga).
3. **Ruang custom** otomatis mendapat slot bebas terkecil di antara 4–8. Di layar S10, pengguna boleh mengganti ke slot bebas lain lewat pemilih lima titik. Tidak ada pemilih warna bebas; hanya slot yang sudah divalidasi. Slot yang sedang dipakai ruang aktif tidak muncul di pemilih, jadi tidak ada dua ruang aktif dengan warna sama.
4. **Mengarsipkan ruang membebaskan slotnya.** Riwayat ruang yang diarsipkan tampil netral dengan nama dan ikon, bukan warna lama. Memulihkan ruang mengambil slot bebas terkecil.
5. **Ruang ke-9 dan seterusnya (Pro)** memakai warna netral `--rf-room-neutral` dengan ikon dan nama sebagai identitas. Warna tidak pernah diputar ulang atau dibuat otomatis.
6. **Bar alokasi selalu diurutkan menurut slot**, bukan menurut urutan kustom pengguna di Denah. Dengan begitu segmen bersebelahan hanya pasangan adjacent yang sudah lolos. Ruang netral digabung menjadi satu segmen "Ruang lain".
7. **Identitas tidak boleh hanya lewat warna.** Setiap ruang selalu tampil dengan ikon dan nama (kartu, legenda, dan label langsung di bar). Ruang custom wajib punya ikon (usulan: dipilih dari daftar bawaan di S10, dengan ikon bawaan bila tidak dipilih).
8. **Warna ruang tidak dipakai untuk status.** Status tetap memakai palet status dan ikon plus teks.

## Peta ke Jetpack Compose

Nama peran warna di `tokens.css` sama dengan Material 3, jadi langsung dipetakan ke `ColorScheme`:

```kotlin
val RizqflowLight = lightColorScheme(
    primary = Color(0xFF4D6359),            // --rf-primary
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8FA79B),
    onPrimaryContainer = Color(0xFF273C33),
    inversePrimary = Color(0xFFB3CCBF),
    secondary = Color(0xFF48626E),
    secondaryContainer = Color(0xFFCBE7F5),
    background = Color(0xFFFAF9F7),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1B1C1B),
    onSurfaceVariant = Color(0xFF424845),
    outline = Color(0xFF727874),
    outlineVariant = Color(0xFFC2C8C3),
    error = Color(0xFFBA1A1A),
    // ...sisanya dari tokens.css (surfaceContainer*, inverse*, onError*)
)
```

| Token | Di Compose |
|---|---|
| `--rf-room-1..8`, `--rf-room-neutral`, `--rf-status-*` | Objek tersendiri lewat `CompositionLocal` (bukan bagian `ColorScheme`); nilai terang dan gelap berbeda |
| `--rf-font-ui` (Manrope), `--rf-font-display` (Libre Caslon Text) | `FontFamily` dari berkas font atau Google Fonts (lisensi OFL); `display` hanya untuk judul |
| `--rf-size-*` | `sp` di `Typography`; angka hero dibatasi dengan `Density(fontScale = min(fontScale, 1.25f))` |
| Bar tumpuk | `Row` dengan `weight` per segmen dan `Arrangement.spacedBy(2.dp)` |
| Cincin | `Canvas` dengan `drawArc`; animasi `animateFloatAsState` (900 ms, kurva `0.2, 0, 0, 1`) |
| Gerak | Hormati pengaturan animasi sistem (durasi animasi 0) |

## Yang perlu dikonfirmasi setelah melihat prototipe

1. **Mode gelap.** Ini turunan dari palet sage karena homepage belum punya mode gelap. Nilainya bisa diubah di satu blok di `tokens.css`.
2. **Ambang 85%** untuk Perlu perhatian di ruang Mencukupi.
3. **Tanggal Hijriyah** pada layar S05 dan S16 hanyalah perkiraan; metode kalender masih keputusan terbuka.
4. **Semua angka** (harga emas, harta, harga Pro Rp 129.000) hanya contoh.

## Keterbatasan

Data tidak disimpan (reset saat halaman dimuat ulang), tanggal tetap September 2026, dan tidak ada pembayaran atau notifikasi sungguhan. Prototipe HTML tidak menguji nuansa native (gestur, haptik, ukuran sentuh); itu dinilai saat implementasi Compose di Tahap 3.
