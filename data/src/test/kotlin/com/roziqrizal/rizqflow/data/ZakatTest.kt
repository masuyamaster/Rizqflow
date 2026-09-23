package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room as AndroidRoom
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.entitlement.Plan
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.WealthKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.zakat.GivingStatus
import com.roziqrizal.rizqflow.domain.zakat.HaulStatus
import com.roziqrizal.rizqflow.domain.zakat.NewWealthItem
import com.roziqrizal.rizqflow.domain.zakat.ZakatHaulHijriStrategy
import com.roziqrizal.rizqflow.domain.zakat.ZakatService
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
import kotlin.test.assertTrue

/** Menguji modul zakat terhadap Room sungguhan: penyimpanan harta, harga emas, dan haul (S14 sampai S17). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZakatTest {

    private lateinit var db: RizqflowDatabase
    private lateinit var local: LocalLedger
    private lateinit var service: ZakatService

    private var counter = 0
    private val newId: () -> String = { "id-${++counter}" }
    private val awal = LocalDate.of(2026, 1, 10)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndroidRoom.inMemoryDatabaseBuilder(context, RizqflowDatabase::class.java).allowMainThreadQueries().build()
        local = LocalLedger(db)
        val setup = WorkspaceSetup(local.workspace, newId)
        runBlocking { setup.setUp(RoomTemplate.TIGA_HAK, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(500_000))) }
        val ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L }
        service = ZakatService(local.rooms, local.transactions, local.zakat, ledger, newId)
    }

    @After
    fun close() = db.close()

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun room() = runBlocking { local.rooms.activeRooms() }.first { it.name == "Memberi" }.id

    private fun account() = runBlocking { local.accounts.activeAccounts() }.first().id

    @Test
    fun `menyimpan harta tersimpan di Room dan terbaca lagi lewat overview`() {
        runBlocking { service.setGivingMode(room(), ZakatHaulHijriStrategy.ID) }

        val hasil = runBlocking {
            service.saveWealth(
                listOf(
                    NewWealthItem(kind = WealthKind.GOLD, label = "Emas", value = rupiah(0), goldMilligrams = 60_000),
                    NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(38_000_000)),
                    NewWealthItem(kind = WealthKind.DEDUCTION, label = "Hutang", value = rupiah(14_600_000)),
                ),
                rupiah(1_660_000),
                awal,
            )
        }
        assertIs<LedgerResult.Success<*>>(hasil)

        val overview = runBlocking { service.overview(room(), awal) }
        assertNotNull(overview)
        assertEquals(setOf("Emas", "Tabungan", "Hutang"), overview.items.map { it.label }.toSet())
        assertEquals(rupiah(99_600_000), overview.items.first { it.label == "Emas" }.value)
        val status = overview.status
        assertIs<GivingStatus.Zakat>(status)
        assertEquals(rupiah(123_000_000), status.netWealth)
    }

    @Test
    fun `menyimpan harta lagi mengganti seluruh baris di Room bukan menambah`() {
        runBlocking { service.setGivingMode(room(), ZakatHaulHijriStrategy.ID) }
        runBlocking { service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Lama", value = rupiah(1_000_000))), rupiah(1_000_000), awal) }

        runBlocking { service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Baru", value = rupiah(2_000_000))), rupiah(1_100_000), awal.plusDays(1)) }

        val items = runBlocking { local.zakat.items(local.zakat.profiles().first().id) }
        assertEquals(listOf("Baru"), items.map { it.label })
    }

    @Test
    fun `menyimpan harta dua kali di hari yang sama tidak menumpuk pemeriksaan`() {
        runBlocking { service.setGivingMode(room(), ZakatHaulHijriStrategy.ID) }
        runBlocking { service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "A", value = rupiah(1_000_000))), rupiah(1_000_000), awal) }

        runBlocking { service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "A", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }

        val checks = runBlocking { local.zakat.checks(local.zakat.profiles().first().id) }
        assertEquals(1, checks.size, "hanya satu pemeriksaan untuk hari yang sama")
        assertEquals(rupiah(200_000_000), checks.single().netWealth)
    }

    @Test
    fun `haul genap dan menunaikan zakat tersimpan sebagai pengeluaran Zakat mal di Room`() {
        runBlocking { service.setGivingMode(room(), ZakatHaulHijriStrategy.ID) }
        runBlocking { service.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }
        val genap = com.roziqrizal.rizqflow.domain.calendar.UmmAlQuraCalendar().plusYears(awal)
        val sebelum = (runBlocking { service.overview(room(), genap) }!!.status as GivingStatus.Zakat)
        assertIs<HaulStatus.Completed>(sebelum.haul)

        val hasil = runBlocking { service.payZakat(room(), account(), rupiah(5_000_000), genap) }
        assertIs<LedgerResult.Success<TransactionId>>(hasil)

        val tx = runBlocking { local.transactions.find(hasil.value) }
        assertNotNull(tx)
        assertEquals(rupiah(5_000_000), tx.amount)
        val kategori = runBlocking { local.rooms.categories(room()) }.first { it.id == tx.categoryId }
        assertEquals("Zakat mal", kategori.name)

        val sesudah = runBlocking { service.overview(room(), genap) }!!
        assertEquals(1, sesudah.payments.size)
        assertIs<HaulStatus.Running>((sesudah.status as GivingStatus.Zakat).haul)
        assertTrue(runBlocking { local.balance(account()) } < rupiah(200_000_000))
    }

    @Test
    fun `beberapa profil tersimpan berurutan menurut pembuatan dan tiap profil punya hartanya sendiri`() {
        val ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L }
        val pro = ZakatService(local.rooms, local.transactions, local.zakat, ledger, newId, entitlements = PlanEntitlements(setOf(Plan.PRO)))
        runBlocking { pro.setGivingMode(room(), ZakatHaulHijriStrategy.ID) }
        val tabungan = listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000)))
        runBlocking { pro.saveWealth(tabungan, rupiah(1_660_000), awal) }

        val istri = (runBlocking { pro.addProfile("Istri") } as LedgerResult.Success).value
        runBlocking { pro.addProfile("Usaha") }
        runBlocking { pro.saveWealth(tabungan.map { it.copy(value = rupiah(5_000_000)) }, rupiah(1_660_000), awal, istri.id) }

        assertEquals(listOf("Utama", "Istri", "Usaha"), runBlocking { local.zakat.profiles() }.map { it.name })
        assertEquals(rupiah(5_000_000), (runBlocking { pro.overview(room(), awal, istri.id) }!!.status as GivingStatus.Zakat).netWealth)
        assertEquals(rupiah(200_000_000), (runBlocking { pro.overview(room(), awal) }!!.status as GivingStatus.Zakat).netWealth)

        runBlocking { pro.archiveProfile(istri.id) }
        assertEquals(listOf("Utama", "Usaha"), runBlocking { local.zakat.profiles() }.map { it.name })
        assertEquals("Istri", runBlocking { local.zakat.findProfile(istri.id) }!!.name)
    }

    private suspend fun LocalLedger.balance(id: com.roziqrizal.rizqflow.domain.model.AccountId) = accounts.balance(id)
}
