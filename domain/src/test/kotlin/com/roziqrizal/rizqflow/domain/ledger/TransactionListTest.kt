package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionListTest {

    private val sept = YearMonth.of(2026, 9)

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun lister(f: LedgerFixture) = TransactionLister(f.store, f.store, f.store)

    private fun list(f: LedgerFixture, filter: ListFilter = ListFilter(sept)) = runSuspend { lister(f).list(filter) }

    private fun income(f: LedgerFixture, day: Int, amount: Long, note: String? = null, source: String? = "Gaji", accountId: AccountId = f.account.id, month: Int = 9) =
        runSuspend { f.ledger.recordIncome(NewIncome(rupiah(amount), accountId, source, LocalDate.of(2026, month, day), note)) }

    private fun expense(f: LedgerFixture, day: Int, amount: Long, room: String = "Keluarga", category: String = "Belanja bulanan", note: String? = null, accountId: AccountId = f.account.id, month: Int = 9) =
        runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(amount), accountId, f.room(room).id, f.category(room, category).id, LocalDate.of(2026, month, day), note))
        }

    private fun titles(l: TransactionListing) = l.groups.flatMap { g -> g.rows.map { it.transaction.note ?: it.transaction.incomeSource ?: "?" } }

    // ------------------------------------------------------------------ pengelompokan dan urutan

    @Test
    fun `bulan tanpa transaksi menghasilkan daftar kosong`() {
        val f = LedgerFixture().standard()
        assertTrue(list(f).isEmpty)
    }

    @Test
    fun `dikelompokkan per tanggal dengan yang terbaru dulu`() {
        val f = LedgerFixture().standard()
        income(f, 1, 8_500_000, "Gaji September")
        expense(f, 18, 150_000, note = "Belanja pasar")
        expense(f, 18, 200_000, category = "Listrik dan air", note = "Token listrik")
        expense(f, 12, 1_020_000, room = "Diri", category = "Dana darurat", note = "Dana darurat")

        val hasil = list(f)

        assertEquals(listOf(18, 12, 1), hasil.groups.map { it.date.dayOfMonth })
        assertEquals(4, hasil.count)
    }

    @Test
    fun `dalam satu hari yang dicatat terakhir tampil di atas`() {
        val f = LedgerFixture().standard()
        expense(f, 18, 100, note = "pertama")
        f.now += 10
        expense(f, 18, 200, note = "kedua")
        f.now += 10
        expense(f, 18, 300, note = "ketiga")

        assertEquals(listOf("ketiga", "kedua", "pertama"), titles(list(f)))
    }

    @Test
    fun `hanya transaksi bulan yang dipilih, termasuk tanggal pertama dan terakhir`() {
        val f = LedgerFixture().standard()
        income(f, 1, 1_000, "awal")
        income(f, 30, 2_000, "akhir")
        income(f, 31, 3_000, "agustus", month = 8)
        income(f, 1, 4_000, "oktober", month = 10)

        assertEquals(listOf("akhir", "awal"), titles(list(f)))
        assertEquals(listOf("agustus"), titles(list(f, ListFilter(YearMonth.of(2026, 8)))))
        assertEquals(listOf("oktober"), titles(list(f, ListFilter(YearMonth.of(2026, 10)))))
    }

    // ------------------------------------------------------------------ isi baris

    @Test
    fun `baris pengeluaran memuat kategori ruang dan akun`() {
        val f = LedgerFixture().standard()
        expense(f, 18, 150_000, note = "Belanja pasar")

        val row = list(f).groups.single().rows.single()

        assertEquals("Belanja bulanan", row.categoryName)
        assertEquals("Keluarga", row.room?.name)
        assertEquals("Dompet", row.accountName)
        assertNull(row.toAccountName)
        assertNull(row.allocatedRoomCount)
    }

    @Test
    fun `baris pemasukan memuat jumlah ruang yang menerima bagian`() {
        val f = LedgerFixture().standard()
        income(f, 1, 8_500_000)
        income(f, 2, 1, note = "kecil") // Rp 1: hanya satu ruang yang kebagian

        val rows = list(f).groups.flatMap { it.rows }

        assertEquals(1, rows.first { it.transaction.note == "kecil" }.allocatedRoomCount)
        assertEquals(3, rows.first { it.transaction.note == null }.allocatedRoomCount)
    }

    @Test
    fun `pemasukan tanpa aturan tidak punya ruang penerima`() {
        val f = LedgerFixture()
        runSuspend { f.setup.setUp(RoomTemplate.KOSONG, FirstAccount("Dompet", com.roziqrizal.rizqflow.domain.model.AccountKind.CASH, rupiah(0))) }
        income(f, 5, 1_000)
        assertEquals(0, list(f).groups.single().rows.single().allocatedRoomCount)
    }

    @Test
    fun `baris transfer memuat akun asal dan tujuan`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank Jago")
        runSuspend { f.ledger.recordTransfer(NewTransfer(rupiah(500_000), f.account.id, bank.id, LocalDate.of(2026, 9, 3), "Tarik tunai")) }

        val row = list(f).groups.single().rows.single()

        assertEquals("Dompet", row.accountName)
        assertEquals("Bank Jago", row.toAccountName)
        assertNull(row.room)
    }

    @Test
    fun `nama akun ruang dan kategori terarsip tetap tampil di riwayat`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank Lama")
        expense(f, 10, 5_000, accountId = bank.id, note = "lama")
        f.store.accountRows[bank.id] = bank.copy(archived = true)
        val keluarga = f.room("Keluarga")
        f.store.roomRows[keluarga.id] = keluarga.copy(archived = true)
        val kategori = f.category("Keluarga", "Belanja bulanan")
        f.store.categoryRows[kategori.id] = kategori.copy(archived = true)

        val row = list(f).groups.single().rows.single()

        assertEquals("Bank Lama", row.accountName)
        assertEquals("Keluarga", row.room?.name)
        assertEquals("Belanja bulanan", row.categoryName)
    }

    // ------------------------------------------------------------------ filter

    private fun campuran(): LedgerFixture {
        val f = LedgerFixture().standard()
        f.addAccount("Bank Jago")
        income(f, 1, 8_500_000, "Gaji September")
        expense(f, 5, 150_000, note = "Belanja pasar")
        expense(f, 6, 1_020_000, room = "Diri", category = "Dana darurat", note = "Tabung")
        runSuspend { f.ledger.recordTransfer(NewTransfer(rupiah(500_000), f.account.id, f.store.accountRows.values.last().id, LocalDate.of(2026, 9, 7), "Tarik tunai")) }
        return f
    }

    @Test
    fun `filter jenis`() {
        val f = campuran()
        assertEquals(listOf("Gaji September"), titles(list(f, ListFilter(sept, kinds = setOf(TransactionKind.INCOME)))))
        assertEquals(2, list(f, ListFilter(sept, kinds = setOf(TransactionKind.EXPENSE))).count)
        assertEquals(listOf("Tarik tunai"), titles(list(f, ListFilter(sept, kinds = setOf(TransactionKind.TRANSFER)))))
        assertEquals(3, list(f, ListFilter(sept, kinds = setOf(TransactionKind.INCOME, TransactionKind.EXPENSE))).count)
        assertEquals(4, list(f, ListFilter(sept)).count)
    }

    @Test
    fun `filter akun menyertakan transfer masuk dan keluar`() {
        val f = campuran()
        val bank = f.store.accountRows.values.last()

        val dompet = list(f, ListFilter(sept, accountId = f.account.id))
        val jago = list(f, ListFilter(sept, accountId = bank.id))

        assertEquals(4, dompet.count)
        assertEquals(listOf("Tarik tunai"), titles(jago), "transfer tampil di akun tujuan juga")
    }

    @Test
    fun `filter ruang dan kategori`() {
        val f = campuran()
        assertEquals(listOf("Tabung"), titles(list(f, ListFilter(sept, roomId = f.room("Diri").id))))
        assertEquals(listOf("Belanja pasar"), titles(list(f, ListFilter(sept, categoryId = f.category("Keluarga", "Belanja bulanan").id))))
        assertTrue(list(f, ListFilter(sept, roomId = f.room("Memberi").id)).isEmpty)
    }

    @Test
    fun `filter bekerja bersama`() {
        val f = campuran()
        val hasil = list(f, ListFilter(sept, kinds = setOf(TransactionKind.EXPENSE), roomId = f.room("Keluarga").id, accountId = f.account.id))
        assertEquals(listOf("Belanja pasar"), titles(hasil))
        assertTrue(list(f, ListFilter(sept, kinds = setOf(TransactionKind.INCOME), roomId = f.room("Keluarga").id)).isEmpty)
    }

    @Test
    fun `penanda filter aktif dan jumlah filter di sheet`() {
        assertTrue(!ListFilter(sept).isNarrowed)
        assertTrue(ListFilter(sept, query = "x").isNarrowed)
        assertTrue(ListFilter(sept, kinds = setOf(TransactionKind.INCOME)).isNarrowed)
        assertEquals(0, ListFilter(sept, kinds = setOf(TransactionKind.INCOME), query = "x").sheetFilterCount)
        assertEquals(2, ListFilter(sept, accountId = AccountId("a"), roomId = com.roziqrizal.rizqflow.domain.model.RoomId("r")).sheetFilterCount)
    }

    // ------------------------------------------------------------------ pencarian

    @Test
    fun `cari di catatan tanpa membedakan huruf besar`() {
        val f = campuran()
        assertEquals(listOf("Belanja pasar"), titles(list(f, ListFilter(sept, query = "PASAR"))))
        assertEquals(listOf("Belanja pasar"), titles(list(f, ListFilter(sept, query = "  belanja p  "))))
    }

    @Test
    fun `cari juga di sumber kategori ruang dan nama akun`() {
        val f = campuran()
        assertEquals(listOf("Gaji September"), titles(list(f, ListFilter(sept, query = "gaji"))))
        assertEquals(listOf("Tabung"), titles(list(f, ListFilter(sept, query = "dana darurat"))))
        assertEquals(listOf("Tabung"), titles(list(f, ListFilter(sept, query = "diri"))))
        assertEquals(listOf("Tarik tunai"), titles(list(f, ListFilter(sept, query = "jago"))))
    }

    @Test
    fun `cari nominal dengan angka, titik, atau awalan Rp`() {
        val f = campuran()
        assertEquals(listOf("Belanja pasar"), titles(list(f, ListFilter(sept, query = "150000"))))
        assertEquals(listOf("Belanja pasar"), titles(list(f, ListFilter(sept, query = "150.000"))))
        assertEquals(listOf("Belanja pasar"), titles(list(f, ListFilter(sept, query = "Rp 150.000"))))
        assertEquals(listOf("Tabung"), titles(list(f, ListFilter(sept, query = "1020"))), "potongan angka juga cocok")
    }

    @Test
    fun `kata cari yang bukan angka tidak dicocokkan ke nominal`() {
        val f = campuran()
        // "150abc" bukan angka murni: hanya dicari di teks, dan tidak ada teks yang memuatnya.
        assertTrue(list(f, ListFilter(sept, query = "150abc")).isEmpty)
    }

    @Test
    fun `hasil cari kosong dibedakan dari bulan kosong lewat penanda filter`() {
        val f = campuran()
        val filter = ListFilter(sept, query = "tidak ada")
        assertTrue(list(f, filter).isEmpty)
        assertTrue(filter.isNarrowed)
        assertNotNull(list(f).groups.firstOrNull())
    }
}
