package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.room.Room as AndroidRoom
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.recurring.Frequency
import com.roziqrizal.rizqflow.domain.recurring.RecurringDraft
import com.roziqrizal.rizqflow.domain.recurring.RecurringService
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

/** Menguji transaksi berulang terhadap Room sungguhan: penyimpanan aturan, mengejar yang terlewat, dan tanpa ganda. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecurringTest {

    private lateinit var db: RizqflowDatabase
    private lateinit var local: LocalLedger
    private lateinit var service: RecurringService

    private var counter = 0
    private val newId: () -> String = { "id-${++counter}" }

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndroidRoom.inMemoryDatabaseBuilder(context, RizqflowDatabase::class.java).allowMainThreadQueries().build()
        local = LocalLedger(db)
        runBlocking { WorkspaceSetup(local.workspace, newId).setUp(RoomTemplate.TIGA_HAK, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(5_000_000))) }
        val ledger = LedgerService(local.accounts, local.rooms, local.transactions, newId) { 1_000L }
        service = RecurringService(local.recurring, local.accounts, local.rooms, local.transactions, ledger, newId)
    }

    @After
    fun close() = db.close()

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private suspend fun expenseDraft(start: LocalDate, frequency: Frequency = Frequency.MONTHLY, end: LocalDate? = null): RecurringDraft {
        val account = local.accounts.activeAccounts().single()
        val room = local.rooms.activeRooms().first { it.name == "Keluarga" }
        val category = local.rooms.categories(room.id).first { !it.isSystem }
        return RecurringDraft(TransactionKind.EXPENSE, Money.rupiah(1_500_000), account.id, roomId = room.id, categoryId = category.id, note = "Kontrakan", frequency = frequency, startDate = start, endDate = end)
    }

    @Test
    fun `aturan tersimpan dan terbaca kembali utuh`() = runBlocking {
        val rule = (service.create(expenseDraft(d(2026, 10, 1), end = d(2027, 3, 1)), d(2026, 9, 24)) as LedgerResult.Success).value

        val stored = assertNotNull(local.recurring.find(rule.id))

        assertEquals(rule, stored)
        assertEquals(d(2027, 3, 1), stored.endDate)
        assertEquals(1, service.list().size)
    }

    @Test
    fun `mengejar kemunculan yang terlewat lalu tidak menggandakan saat dijalankan lagi`() = runBlocking {
        service.create(expenseDraft(d(2026, 6, 15)), d(2026, 6, 15))

        val first = service.runDue(d(2026, 9, 21))
        val second = service.runDue(d(2026, 9, 21))

        assertEquals(4, first.recorded)
        assertEquals(0, second.recorded)
        val recorded = local.transactions.between(d(2026, 1, 1), d(2026, 12, 31)).filter { it.origin == TransactionOrigin.RECURRING }
        assertEquals(listOf(d(2026, 9, 15), d(2026, 8, 15), d(2026, 7, 15), d(2026, 6, 15)), recorded.map { it.occurredOn })
        assertEquals(d(2026, 10, 15), local.recurring.all().single().nextDue)
    }

    @Test
    fun `pemasukan berulang menyimpan potret alokasinya di database`() = runBlocking {
        val account = local.accounts.activeAccounts().single()
        service.create(RecurringDraft(TransactionKind.INCOME, Money.rupiah(10_000_000), account.id, incomeSource = "Gaji", frequency = Frequency.MONTHLY, startDate = d(2026, 9, 1)), d(2026, 9, 1))

        service.runDue(d(2026, 9, 21))

        val income = local.transactions.between(d(2026, 9, 1), d(2026, 9, 30)).single()
        assertEquals(TransactionOrigin.RECURRING, income.origin)
        assertTrue(local.transactions.entriesOf(income.id).isNotEmpty())
    }

    @Test
    fun `hapus aturan tidak menghapus transaksi yang sudah dicatat`() = runBlocking {
        val rule = (service.create(expenseDraft(d(2026, 9, 1)), d(2026, 9, 1)) as LedgerResult.Success).value
        service.runDue(d(2026, 9, 21))

        service.delete(rule.id)

        assertNull(local.recurring.find(rule.id))
        assertEquals(1, local.transactions.between(d(2026, 9, 1), d(2026, 9, 30)).size)
    }
}
