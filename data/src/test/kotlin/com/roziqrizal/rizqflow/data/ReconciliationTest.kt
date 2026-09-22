package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room as AndroidRoom
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.NewExpense
import com.roziqrizal.rizqflow.domain.ledger.ReconciliationService
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.TransactionKind
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Menguji Koreksi saldo (S25) terhadap Room sungguhan, termasuk kueri mentah `latestExpenseRoom`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReconciliationTest {

    private lateinit var db: RizqflowDatabase
    private lateinit var local: LocalLedger
    private lateinit var ledger: LedgerService
    private lateinit var service: ReconciliationService

    private var counter = 0
    private val newId: () -> String = { "id-${++counter}" }
    private val today = LocalDate.of(2026, 9, 22)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndroidRoom.inMemoryDatabaseBuilder(context, RizqflowDatabase::class.java).allowMainThreadQueries().build()
        local = LocalLedger(db)
        val setup = WorkspaceSetup(local.workspace, newId)
        runBlocking { setup.setUp(RoomTemplate.TIGA_HAK, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(500_000))) }
        ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L }
        service = ReconciliationService(local.accounts, local.rooms, local.transactions, ledger)
    }

    @After
    fun close() = db.close()

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun account() = runBlocking { local.accounts.activeAccounts() }.first().id

    private fun room(name: String) = runBlocking { local.rooms.activeRooms() }.first { it.name == name }.id

    @Test
    fun `ikhtisar mengusulkan ruang dari pengeluaran terakhir lewat kueri Room sungguhan`() {
        val diri = room("Diri")
        val kategori = runBlocking { local.rooms.categories(diri) }.first()
        runBlocking { ledger.recordExpense(NewExpense(rupiah(20_000), account(), diri, kategori.id, today)) }

        val overview = runBlocking { service.overview(account()) }
        assertNotNull(overview)
        assertEquals(diri, overview.suggestedRoomId)
        assertEquals(rupiah(480_000), overview.recorded)
    }

    @Test
    fun `tanpa pengeluaran ruang usulan bertipe Mencukupi`() {
        val overview = runBlocking { service.overview(account()) }
        assertEquals(room("Keluarga"), overview!!.suggestedRoomId)
    }

    @Test
    fun `saldo sebenarnya lebih kecil tersimpan sebagai pengeluaran Tak terlacak di Room sungguhan`() {
        val hasil = runBlocking { service.correct(account(), rupiah(453_000), room("Diri"), today) }

        assertIs<LedgerResult.Success<*>>(hasil)
        val txId = (hasil as LedgerResult.Success).value
        assertNotNull(txId)
        val tx = runBlocking { local.transactions.find(txId!!) }!!
        assertEquals(TransactionKind.EXPENSE, tx.kind)
        assertEquals(rupiah(47_000), tx.amount)
        val kategori = runBlocking { local.rooms.categories(room("Diri")) }.first { it.id == tx.categoryId }
        assertEquals("Tak terlacak", kategori.name)
        assertEquals(rupiah(453_000), runBlocking { local.accounts.balance(account()) })
    }

    @Test
    fun `saldo sebenarnya lebih besar tersimpan sebagai pemasukan dan dialirkan ke ruang aktif`() {
        val hasil = runBlocking { service.correct(account(), rupiah(700_000), room("Diri"), today) }

        val txId = (hasil as LedgerResult.Success).value!!
        val tx = runBlocking { local.transactions.find(txId) }!!
        assertEquals(TransactionKind.INCOME, tx.kind)
        assertEquals(rupiah(200_000), tx.amount)
        assertEquals(rupiah(700_000), runBlocking { local.accounts.balance(account()) })
        val entries = runBlocking { local.transactions.entriesOf(txId) }
        assertEquals(3, entries.size, "pemasukan biasa dialirkan ke semua ruang aktif")
    }

    @Test
    fun `saldo sama tidak mencatat transaksi tetapi memperbarui tanggal cocok`() {
        val hasil = runBlocking { service.correct(account(), rupiah(500_000), room("Diri"), today) }

        assertIs<LedgerResult.Success<*>>(hasil)
        assertNull((hasil as LedgerResult.Success).value)
        val akun = runBlocking { local.accounts.activeAccounts() }.first { it.id == account() }
        assertEquals(today, akun.lastReconciledOn)
    }
}
