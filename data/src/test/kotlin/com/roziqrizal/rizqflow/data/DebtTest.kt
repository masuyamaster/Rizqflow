package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room as AndroidRoom
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.debt.DebtDirection
import com.roziqrizal.rizqflow.domain.debt.DebtDraft
import com.roziqrizal.rizqflow.domain.debt.DebtReminderService
import com.roziqrizal.rizqflow.domain.debt.DebtService
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.reminder.DueStage
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

/** Menguji utang-piutang terhadap Room sungguhan: penyimpanan, rumus saldo pinjaman, pelunasan, hapus, dan pengingat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebtTest {

    private lateinit var db: RizqflowDatabase
    private lateinit var local: LocalLedger
    private lateinit var service: DebtService
    private lateinit var reminders: DebtReminderService

    private var counter = 0
    private val newId: () -> String = { "id-${++counter}" }
    private val today = LocalDate.of(2026, 9, 24)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndroidRoom.inMemoryDatabaseBuilder(context, RizqflowDatabase::class.java).allowMainThreadQueries().build()
        local = LocalLedger(db)
        runBlocking { WorkspaceSetup(local.workspace, newId).setUp(RoomTemplate.TIGA_HAK, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(5_000_000))) }
        val ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L }
        service = DebtService(local.debts, local.accounts, local.transactions, ledger, newId)
        reminders = DebtReminderService(local.debts, local.settings)
    }

    @After
    fun close() = db.close()

    private suspend fun balance() = local.accounts.balance(local.accounts.activeAccounts().single().id)

    private suspend fun lend(amount: Long, due: LocalDate? = null, record: Boolean = true) = (
        service.create(
            DebtDraft(
                DebtDirection.LENT, "Budi", Money.rupiah(amount), today, due,
                accountId = local.accounts.activeAccounts().single().id, recordMovement = record,
            ),
        ) as LedgerResult.Success
        ).value

    @Test
    fun `utang tersimpan dan terbaca kembali utuh`() = runBlocking {
        val debt = lend(200_000, due = today.plusDays(30))

        val stored = assertNotNull(local.debts.find(debt.id))

        assertEquals(debt, stored)
        assertEquals(today.plusDays(30), stored.dueDate)
        assertTrue(stored.initialTransactionId != null)
    }

    @Test
    fun `rumus saldo memakai pinjaman keluar dan masuk`() = runBlocking {
        assertEquals(Money.rupiah(5_000_000), balance())

        val lent = lend(200_000)
        assertEquals(Money.rupiah(4_800_000), balance())
        assertEquals(TransactionKind.LOAN_OUT, local.transactions.find(lent.initialTransactionId!!)!!.kind)

        service.repay(lent.id, Money.rupiah(50_000), today, local.accounts.activeAccounts().single().id)
        assertEquals(Money.rupiah(4_850_000), balance())

        service.create(DebtDraft(DebtDirection.BORROWED, "Ibu", Money.rupiah(1_000_000), today, accountId = local.accounts.activeAccounts().single().id))
        assertEquals(Money.rupiah(5_850_000), balance())
    }

    @Test
    fun `pinjaman tidak dihitung sebagai pengeluaran atau pemasukan`() = runBlocking {
        lend(200_000)
        service.create(DebtDraft(DebtDirection.BORROWED, "Ibu", Money.rupiah(1_000_000), today, accountId = local.accounts.activeAccounts().single().id))

        val all = local.transactions.between(today.minusDays(1), today.plusDays(1))
        val kinds = all.map { it.kind }.toSet()
        val totals = local.transactions.roomTotals(today.minusDays(1), today.plusDays(1))

        assertTrue(TransactionKind.EXPENSE !in kinds && TransactionKind.INCOME !in kinds)
        assertTrue(totals.spent.values.all { it.isZero })
        assertTrue(totals.allocated.values.all { it.isZero })
    }

    @Test
    fun `pelunasan tersimpan dan sisa dihitung dari pelunasan`() = runBlocking {
        val debt = lend(200_000)
        val account = local.accounts.activeAccounts().single().id
        service.repay(debt.id, Money.rupiah(50_000), today, account)
        service.repay(debt.id, Money.rupiah(30_000), today.plusDays(1), null)

        val row = service.overview(today).rows.single()

        assertEquals(Money.rupiah(80_000), row.repaid)
        assertEquals(Money.rupiah(120_000), row.outstanding)
        assertEquals(2, local.debts.allPayments().size)
        assertNull(local.debts.allPayments().last().transactionId)
    }

    @Test
    fun `hapus utang menghapus pelunasan dan transaksinya sehingga saldo kembali`() = runBlocking {
        val debt = lend(200_000)
        val account = local.accounts.activeAccounts().single().id
        service.repay(debt.id, Money.rupiah(50_000), today, account)

        service.delete(debt.id)

        assertNull(local.debts.find(debt.id))
        assertTrue(local.debts.allPayments().isEmpty())
        assertTrue(local.transactions.between(today.minusDays(1), today.plusDays(1)).isEmpty())
        assertEquals(Money.rupiah(5_000_000), balance())
    }

    @Test
    fun `menghapus transaksi awal di tempat lain tidak menghapus utangnya`() = runBlocking {
        val debt = lend(200_000)

        local.transactions.delete(debt.initialTransactionId!!)

        assertNull(local.debts.find(debt.id)!!.initialTransactionId)
    }

    @Test
    fun `urungkan pelunasan menghapus barisnya dan transaksinya`() = runBlocking {
        val debt = lend(200_000)
        val repayment = (service.repay(debt.id, Money.rupiah(200_000), today, local.accounts.activeAccounts().single().id) as LedgerResult.Success).value

        service.undoRepay(repayment.payment)

        assertTrue(local.debts.allPayments().isEmpty())
        assertNull(local.transactions.find(repayment.payment.transactionId!!))
        assertEquals(Money.rupiah(4_800_000), balance())
    }

    @Test
    fun `pengingat menyapa sekali per tahap dengan penanda di database`() = runBlocking {
        val debt = lend(200_000, due = today.plusDays(3))

        val approaching = reminders.dueReminders(today).single()
        reminders.markNotified(approaching)

        assertEquals(DueStage.APPROACHING, approaching.stage)
        assertTrue(reminders.dueReminders(today).isEmpty())
        assertEquals(DueStage.DUE_TODAY, reminders.dueReminders(today.plusDays(3)).single().stage)
        assertEquals(debt.id, approaching.debt.id)
    }
}
