package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.calendar.UmmAlQuraCalendar
import com.roziqrizal.rizqflow.domain.ledger.InMemorySettings
import com.roziqrizal.rizqflow.domain.ledger.LedgerFixture
import com.roziqrizal.rizqflow.domain.model.WealthKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HaulReminderServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private class Env(f: LedgerFixture) {
        val zakatRepo = InMemoryZakat()
        val settings = InMemorySettings()
        val zakat = ZakatService(f.store, f.store, zakatRepo, f.ledger, f.newId, UmmAlQuraCalendar())
        val service = HaulReminderService(f.store, zakat, settings)
        val room = f.room("Memberi").id
    }

    private fun env(): Pair<LedgerFixture, Env> {
        val f = LedgerFixture().standard()
        return f to Env(f)
    }

    @Test
    fun `ruang yang masih mode persentase donasi tidak pernah menghasilkan pengingat`() {
        val (f, e) = env()

        assertTrue(runSuspend { e.service.dueReminders(f.today) }.isEmpty())
    }

    @Test
    fun `belum mencapai nisab tidak menghasilkan pengingat`() {
        val (f, e) = env()
        runSuspend { e.zakat.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }

        assertTrue(runSuspend { e.service.dueReminders(f.today) }.isEmpty())
    }

    @Test
    fun `haul berjalan jauh dari jatuh tempo tidak menghasilkan pengingat`() {
        val (_, e) = env()
        val awal = LocalDate.of(2026, 1, 10)
        runSuspend { e.zakat.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        runSuspend { e.zakat.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }

        assertTrue(runSuspend { e.service.dueReminders(awal.plusMonths(6)) }.isEmpty())
    }

    @Test
    fun `haul mendekati jatuh tempo menghasilkan pengingat sekali sampai ditandai`() {
        val (_, e) = env()
        val awal = LocalDate.of(2026, 1, 10)
        val genap = UmmAlQuraCalendar().plusYears(awal)
        val mendekati = genap.minusDays(5)
        runSuspend { e.zakat.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        runSuspend { e.zakat.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }

        val pengingat = runSuspend { e.service.dueReminders(mendekati) }

        assertEquals(1, pengingat.size)
        assertEquals(e.room, pengingat.first().roomId)
        assertIs<HaulStatus.Running>(pengingat.first().status)

        runSuspend { e.service.markNotified(pengingat.first()) }

        assertTrue(runSuspend { e.service.dueReminders(mendekati) }.isEmpty())
        assertTrue(runSuspend { e.service.dueReminders(mendekati.plusDays(1)) }.isEmpty())
    }

    @Test
    fun `haul genap menghasilkan pengingat berbeda dari pengingat mendekati`() {
        val (_, e) = env()
        val awal = LocalDate.of(2026, 1, 10)
        val genap = UmmAlQuraCalendar().plusYears(awal)
        runSuspend { e.zakat.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        runSuspend { e.zakat.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }
        val mendekati = runSuspend { e.service.dueReminders(genap.minusDays(5)) }.first()
        runSuspend { e.service.markNotified(mendekati) }

        val pengingat = runSuspend { e.service.dueReminders(genap) }

        assertEquals(1, pengingat.size)
        assertIs<HaulStatus.Completed>(pengingat.first().status)

        runSuspend { e.service.markNotified(pengingat.first()) }

        assertTrue(runSuspend { e.service.dueReminders(genap) }.isEmpty())
        assertTrue(runSuspend { e.service.dueReminders(genap.plusDays(3)) }.isEmpty())
    }

    @Test
    fun `menunaikan zakat memulai haul baru dan menghentikan pengingat lama`() {
        val (f, e) = env()
        val awal = LocalDate.of(2026, 1, 10)
        val genap = UmmAlQuraCalendar().plusYears(awal)
        runSuspend { e.zakat.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        runSuspend { e.zakat.saveWealth(listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(200_000_000))), rupiah(1_660_000), awal) }
        val genapReminder = runSuspend { e.service.dueReminders(genap) }.first()
        runSuspend { e.service.markNotified(genapReminder) }

        runSuspend { e.zakat.payZakat(e.room, f.account.id, rupiah(5_000_000), genap) }

        assertTrue(runSuspend { e.service.dueReminders(genap) }.isEmpty())
    }
}
