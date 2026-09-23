package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room as AndroidRoom
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.entitlement.Plan
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.role.DcaStatus
import com.roziqrizal.rizqflow.domain.role.RoleKind
import com.roziqrizal.rizqflow.domain.role.RoleService
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Menguji sistem per peran terhadap Room sungguhan: Trader, jadwal DCA, dan pelepasan peran. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoleTest {

    private lateinit var db: RizqflowDatabase
    private lateinit var local: LocalLedger
    private lateinit var service: RoleService

    private var counter = 0
    private val newId: () -> String = { "id-${++counter}" }
    private val today = LocalDate.of(2026, 9, 23)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndroidRoom.inMemoryDatabaseBuilder(context, RizqflowDatabase::class.java).allowMainThreadQueries().build()
        local = LocalLedger(db)
        runBlocking { WorkspaceSetup(local.workspace, newId).setUp(RoomTemplate.TIGA_HAK, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(5_000_000))) }
        val ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L }
        service = RoleService(local.rooms, local.accounts, local.transactions, local.roles, ledger, PlanEntitlements(setOf(Plan.PRO)))
    }

    @After
    fun close() = db.close()

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun diri() = runBlocking { local.rooms.activeRooms() }.first { it.name == "Diri" }

    private fun investasi() = runBlocking { local.rooms.categories(diri().id) }.first { it.name == "Investasi" }

    private fun account() = runBlocking { local.accounts.activeAccounts() }.first()

    @Test
    fun `Trader tersimpan di Room dan terbaca lagi dengan batas risiko yang benar`() {
        assertIs<LedgerResult.Success<*>>(runBlocking { service.setTrader(diri().id, rupiah(20_000_000), BasisPoints(150)) })

        val overview = runBlocking { service.overview(diri().id, today) }!!

        assertEquals(RoleKind.TRADER, overview.kind)
        assertEquals(rupiah(300_000), overview.trader!!.maxRiskPerTrade)
        assertEquals(rupiah(300_000), runBlocking { service.riskWarning(diri().id, rupiah(300_001)) }!!.maxRisk)
    }

    @Test
    fun `memasang Trader dua kali menimpa isiannya bukan menambah baris`() {
        runBlocking { service.setTrader(diri().id, rupiah(1_000_000), BasisPoints(100)) }
        runBlocking { service.setTrader(diri().id, rupiah(2_000_000), BasisPoints(200)) }

        val trader = runBlocking { local.roles.trader(diri().id) }!!
        assertEquals(rupiah(2_000_000), trader.capital)
        assertEquals(200, trader.riskPerTrade.value)
    }

    @Test
    fun `jadwal DCA tersimpan dan mencatatnya membuat pengeluaran serta status selesai`() {
        assertIs<LedgerResult.Success<*>>(runBlocking { service.setDca(diri().id, rupiah(750_000), 5, account().id, investasi().id) })
        val before = runBlocking { service.overview(diri().id, today) }!!.dca!!
        assertIs<DcaStatus.Due>(before.status)
        assertEquals("Dompet", before.accountName)

        assertIs<LedgerResult.Success<*>>(runBlocking { service.recordDca(diri().id, today) })

        assertEquals(DcaStatus.Done(rupiah(750_000)), runBlocking { service.overview(diri().id, today) }!!.dca!!.status)
        assertEquals(rupiah(4_250_000), runBlocking { local.accounts.balance(account().id) })
    }

    @Test
    fun `melepas peran menghapus barisnya tetapi pengeluaran DCA yang sudah dicatat tetap ada`() {
        runBlocking { service.setDca(diri().id, rupiah(750_000), 5, account().id, investasi().id) }
        runBlocking { service.recordDca(diri().id, today) }

        runBlocking { service.remove(diri().id) }

        assertNull(runBlocking { local.roles.dca(diri().id) })
        assertNull(runBlocking { local.roles.trader(diri().id) })
        assertEquals(1, runBlocking { local.transactions.between(today, today) }.size)
    }

    @Test
    fun `semua jadwal DCA terbaca untuk pengingat`() {
        runBlocking { service.setDca(diri().id, rupiah(750_000), 5, account().id, investasi().id) }

        val due = runBlocking { service.dueDca(today) }

        assertEquals(1, due.size)
        assertTrue(due.single().status is DcaStatus.Due)
    }
}
