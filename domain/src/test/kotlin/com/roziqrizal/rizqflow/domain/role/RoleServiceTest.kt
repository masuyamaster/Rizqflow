package com.roziqrizal.rizqflow.domain.role

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.entitlement.Plan
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.ledger.InMemorySettings
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerFixture
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.NewExpense
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoleServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private class Env(val f: LedgerFixture, plans: Set<Plan>) {
        val roles = InMemoryRoles()
        val settings = InMemorySettings()
        val service = RoleService(f.store, f.store, f.store, roles, f.ledger, PlanEntitlements(plans))
        val reminders = DcaReminderService(service, settings)
        val diri = f.room("Diri")
        val investasi = f.category("Diri", "Investasi")
    }

    private fun env(plans: Set<Plan> = setOf(Plan.PRO)): Env = Env(LedgerFixture().standard(), plans)

    private fun <T> LedgerResult<T>.error() = (this as LedgerResult.Failure).error

    private fun Env.setDca(amount: Long = 500_000, day: Int = 5) =
        runSuspend { service.setDca(diri.id, rupiah(amount), day, f.account.id, investasi.id) }

    private fun Env.spendOnInvestasi(amount: Long, on: LocalDate = f.today) = runSuspend {
        f.ledger.recordExpense(NewExpense(rupiah(amount), f.account.id, diri.id, investasi.id, on))
    }

    // ------------------------------------------------------------------ model

    @Test
    fun `batas risiko dihitung eksak dari modal dan basis point dan dibulatkan ke bawah`() {
        val profile = TraderProfile(RoomId("r"), rupiah(10_000_000), BasisPoints(150))

        assertEquals(rupiah(150_000), profile.maxRiskPerTrade)
        assertEquals(rupiah(4), TraderProfile(RoomId("r"), rupiah(1_999), BasisPoints(25)).maxRiskPerTrade)
    }

    @Test
    fun `model menolak modal nol risiko di luar jangkauan dan tanggal DCA di luar 1 sampai 28`() {
        assertFailsWith<IllegalArgumentException> { TraderProfile(RoomId("r"), rupiah(0), BasisPoints(100)) }
        assertFailsWith<IllegalArgumentException> { TraderProfile(RoomId("r"), rupiah(1), BasisPoints(0)) }
        assertFailsWith<IllegalArgumentException> { TraderProfile(RoomId("r"), rupiah(1), BasisPoints(TraderProfile.MAX_RISK_BP + 1)) }
        val f = LedgerFixture().standard()
        val ids = Triple(f.account.id, f.category("Diri", "Investasi").id, f.room("Diri").id)
        assertFailsWith<IllegalArgumentException> { DcaPlan(ids.third, rupiah(1), 0, ids.first, ids.second) }
        assertFailsWith<IllegalArgumentException> { DcaPlan(ids.third, rupiah(1), 29, ids.first, ids.second) }
    }

    // ------------------------------------------------------------------ Trader

    @Test
    fun `Trader tersimpan dan terbaca lewat overview`() {
        val e = env()

        assertIs<LedgerResult.Success<Unit>>(runSuspend { e.service.setTrader(e.diri.id, rupiah(10_000_000), BasisPoints(200)) })

        val overview = runSuspend { e.service.overview(e.diri.id, e.f.today) }!!
        assertEquals(RoleKind.TRADER, overview.kind)
        assertEquals(rupiah(200_000), overview.trader!!.maxRiskPerTrade)
        assertNull(overview.dca)
    }

    @Test
    fun `memasang peran butuh Pro tetapi tidak menyentuh data bila ditolak`() {
        val e = env(plans = emptySet())

        val result = runSuspend { e.service.setTrader(e.diri.id, rupiah(1_000_000), BasisPoints(100)) }

        assertEquals(LedgerError.FEATURE_LOCKED, result.error())
        assertNull(e.roles.traders[e.diri.id])
        assertEquals(LedgerError.FEATURE_LOCKED, e.setDca().error())
    }

    @Test
    fun `isian Trader yang tidak sah ditolak dengan galat yang jelas`() {
        val e = env()

        assertEquals(LedgerError.INVALID_ROLE_SETTINGS, runSuspend { e.service.setTrader(e.diri.id, rupiah(0), BasisPoints(100)) }.error())
        assertEquals(LedgerError.INVALID_ROLE_SETTINGS, runSuspend { e.service.setTrader(e.diri.id, rupiah(1_000), BasisPoints(0)) }.error())
        assertEquals(LedgerError.INVALID_ROLE_SETTINGS, runSuspend { e.service.setTrader(e.diri.id, rupiah(1_000), BasisPoints(TraderProfile.MAX_RISK_BP + 1)) }.error())
        assertEquals(LedgerError.ROOM_NOT_FOUND, runSuspend { e.service.setTrader(RoomId("tidak-ada"), rupiah(1_000), BasisPoints(100)) }.error())
    }

    @Test
    fun `ruang terarsip tidak bisa diberi peran`() {
        val e = env()
        runSuspend { e.f.rules.archiveRoom(e.diri.id) }

        assertEquals(LedgerError.ROOM_ARCHIVED, runSuspend { e.service.setTrader(e.diri.id, rupiah(1_000), BasisPoints(100)) }.error())
    }

    @Test
    fun `pengeluaran di atas batas risiko memunculkan peringatan dan yang tepat di batas tidak`() {
        val e = env()
        runSuspend { e.service.setTrader(e.diri.id, rupiah(10_000_000), BasisPoints(100)) }

        val warning = runSuspend { e.service.riskWarning(e.diri.id, rupiah(100_001)) }

        assertNotNull(warning)
        assertEquals(rupiah(100_000), warning.maxRisk)
        assertNull(runSuspend { e.service.riskWarning(e.diri.id, rupiah(100_000)) })
        assertNull(runSuspend { e.service.riskWarning(e.diri.id, rupiah(50_000)) })
    }

    @Test
    fun `ruang tanpa Trader tidak pernah memunculkan peringatan risiko`() {
        val e = env()

        assertNull(runSuspend { e.service.riskWarning(e.diri.id, rupiah(999_999_999)) })
    }

    @Test
    fun `turun paket membuat peringatan diam tetapi pengaturan tetap tersimpan`() {
        val pro = env()
        runSuspend { pro.service.setTrader(pro.diri.id, rupiah(10_000_000), BasisPoints(100)) }
        val lapsed = Env(pro.f, emptySet()).also { it.roles.traders.putAll(pro.roles.traders) }

        assertNull(runSuspend { lapsed.service.riskWarning(lapsed.diri.id, rupiah(999_999)) })
        val overview = runSuspend { lapsed.service.overview(lapsed.diri.id, lapsed.f.today) }!!
        assertTrue(!overview.entitled)
        assertNotNull(overview.trader)
    }

    // ------------------------------------------------------------------ Investor (DCA)

    @Test
    fun `DCA tersimpan dan sebelum tanggalnya berstatus akan datang`() {
        val e = env()

        assertIs<LedgerResult.Success<Unit>>(e.setDca(day = 25))

        val view = runSuspend { e.service.overview(e.diri.id, e.f.today) }!!.dca!!
        assertEquals(DcaStatus.Upcoming(LocalDate.of(2026, 9, 25)), view.status)
        assertEquals("Investasi", view.categoryName)
    }

    @Test
    fun `DCA jatuh tempo pada tanggalnya dan sesudahnya sampai nominalnya tercatat`() {
        val e = env()
        e.setDca(amount = 500_000, day = 21) // hari ini 21 September 2026

        val onTheDay = runSuspend { e.service.overview(e.diri.id, e.f.today) }!!.dca!!.status
        val later = runSuspend { e.service.overview(e.diri.id, LocalDate.of(2026, 9, 28)) }!!.dca!!.status

        assertEquals(DcaStatus.Due(LocalDate.of(2026, 9, 21), rupiah(0)), onTheDay)
        assertEquals(DcaStatus.Due(LocalDate.of(2026, 9, 21), rupiah(0)), later)
    }

    @Test
    fun `mencatat DCA membuat pengeluaran di kategori DCA dan status menjadi selesai`() {
        val e = env()
        e.setDca(amount = 500_000, day = 5)

        val id = (runSuspend { e.service.recordDca(e.diri.id, e.f.today) } as LedgerResult.Success).value

        val tx = e.f.store.transactionRows.getValue(id)
        assertEquals(TransactionKind.EXPENSE, tx.kind)
        assertEquals(rupiah(500_000), tx.amount)
        assertEquals(e.investasi.id, tx.categoryId)
        assertEquals(RoleService.DCA_NOTE, tx.note)
        assertEquals(rupiah(0), e.f.balance()) // Rp 500.000 di dompet, seluruhnya diinvestasikan
        assertIs<DcaStatus.Done>(runSuspend { e.service.overview(e.diri.id, e.f.today) }!!.dca!!.status)
    }

    @Test
    fun `pengeluaran manual di kategori DCA juga dihitung dan pecahan dijumlahkan`() {
        val e = env()
        e.setDca(amount = 500_000, day = 5)

        e.spendOnInvestasi(200_000)
        val partial = runSuspend { e.service.overview(e.diri.id, e.f.today) }!!.dca!!.status
        e.spendOnInvestasi(300_000)
        val full = runSuspend { e.service.overview(e.diri.id, e.f.today) }!!.dca!!.status

        assertEquals(DcaStatus.Due(LocalDate.of(2026, 9, 5), rupiah(200_000)), partial)
        assertEquals(DcaStatus.Done(rupiah(500_000)), full)
    }

    @Test
    fun `pengeluaran bulan lain atau kategori lain tidak dihitung`() {
        val e = env()
        e.setDca(amount = 500_000, day = 5)
        e.spendOnInvestasi(500_000, on = LocalDate.of(2026, 8, 30))
        runSuspend {
            e.f.ledger.recordExpense(NewExpense(rupiah(500_000), e.f.account.id, e.diri.id, e.f.category("Diri", "Belajar").id, e.f.today))
        }

        assertIs<DcaStatus.Due>(runSuspend { e.service.overview(e.diri.id, e.f.today) }!!.dca!!.status)
    }

    @Test
    fun `DCA hanya boleh memakai kategori biasa milik ruang itu dan akun aktif`() {
        val e = env()
        val sistem = e.f.category("Diri", "Tak terlacak")
        val milikRuangLain = e.f.category("Keluarga", "Sekolah")
        val arsip = e.f.addAccount("Lama", archived = true)

        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, runSuspend { e.service.setDca(e.diri.id, rupiah(1_000), 5, e.f.account.id, sistem.id) }.error())
        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, runSuspend { e.service.setDca(e.diri.id, rupiah(1_000), 5, e.f.account.id, milikRuangLain.id) }.error())
        assertEquals(LedgerError.ACCOUNT_ARCHIVED, runSuspend { e.service.setDca(e.diri.id, rupiah(1_000), 5, arsip.id, e.investasi.id) }.error())
        assertEquals(LedgerError.INVALID_ROLE_SETTINGS, runSuspend { e.service.setDca(e.diri.id, rupiah(1_000), 29, e.f.account.id, e.investasi.id) }.error())
        assertEquals(LedgerError.INVALID_ROLE_SETTINGS, runSuspend { e.service.setDca(e.diri.id, rupiah(0), 5, e.f.account.id, e.investasi.id) }.error())
    }

    @Test
    fun `mencatat DCA tanpa jadwal ditolak`() {
        val e = env()

        assertEquals(LedgerError.ROLE_NOT_SET, runSuspend { e.service.recordDca(e.diri.id, e.f.today) }.error())
    }

    // ------------------------------------------------------------------ satu ruang satu peran

    @Test
    fun `ruang yang sudah Trader menolak DCA dan sebaliknya sampai peran dilepas`() {
        val e = env()
        runSuspend { e.service.setTrader(e.diri.id, rupiah(1_000_000), BasisPoints(100)) }

        assertEquals(LedgerError.ROLE_CONFLICT, e.setDca().error())

        runSuspend { e.service.remove(e.diri.id) }
        assertIs<LedgerResult.Success<Unit>>(e.setDca())
        assertEquals(
            LedgerError.ROLE_CONFLICT,
            runSuspend { e.service.setTrader(e.diri.id, rupiah(1_000_000), BasisPoints(100)) }.error(),
        )
    }

    @Test
    fun `melepas peran selalu boleh walau paket tidak membukanya dan transaksi lama tetap`() {
        val pro = env()
        pro.setDca()
        pro.service.let { runSuspend { it.recordDca(pro.diri.id, pro.f.today) } }
        val lapsed = Env(pro.f, emptySet()).also { it.roles.plans.putAll(pro.roles.plans) }

        runSuspend { lapsed.service.remove(lapsed.diri.id) }

        assertNull(runSuspend { lapsed.service.overview(lapsed.diri.id, lapsed.f.today) }!!.kind)
        assertEquals(1, pro.f.store.transactionRows.values.count { it.kind == TransactionKind.EXPENSE })
    }

    // ------------------------------------------------------------------ pengingat

    @Test
    fun `pengingat DCA hanya untuk jadwal jatuh tempo dan disapa sekali per bulan`() {
        val e = env()
        e.setDca(amount = 500_000, day = 21)

        val first = runSuspend { e.reminders.dueReminders(e.f.today) }
        assertEquals(1, first.size)
        runSuspend { e.reminders.markNotified(first.single()) }

        assertTrue(runSuspend { e.reminders.dueReminders(LocalDate.of(2026, 9, 22)) }.isEmpty())
        // Bulan berikutnya tanggalnya tiba lagi: disapa lagi.
        assertEquals(1, runSuspend { e.reminders.dueReminders(LocalDate.of(2026, 10, 21)) }.size)
    }

    @Test
    fun `tidak ada pengingat bila belum jatuh tempo atau nominalnya sudah tercatat`() {
        val e = env()
        e.setDca(amount = 500_000, day = 25)
        assertTrue(runSuspend { e.reminders.dueReminders(e.f.today) }.isEmpty())

        e.setDca(amount = 500_000, day = 5)
        e.spendOnInvestasi(500_000)
        assertTrue(runSuspend { e.reminders.dueReminders(e.f.today) }.isEmpty())
    }

    @Test
    fun `tidak ada pengingat bila paket tidak membukanya`() {
        val pro = env()
        pro.setDca(amount = 500_000, day = 5)
        val lapsed = Env(pro.f, emptySet()).also { it.roles.plans.putAll(pro.roles.plans) }

        assertTrue(runSuspend { lapsed.reminders.dueReminders(pro.f.today) }.isEmpty())
    }

    @Test
    fun `ruang terarsip tidak mengirim pengingat DCA`() {
        val e = env()
        e.setDca(amount = 500_000, day = 5)
        runSuspend { e.f.rules.archiveRoom(e.diri.id) }

        assertTrue(runSuspend { e.reminders.dueReminders(e.f.today) }.isEmpty())
    }
}
