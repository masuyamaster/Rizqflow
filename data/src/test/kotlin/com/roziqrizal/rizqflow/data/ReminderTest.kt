package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room as AndroidRoom
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.ReminderService
import com.roziqrizal.rizqflow.domain.ledger.ReminderSettings
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.TransactionId
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Menguji pengingat malam (S26) terhadap Room sungguhan: pengaturan, hari ditandai, dan balasan kilat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderTest {

    private lateinit var db: RizqflowDatabase
    private lateinit var local: LocalLedger
    private lateinit var service: ReminderService

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
        val ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L }
        service = ReminderService(local.settings, local.accounts, local.rooms, local.transactions, ledger)
    }

    @After
    fun close() = db.close()

    @Test
    fun `pengaturan tersimpan di Room dan terbaca lagi setelah dibuka ulang`() {
        runBlocking { service.updateSettings(false, 21, 15) }

        val dibukaLagi = ReminderService(local.settings, local.accounts, local.rooms, local.transactions, LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L })
        assertEquals(ReminderSettings(false, 21, 15), runBlocking { dibukaLagi.settings() })
    }

    @Test
    fun `Tidak ada tersimpan sebagai hari ditandai di Room sungguhan`() {
        assertFalse(runBlocking { service.isTodayDismissed(today) })

        runBlocking { service.dismissToday(today) }

        assertTrue(runBlocking { service.isTodayDismissed(today) })
        assertFalse(runBlocking { service.isTodayDismissed(today.plusDays(1)) })
    }

    @Test
    fun `balasan kilat tersimpan sebagai pengeluaran sungguhan dan menandai hari itu`() {
        val hasil = runBlocking { service.recordQuickReply("kopi 25000", today) }

        assertIs<LedgerResult.Success<TransactionId>>(hasil)
        val tx = runBlocking { local.transactions.find(hasil.value) }!!
        assertEquals(TransactionKind.EXPENSE, tx.kind)
        assertEquals(Money.rupiah(25_000), tx.amount)
        assertEquals("kopi", tx.note)
        assertTrue(runBlocking { service.isTodayDismissed(today) })
    }
}
