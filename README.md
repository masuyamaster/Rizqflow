# Rizqflow

> Rezeki mengalir, setiap hak terpenuhi.

Aplikasi finansial berbasis **hak**: rezeki yang masuk dibagi ke ruang-ruang kehidupan (Memberi, Diri, Keluarga, dan peran lain) lewat aturan alokasi, lalu aplikasi memastikan tidak ada hak yang terabaikan. Bukan sekadar pencatat pengeluaran.

Inti aplikasinya universal (role-based budgeting). Modul Islami, yaitu tracker zakat mal dengan nisab dan haul kalender Hijriyah, bersifat opsional dan menjadi pembeda utama.

**Status:** Tahap 0 dan 1 hampir selesai, Tahap 2 dimulai: kerangka proyek Android sudah bisa dibangun dan `Money` sudah ada. Platform: Android, dirilis di Google Play Store.

## Dokumen

| Dokumen | Isi |
|---|---|
| [docs/konsep.md](docs/konsep.md) | Masalah, posisi produk, cakupan MVP, arsitektur, model data, keputusan |
| [docs/monetisasi.md](docs/monetisasi.md) | Model bisnis: Gratis / Pro (sekali bayar) / Sync (langganan) |
| [docs/roadmap.md](docs/roadmap.md) | 10 tahap pengerjaan |
| [docs/ui-flow.md](docs/ui-flow.md) | Layar S01–S28 dan flow F1–F9 |
| [docs/wireframe.md](docs/wireframe.md) | Wireframe teks seluruh layar S01–S28, termasuk keadaan kosong |
| [docs/design/](docs/design/README.md) | Design tokens dan prototipe klik (buka `prototype.html`) |

Progres dan tugas dilacak di Notion (hub Rizqflow di Ruang Finansial); lihat [CLAUDE.md](CLAUDE.md).

## Membangun

Butuh JDK 17, Android SDK, dan Android Studio versi terbaru (proyek memakai AGP 9.4). Path SDK ditulis di `local.properties` (tidak masuk git), misalnya `sdk.dir=C:/Users/<nama>/AppData/Local/Android/Sdk`.

```
./gradlew :domain:test          # tes logika inti di JVM
./gradlew :app:assembleDebug    # APK debug
```
