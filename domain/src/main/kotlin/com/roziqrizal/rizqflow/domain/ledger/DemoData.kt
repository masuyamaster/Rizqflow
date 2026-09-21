package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * Data contoh untuk mode demo (S22): satu ruang kerja lengkap yang mengisi database demo yang
 * terpisah dari data pengguna. Menyusun bulan lalu dan bulan ini supaya ketiga status ruang terlihat
 * (Memberi terpenuhi, Diri berjalan, Keluarga perlu perhatian) dan navigator bulan punya isi.
 *
 * Semua tanggal dijepit ke hari ini: tidak ada transaksi di masa depan walau hari ini masih awal bulan.
 * Semua lewat layanan biasa, jadi data contoh tunduk pada aturan yang sama dengan data asli. Kegagalan
 * berarti kesalahan program (bukan masukan pengguna), sehingga dilaporkan sebagai [IllegalStateException].
 */
class DemoData(
    private val setup: WorkspaceSetup,
    private val ledger: LedgerService,
    private val management: ManagementService,
    private val favorites: FavoriteService,
    private val rooms: RoomRepository,
) {
    suspend fun seed(today: LocalDate) {
        ok(setup.setUp(RoomTemplate.TIGA_HAK, FirstAccount("Tunai", AccountKind.CASH, Money.rupiah(500_000))))
        val tunai = firstAccount()
        val bank = ok(management.addAccount(NewAccount("Bank Jago", AccountKind.BANK, Money.rupiah(2_000_000))))
        val gopay = ok(management.addAccount(NewAccount("GoPay", AccountKind.EWALLET, Money.rupiah(250_000))))

        val thisMonth = YearMonth.from(today)
        val lastMonth = thisMonth.minusMonths(1)
        fun day(month: YearMonth, d: Int): LocalDate = month.atDay(minOf(d, month.lengthOfMonth())).let { if (it > today) today else it }

        // Bulan lalu: sudah berakhir, jadi Mencukupi dinilai di akhir bulan dan yang belum tercapai netral.
        income(bank, 8_000_000, day(lastMonth, 1), "Gaji bulan lalu")
        expense(bank, "Memberi", "Sedekah", 500_000, day(lastMonth, 6), "Sedekah Jumat")
        expense(bank, "Diri", "Dana darurat", 2_400_000, day(lastMonth, 10), "Dana darurat")
        expense(bank, "Keluarga", "Belanja bulanan", 2_000_000, day(lastMonth, 4), "Belanja bulanan")
        expense(bank, "Keluarga", "Listrik dan air", 600_000, day(lastMonth, 8), "Token listrik dan PDAM")
        expense(bank, "Keluarga", "Sekolah", 1_500_000, day(lastMonth, 12), "SPP")

        // Bulan ini.
        income(bank, 8_500_000, day(thisMonth, 1), "Gaji")
        expense(bank, "Memberi", "Sedekah", 850_000, day(thisMonth, 2), "Sedekah dan infak")
        expense(bank, "Diri", "Investasi", 1_500_000, day(thisMonth, 3), "Reksa dana")
        expense(bank, "Diri", "Dana darurat", 300_000, day(thisMonth, 9), "Dana darurat")
        expense(bank, "Keluarga", "Belanja bulanan", 2_000_000, day(thisMonth, 2), "Belanja bulanan")
        expense(bank, "Keluarga", "Listrik dan air", 650_000, day(thisMonth, 5), "Token listrik")
        expense(bank, "Keluarga", "Sekolah", 1_500_000, day(thisMonth, 6), "SPP")
        expense(bank, "Keluarga", "Belanja bulanan", 250_000, day(thisMonth, 10), "Belanja pasar")
        ok(ledger.recordTransfer(NewTransfer(Money.rupiah(200_000), bank, gopay, day(thisMonth, 7), "Isi saldo GoPay")))

        // Favorit yang sering dipakai; memakainya sekali-dua kali membuat urutannya bermakna.
        val keluarga = requireNotNull(room("Keluarga"))
        val lainLain = requireNotNull(category(keluarga.id, "Lain-lain"))
        val kopi = ok(favorites.create("Kopi", Money.rupiah(15_000), keluarga.id, lainLain.id, tunai))
        val parkir = ok(favorites.create("Parkir", Money.rupiah(3_000), keluarga.id, lainLain.id, tunai))
        ok(favorites.create("Jajan", Money.rupiah(20_000), keluarga.id, lainLain.id, gopay))
        ok(favorites.create("Bensin", Money.rupiah(30_000), keluarga.id, lainLain.id, gopay))
        repeat(3) { ok(favorites.use(kopi.id, today)) }
        repeat(2) { ok(favorites.use(parkir.id, today)) }
    }

    private suspend fun firstAccount(): AccountId = management.accountOverview().active.first().account.id

    private suspend fun room(name: String): Room? = rooms.activeRooms().firstOrNull { it.name == name }

    private suspend fun category(roomId: RoomId, name: String): Category? =
        rooms.categories(roomId).firstOrNull { it.name == name }

    private suspend fun income(account: AccountId, amount: Long, date: LocalDate, note: String) {
        ok(ledger.recordIncome(NewIncome(Money.rupiah(amount), account, "Gaji", date, note)))
    }

    private suspend fun expense(account: AccountId, roomName: String, categoryName: String, amount: Long, date: LocalDate, note: String) {
        val room = checkNotNull(room(roomName)) { "Ruang $roomName tidak ada" }
        val category = checkNotNull(category(room.id, categoryName)) { "Kategori $categoryName tidak ada" }
        ok(ledger.recordExpense(NewExpense(Money.rupiah(amount), account, room.id, category.id, date, note)))
    }

    private fun <T> ok(result: LedgerResult<T>): T = when (result) {
        is LedgerResult.Success -> result.value
        is LedgerResult.Failure -> error("Data contoh gagal dibuat: ${result.error}")
    }
}
