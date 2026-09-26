package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room as AndroidRoom
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.bill.BillDraft
import com.roziqrizal.rizqflow.domain.bill.BillReminderService
import com.roziqrizal.rizqflow.domain.bill.BillReminderStage
import com.roziqrizal.rizqflow.domain.bill.BillService
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.recurring.Frequency
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Menguji tagihan dan cicilan terhadap Room sungguhan: penyimpanan, membayar, mengurungkan, dan pengingat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BillTest {

    private lateinit var db: RizqflowDatabase
    private lateinit var local: LocalLedger
    private lateinit var service: BillService
    private lateinit var reminders: BillReminderService

    private var counter = 0
    private val newId: () -> String = { "id-${++counter}" }

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndroidRoom.inMemoryDatabaseBuilder(context, RizqflowDatabase::class.java).allowMainThreadQueries().build()
        local = LocalLedger(db)
        runBlocking { WorkspaceSetup(local.workspace, newId).setUp(RoomTemplate.TIGA_HAK, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(5_000_000))) }
        val ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L }
        service = BillService(local.bills, local.accounts, local.rooms, local.transactions, ledger, newId)
        reminders = BillReminderService(local.bills, local.settings)
    }

    @After
    fun close() = db.close()

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private suspend fun draft(due: LocalDate, frequency: Frequency? = Frequency.MONTHLY, total: Int? = null, alreadyPaid: Int = 0): BillDraft {
        val account = local.accounts.activeAccounts().single()
        val room = local.rooms.activeRooms().first { it.name == "Keluarga" }
        val category = local.rooms.categories(room.id).first { !it.isSystem }
        return BillDraft("Cicilan motor", Money.rupiah(1_200_000), account.id, room.id, category.id, note = "Dealer", frequency = frequency, dueDate = due, totalInstallments = total, alreadyPaid = alreadyPaid)
    }

    @Test
    fun `tagihan tersimpan dan terbaca kembali utuh`() = runBlocking {
        val bill = (service.create(draft(d(2026, 10, 5), total = 12, alreadyPaid = 3)) as LedgerResult.Success).value

        val stored = assertNotNull(local.bills.find(bill.id))

        assertEquals(bill, stored)
        assertEquals(12, stored.totalInstallments)
        assertEquals(3, stored.paidCount)
        assertEquals("Dealer", stored.note)
    }

    @Test
    fun `tagihan sekali bayar menyimpan frekuensi kosong`() = runBlocking {
        val bill = (service.create(draft(d(2026, 10, 5), frequency = null)) as LedgerResult.Success).value

        assertNull(local.bills.find(bill.id)!!.frequency)
    }

    @Test
    fun `membayar mencatat pengeluaran di database dan memajukan jatuh tempo`() = runBlocking {
        val bill = (service.create(draft(d(2026, 9, 5), total = 2)) as LedgerResult.Success).value

        val payment = (service.pay(bill.id, d(2026, 9, 5)) as LedgerResult.Success).value

        val tx = assertNotNull(local.transactions.find(payment.transactionId))
        assertEquals(TransactionOrigin.BILL, tx.origin)
        assertEquals("Cicilan motor (1/2)", tx.note)
        assertEquals(d(2026, 10, 5), local.bills.find(bill.id)!!.nextDue)
        assertEquals(Money.rupiah(3_800_000), local.accounts.balance(local.accounts.activeAccounts().single().id))
    }

    @Test
    fun `urungkan mengembalikan tagihan dan menghapus pengeluaran`() = runBlocking {
        val bill = (service.create(draft(d(2026, 9, 5))) as LedgerResult.Success).value
        val payment = (service.pay(bill.id, d(2026, 9, 5)) as LedgerResult.Success).value

        service.undoPay(payment)

        assertEquals(bill, local.bills.find(bill.id))
        assertNull(local.transactions.find(payment.transactionId))
    }

    @Test
    fun `hapus tagihan tidak menghapus pengeluaran yang sudah dicatat`() = runBlocking {
        val bill = (service.create(draft(d(2026, 9, 5))) as LedgerResult.Success).value
        val payment = (service.pay(bill.id, d(2026, 9, 5)) as LedgerResult.Success).value

        service.delete(bill.id)

        assertNull(local.bills.find(bill.id))
        assertTrue(local.transactions.find(payment.transactionId) != null)
    }

    @Test
    fun `pengingat menyapa sekali per tahap dengan penanda di database`() = runBlocking {
        val bill = (service.create(draft(d(2026, 9, 25))) as LedgerResult.Success).value

        val approaching = reminders.dueReminders(d(2026, 9, 22)).single()
        reminders.markNotified(approaching)
        val today = reminders.dueReminders(d(2026, 9, 25)).single()

        assertEquals(BillReminderStage.APPROACHING, approaching.stage)
        assertEquals(BillReminderStage.DUE_TODAY, today.stage)
        assertTrue(reminders.dueReminders(d(2026, 9, 22)).isEmpty())
        assertEquals(bill.id, today.bill.id)
    }
}
