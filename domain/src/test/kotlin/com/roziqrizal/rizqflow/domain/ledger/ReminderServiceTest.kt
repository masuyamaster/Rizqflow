package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private class Fixture(val f: LedgerFixture = LedgerFixture().standard(), val settings: InMemorySettings = InMemorySettings()) {
        val service = ReminderService(settings, f.store, f.store, f.store, f.ledger)
    }

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success).value

    private fun <T> LedgerResult<T>.error(): LedgerError = (this as LedgerResult.Failure).error

    // ------------------------------------------------------------------ pengaturan

    @Test
    fun `pengaturan bawaan aktif jam 20-00`() {
        val x = Fixture()

        assertEquals(ReminderSettings.DEFAULT, runSuspend { x.service.settings() })
    }

    @Test
    fun `pengaturan tersimpan dan terbaca lagi`() {
        val x = Fixture()

        runSuspend { x.service.updateSettings(false, 21, 30) }

        assertEquals(ReminderSettings(false, 21, 30), runSuspend { x.service.settings() })
    }

    @Test
    fun `jam atau menit di luar jangkauan ditolak`() {
        val x = Fixture()

        assertEquals(LedgerError.INVALID_REMINDER_TIME, runSuspend { x.service.updateSettings(true, 24, 0) }.error())
        assertEquals(LedgerError.INVALID_REMINDER_TIME, runSuspend { x.service.updateSettings(true, 20, 60) }.error())
        assertEquals(LedgerError.INVALID_REMINDER_TIME, runSuspend { x.service.updateSettings(true, -1, 0) }.error())
    }

    // ------------------------------------------------------------------ izin notifikasi

    @Test
    fun `izin notifikasi belum pernah diminta secara bawaan`() {
        val x = Fixture()

        assertFalse(runSuspend { x.service.hasRequestedNotificationPermission() })
        runSuspend { x.service.markNotificationPermissionRequested() }

        assertTrue(runSuspend { x.service.hasRequestedNotificationPermission() })
    }

    // ------------------------------------------------------------------ Tidak ada

    @Test
    fun `Tidak ada menandai hanya hari itu sudah dicek`() {
        val x = Fixture()

        assertFalse(runSuspend { x.service.isTodayDismissed(x.f.today) })
        runSuspend { x.service.dismissToday(x.f.today) }

        assertTrue(runSuspend { x.service.isTodayDismissed(x.f.today) })
        assertFalse(runSuspend { x.service.isTodayDismissed(x.f.today.plusDays(1)) })
    }

    // ------------------------------------------------------------------ menguraikan balasan

    @Test
    fun `menguraikan balasan catatan dan nominal`() {
        val x = Fixture()

        assertEquals(ParsedQuickReply("kopi", rupiah(25_000)), x.service.parseQuickReply("kopi 25000"))
        assertEquals(ParsedQuickReply("kopi susu", rupiah(25_000)), x.service.parseQuickReply("  kopi susu   25.000  "))
    }

    @Test
    fun `balasan tanpa catatan atau tanpa nominal yang sah tidak terurai`() {
        val x = Fixture()

        assertNull(x.service.parseQuickReply("25000"))
        assertNull(x.service.parseQuickReply("kopi"))
        assertNull(x.service.parseQuickReply(""))
        assertNull(x.service.parseQuickReply("kopi 0"))
        assertNull(x.service.parseQuickReply("kopi -5000"))
    }

    // ------------------------------------------------------------------ mencatat balasan

    @Test
    fun `mencatat balasan di ruang dan kategori pengeluaran terakhir`() {
        val x = Fixture()
        runSuspend {
            x.f.ledger.recordExpense(NewExpense(rupiah(50_000), x.f.account.id, x.f.room("Diri").id, x.f.category("Diri", "Investasi").id, x.f.today))
        }

        val hasil = runSuspend { x.service.recordQuickReply("kopi 25000", x.f.today) }

        assertIs<LedgerResult.Success<*>>(hasil)
        val tx = runSuspend { x.f.store.find(hasil.value()) }!!
        assertEquals(TransactionKind.EXPENSE, tx.kind)
        assertEquals(rupiah(25_000), tx.amount)
        assertEquals("kopi", tx.note)
        assertEquals(x.f.room("Diri").id, tx.roomId)
        assertEquals(x.f.category("Diri", "Investasi").id, tx.categoryId)
    }

    @Test
    fun `mencatat balasan juga menandai hari itu sudah dicek`() {
        val x = Fixture()
        runSuspend {
            x.f.ledger.recordExpense(NewExpense(rupiah(50_000), x.f.account.id, x.f.room("Diri").id, x.f.category("Diri", "Investasi").id, x.f.today))
        }

        runSuspend { x.service.recordQuickReply("kopi 25000", x.f.today) }

        assertTrue(runSuspend { x.settings.isDayChecked(x.f.today) })
    }

    @Test
    fun `tanpa pengeluaran sebelumnya memakai ruang bertipe Mencukupi`() {
        val x = Fixture()

        val hasil = runSuspend { x.service.recordQuickReply("parkir 5000", x.f.today) }

        assertIs<LedgerResult.Success<*>>(hasil)
        val tx = runSuspend { x.f.store.find(hasil.value()) }!!
        assertEquals(x.f.room("Keluarga").id, tx.roomId)
    }

    @Test
    fun `balasan yang tidak bisa diuraikan ditolak dan tidak mencatat apa pun`() {
        val x = Fixture()

        val hasil = runSuspend { x.service.recordQuickReply("tidak jelas", LocalDate.of(2026, 9, 22)) }

        assertEquals(LedgerError.QUICK_REPLY_UNREADABLE, hasil.error())
        assertTrue(x.f.store.transactionRows.isEmpty())
    }
}
