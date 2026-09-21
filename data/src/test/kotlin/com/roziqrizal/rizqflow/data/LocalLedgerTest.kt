package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room as AndroidRoom
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.data.repo.toEntity
import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.ledger.Account
import com.roziqrizal.rizqflow.domain.ledger.AllocationEntry
import com.roziqrizal.rizqflow.domain.ledger.Category
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.IncomeReceipt
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.MoneyTransaction
import com.roziqrizal.rizqflow.domain.ledger.NewExpense
import com.roziqrizal.rizqflow.domain.ledger.NewIncome
import com.roziqrizal.rizqflow.domain.ledger.NewRoom
import com.roziqrizal.rizqflow.domain.ledger.NewTransfer
import com.roziqrizal.rizqflow.domain.ledger.Room
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.RuleService
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Currency
import com.roziqrizal.rizqflow.domain.money.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Menguji lapisan data terhadap Room sungguhan (SQLite di JVM lewat Robolectric): kueri, kunci
 * asing, transaksi, dan pemetaan. Layanan domain dipakai apa adanya di atas repositori Room,
 * sehingga yang diuji adalah alur nyata yang akan dipakai layar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalLedgerTest {

    private lateinit var db: RizqflowDatabase
    private lateinit var local: LocalLedger
    private lateinit var setup: WorkspaceSetup
    private lateinit var service: LedgerService
    private lateinit var rules: RuleService

    private var counter = 0
    private val newId: () -> String = { "id-${++counter}" }
    private var now = 1_000L
    private val day = LocalDate.of(2026, 9, 21)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndroidRoom.inMemoryDatabaseBuilder(context, RizqflowDatabase::class.java).allowMainThreadQueries().build()
        local = LocalLedger(db)
        setup = WorkspaceSetup(local.workspace, newId)
        service = LedgerService(local.accounts, local.rooms, local.transactions, newId) { now }
        rules = RuleService(local.rooms, PlanEntitlements(), newId)
    }

    @After
    fun close() = db.close()

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun standard() = runBlocking {
        val result = setup.setUp(RoomTemplate.TIGA_HAK, FirstAccount("Dompet", AccountKind.CASH, rupiah(500_000)))
        assertIs<LedgerResult.Success<Unit>>(result)
    }

    private fun account(): Account = runBlocking { local.accounts.activeAccounts().first() }

    private fun room(name: String): Room = runBlocking { local.rooms.activeRooms().first { it.name == name } }

    private fun category(roomName: String, name: String): Category =
        runBlocking { local.rooms.categories(room(roomName).id).first { it.name == name } }

    private fun income(amount: Long, on: LocalDate = day, accountId: AccountId = account().id): IncomeReceipt = runBlocking {
        (service.recordIncome(NewIncome(rupiah(amount), accountId, "Gaji", on)) as LedgerResult.Success).value
    }

    private fun entryAmounts(id: TransactionId) = runBlocking { local.transactions.entriesOf(id) }.map { it.amount.minor }

    // ------------------------------------------------------------------ pengaturan awal

    @Test
    fun `ruang kerja kosong sebelum onboarding dan berisi sesudahnya`() = runBlocking {
        assertTrue(local.workspace.isEmpty())
        standard()
        assertTrue(!local.workspace.isEmpty())
    }

    @Test
    fun `onboarding menyimpan ruang aturan kategori dan akun dengan benar`() {
        standard()

        val rooms = runBlocking { local.rooms.activeRooms() }
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), rooms.map { it.name })
        assertEquals(listOf(RoomKind.MENUNAIKAN, RoomKind.MENUMBUHKAN, RoomKind.MENCUKUPI), rooms.map { it.kind })
        assertEquals("percentage", rooms[0].givingMode)
        assertNull(rooms[1].givingMode)

        val rules = runBlocking { local.rooms.rules() }
        assertEquals(listOf(1_000, 3_000, 6_000), rules.map { it.share.value })
        assertEquals(rooms.map { it.id }, rules.map { it.roomId })

        val keluarga = runBlocking { local.rooms.categories(rooms[2].id) }
        assertEquals(listOf("Belanja bulanan", "Listrik dan air", "Sekolah", "Lain-lain", "Tak terlacak"), keluarga.map { it.name })
        assertTrue(keluarga.last().isSystem)

        assertEquals("Dompet", account().name)
        assertEquals(rupiah(500_000), runBlocking { local.accounts.balance(account().id) })
    }

    @Test
    fun `onboarding kedua ditolak dan tidak menggandakan data`() {
        standard()
        val kedua = runBlocking { setup.setUp(RoomTemplate.TIGA_HAK, FirstAccount("Lain", AccountKind.BANK, rupiah(0))) }

        assertIs<LedgerResult.Failure>(kedua)
        assertEquals(3, runBlocking { local.rooms.activeRooms() }.size)
        assertEquals(1, runBlocking { local.accounts.activeAccounts() }.size)
    }

    @Test
    fun `onboarding yang gagal di tengah membatalkan semuanya`() {
        // Kategori menunjuk ruang yang tidak ada: kunci asing menolak, seluruh transaksi database dibatalkan.
        val orphan = Category(CategoryId("c1"), RoomId("tidak-ada"), "Yatim")
        val snapshot = com.roziqrizal.rizqflow.domain.ledger.WorkspaceSnapshot(
            rooms = listOf(Room(RoomId("r1"), "Ruang", RoomKind.MENCUKUPI, "home", 1, 0)),
            categories = listOf(orphan),
            rules = emptyList(),
            accounts = listOf(Account(AccountId("a1"), "Dompet", AccountKind.CASH, rupiah(0))),
        )

        assertFailsWith<Exception> { runBlocking { local.workspace.initialize(snapshot) } }

        assertTrue(runBlocking { local.workspace.isEmpty() }, "ruang dan akun tidak boleh tersisa")
    }

    // ------------------------------------------------------------------ pemasukan

    @Test
    fun `pemasukan tersimpan dengan potret alokasi berurutan menurut prioritas ruang`() {
        standard()

        val receipt = income(1_234_567)

        assertEquals(listOf(123_457L, 370_370L, 740_740L), entryAmounts(receipt.transaction.id))
        val entries = runBlocking { local.transactions.entriesOf(receipt.transaction.id) }
        assertEquals(listOf(1_000, 3_000, 6_000), entries.map { it.share.value })
        assertEquals(rupiah(1_234_567), entries.fold(rupiah(0)) { a, e -> a + e.amount })
        assertEquals(rupiah(1_734_567), runBlocking { local.accounts.balance(account().id) })
    }

    @Test
    fun `nominal sangat kecil menyimpan baris berjumlah nol beserta persentasenya`() {
        standard()
        val receipt = income(1)
        assertEquals(listOf(0L, 0L, 1L), entryAmounts(receipt.transaction.id))
        assertEquals(3, runBlocking { local.transactions.entriesOf(receipt.transaction.id) }.size)
    }

    @Test
    fun `pemasukan yang gagal disimpan tidak meninggalkan transaksi tanpa alokasi`() {
        standard()
        val id = TransactionId("t-gagal")
        val tx = MoneyTransaction(id, TransactionKind.INCOME, rupiah(1_000), account().id, occurredOn = day, createdAtMillis = 1, updatedAtMillis = 1)
        val entry = AllocationEntry("e1", id, RoomId("tidak-ada"), BasisPoints.percent(100), rupiah(1_000), 1)

        assertFailsWith<Exception> { runBlocking { local.transactions.saveIncome(tx, listOf(entry)) } }

        assertNull(runBlocking { local.transactions.find(id) })
    }

    @Test
    fun `tanggal dan waktu kembali persis sama setelah disimpan`() {
        standard()
        now = 1_726_900_000_123L
        val tanggal = listOf(LocalDate.of(1970, 1, 1), LocalDate.of(2026, 2, 28), LocalDate.of(2100, 12, 31))
        val ids = tanggal.map { income(1_000, on = it).transaction.id }

        val kembali = ids.map { runBlocking { local.transactions.find(it) }!! }

        assertEquals(tanggal, kembali.map { it.occurredOn })
        assertTrue(kembali.all { it.createdAtMillis == 1_726_900_000_123L })
    }

    @Test
    fun `sumber catatan dan asal transaksi kembali utuh, termasuk nilai kosong`() {
        standard()
        val penuh = runBlocking {
            (service.recordIncome(NewIncome(rupiah(1_000), account().id, "Freelance", day, "proyek desain")) as LedgerResult.Success).value
        }.transaction
        val kosong = runBlocking { (service.recordIncome(NewIncome(rupiah(1_000), account().id, null, day)) as LedgerResult.Success).value }.transaction

        val a = runBlocking { local.transactions.find(penuh.id) }!!
        val b = runBlocking { local.transactions.find(kosong.id) }!!
        assertEquals("Freelance" to "proyek desain", a.incomeSource to a.note)
        assertNull(b.incomeSource)
        assertNull(b.note)
    }

    // ------------------------------------------------------------------ pengeluaran dan transfer

    @Test
    fun `pengeluaran mengurangi saldo dan tersimpan dengan ruang dan kategorinya`() {
        standard()
        val tx = runBlocking {
            (service.recordExpense(NewExpense(rupiah(25_000), account().id, room("Keluarga").id, category("Keluarga", "Sekolah").id, day, "SPP")) as LedgerResult.Success).value
        }

        val disimpan = runBlocking { local.transactions.find(tx.id) }!!
        assertEquals(room("Keluarga").id, disimpan.roomId)
        assertEquals(category("Keluarga", "Sekolah").id, disimpan.categoryId)
        assertEquals(rupiah(475_000), runBlocking { local.accounts.balance(account().id) })
    }

    @Test
    fun `pengeluaran ke kategori yang tidak ada ditolak kunci asing sebagai jaring pengaman terakhir`() {
        standard()
        val tx = MoneyTransaction(
            TransactionId("t-x"), TransactionKind.EXPENSE, rupiah(1_000), account().id,
            roomId = room("Keluarga").id, categoryId = CategoryId("tidak-ada"), occurredOn = day, createdAtMillis = 1, updatedAtMillis = 1,
        )
        assertFailsWith<Exception> { runBlocking { local.transactions.save(tx) } }
        assertNull(runBlocking { local.transactions.find(tx.id) })
    }

    @Test
    fun `transfer memindahkan saldo dan total kedua akun tetap`() {
        standard()
        val bank = Account(AccountId("bank"), "Bank", AccountKind.BANK, rupiah(200_000), sortOrder = 1)
        runBlocking { local.accounts.save(bank) }

        runBlocking { service.recordTransfer(NewTransfer(rupiah(100_000), account().id, bank.id, day)) }

        assertEquals(rupiah(400_000), runBlocking { local.accounts.balance(account().id) })
        assertEquals(rupiah(300_000), runBlocking { local.accounts.balance(bank.id) })
    }

    // ------------------------------------------------------------------ ubah dan hapus

    @Test
    fun `ubah nominal memakai persentase potret dan menjaga pengenal baris`() {
        standard()
        val receipt = income(1_000_000)
        val idsSebelum = runBlocking { local.transactions.entriesOf(receipt.transaction.id) }.map { it.id }

        val hasil = runBlocking { service.editIncomeAmount(receipt.transaction.id, rupiah(2_000_000)) }

        assertIs<LedgerResult.Success<IncomeReceipt>>(hasil)
        assertEquals(listOf(200_000L, 600_000L, 1_200_000L), entryAmounts(receipt.transaction.id))
        assertEquals(idsSebelum, runBlocking { local.transactions.entriesOf(receipt.transaction.id) }.map { it.id })
        assertEquals(rupiah(2_000_000), runBlocking { local.transactions.find(receipt.transaction.id) }!!.amount)
    }

    @Test
    fun `mengubah aturan tidak mengubah riwayat pemasukan lama`() {
        standard()
        val receipt = income(1_000_000)
        val semua = listOf("Memberi", "Diri", "Keluarga").map { room(it).id }

        runBlocking {
            rules.changeRules(listOf(AllocationRule(semua[0], BasisPoints.percent(50)), AllocationRule(semua[1], BasisPoints.percent(25)), AllocationRule(semua[2], BasisPoints.percent(25))))
        }

        assertEquals(listOf(100_000L, 300_000L, 600_000L), entryAmounts(receipt.transaction.id))
        assertEquals(listOf(500_000L, 250_000L, 250_000L), entryAmounts(income(1_000_000).transaction.id))
    }

    @Test
    fun `menghapus pemasukan ikut menghapus potret alokasinya lewat kunci asing`() {
        standard()
        val receipt = income(1_000_000)

        runBlocking { service.delete(receipt.transaction.id) }

        assertNull(runBlocking { local.transactions.find(receipt.transaction.id) })
        assertTrue(runBlocking { db.transactions().entriesOf(receipt.transaction.id.value) }.isEmpty())
        assertEquals(rupiah(500_000), runBlocking { local.accounts.balance(account().id) })
    }

    @Test
    fun `daftar antara dua hari berurutan terbaru dulu dan hanya dalam rentang`() {
        standard()
        val a = income(1_000, on = LocalDate.of(2026, 9, 10)).transaction
        val c = income(3_000, on = LocalDate.of(2026, 9, 20)).transaction
        now += 1
        val b = income(2_000, on = LocalDate.of(2026, 9, 20)).transaction
        income(4_000, on = LocalDate.of(2026, 9, 30))

        val hasil = runBlocking { local.transactions.between(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20)) }

        assertEquals(listOf(b.id, c.id, a.id), hasil.map { it.id })
    }

    // ------------------------------------------------------------------ aturan dan ruang

    @Test
    fun `mengganti aturan hanya menyentuh ruang aktif dan mempertahankan persentase ruang terarsip`() {
        standard()
        val diri = room("Diri")
        runBlocking { db.rooms().upsert(diri.copy(archived = true).toEntity()) }

        val hasil = runBlocking {
            rules.changeRules(listOf(AllocationRule(room("Memberi").id, BasisPoints.percent(40)), AllocationRule(room("Keluarga").id, BasisPoints.percent(60))))
        }

        assertIs<LedgerResult.Success<Unit>>(hasil)
        assertEquals(listOf(4_000, 6_000), runBlocking { local.rooms.rules() }.map { it.share.value })
        val tersimpan = db.query("SELECT share_bp FROM allocation_rule WHERE room_id = ?", arrayOf<Any?>(diri.id.value)).use { c ->
            assertTrue(c.moveToFirst(), "aturan ruang terarsip masih ada")
            c.getInt(0)
        }
        assertEquals(3_000, tersimpan)
    }

    @Test
    fun `ruang terarsip tidak muncul di daftar aktif maupun aturan`() {
        standard()
        val diri = room("Diri")
        runBlocking { db.rooms().upsert(diri.copy(archived = true).toEntity()) }

        assertEquals(listOf("Memberi", "Keluarga"), runBlocking { local.rooms.activeRooms() }.map { it.name })
        assertEquals(listOf(1_000, 6_000), runBlocking { local.rooms.rules() }.map { it.share.value })
    }

    @Test
    fun `menambah ruang menyimpan ruang dan kategori awalnya, dan ruang keenam ditolak`() {
        standard()
        val baru = runBlocking { rules.addRoom(NewRoom("Orang tua", RoomKind.MENCUKUPI, "home", 4)) }
        val id = (baru as LedgerResult.Success).value

        assertEquals(listOf("Lain-lain"), runBlocking { local.rooms.categories(id) }.map { it.name })
        assertEquals(4, runBlocking { local.rooms.activeRooms() }.size)
        runBlocking { rules.addRoom(NewRoom("Tabungan haji", RoomKind.MENUMBUHKAN, "sprout", 5)) }
        assertIs<LedgerResult.Failure>(runBlocking { rules.addRoom(NewRoom("Liburan", RoomKind.MENCUKUPI, "home", 6)) })
        assertEquals(5, runBlocking { local.rooms.activeRooms() }.size)
    }

    @Test
    fun `ruang yang gagal disimpan tidak tersisa tanpa kategorinya`() {
        standard()
        val room = Room(RoomId("r-baru"), "Baru", RoomKind.MENCUKUPI, "home", 4, 3)
        val yatim = Category(CategoryId("c-yatim"), RoomId("bukan-r-baru"), "Yatim")

        assertFailsWith<Exception> { runBlocking { local.rooms.addRoom(room, listOf(yatim)) } }

        assertNull(runBlocking { local.rooms.find(room.id) })
    }

    // ------------------------------------------------------------------ akun dan mata uang

    @Test
    fun `tanggal koreksi saldo akun tidak hilang saat akun disimpan ulang`() {
        standard()
        val dicocokkan = account().copy(lastReconciledOn = LocalDate.of(2026, 9, 1))
        runBlocking { local.accounts.save(dicocokkan) }
        runBlocking { local.accounts.save(account().copy(name = "Dompet utama")) }

        assertEquals(LocalDate.of(2026, 9, 1), account().lastReconciledOn)
        assertEquals("Dompet utama", account().name)
    }

    @Test
    fun `saldo akun yang tidak ada dilaporkan sebagai galat, bukan nol`() {
        assertFailsWith<IllegalArgumentException> { runBlocking { local.accounts.balance(AccountId("tidak-ada")) } }
    }

    @Test
    fun `mata uang dan nominal besar kembali persis`() {
        val usd = Account(AccountId("usd"), "Dolar", AccountKind.BANK, Money(1_234, Currency.USD))
        runBlocking { local.accounts.save(usd) }

        val kembali = runBlocking { local.accounts.find(usd.id) }!!
        assertEquals(Money(1_234, Currency.USD), kembali.openingBalance)
        assertEquals(Money(1_234, Currency.USD), runBlocking { local.accounts.balance(usd.id) })

        val besar = Account(AccountId("besar"), "Besar", AccountKind.BANK, Money(Long.MAX_VALUE / 2))
        runBlocking { local.accounts.save(besar) }
        assertEquals(Money(Long.MAX_VALUE / 2), runBlocking { local.accounts.find(besar.id) }!!.openingBalance)
    }

    @Test
    fun `akun terarsip tidak muncul di daftar aktif tetapi tetap bisa ditemukan`() {
        standard()
        val lama = Account(AccountId("lama"), "Lama", AccountKind.CASH, rupiah(0), archived = true, sortOrder = 5)
        runBlocking { local.accounts.save(lama) }

        assertEquals(listOf("Dompet"), runBlocking { local.accounts.activeAccounts() }.map { it.name })
        assertNotNull(runBlocking { local.accounts.find(lama.id) })
    }
}
