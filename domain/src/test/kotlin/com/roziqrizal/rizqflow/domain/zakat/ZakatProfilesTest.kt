package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.calendar.UmmAlQuraCalendar
import com.roziqrizal.rizqflow.domain.entitlement.Plan
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.ledger.InMemorySettings
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerFixture
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.model.WealthKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Multi-profil harta zakat (Pro): tiap profil punya harta, haul, riwayat, dan pengingatnya sendiri. */
class ZakatProfilesTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private val awal = LocalDate.of(2026, 1, 10)

    private class Env(val f: LedgerFixture, plans: Set<Plan>) {
        val repo = InMemoryZakat()
        val settings = InMemorySettings()
        val service = ZakatService(f.store, f.store, repo, f.ledger, f.newId, UmmAlQuraCalendar(), entitlements = PlanEntitlements(plans))
        val reminders = HaulReminderService(f.store, service, settings)
        val room = f.room("Memberi").id
    }

    private fun env(plans: Set<Plan> = setOf(Plan.PRO)): Env {
        val e = Env(LedgerFixture().standard(), plans)
        runSuspend { e.service.setGivingMode(e.room, ZakatHaulHijriStrategy.ID) }
        return e
    }

    private fun cash(n: Long) = listOf(NewWealthItem(kind = WealthKind.CASH_SAVINGS, label = "Tabungan", value = rupiah(n)))

    private fun Env.fill(profileId: String?, value: Long, on: LocalDate = awal) =
        runSuspend { service.saveWealth(cash(value), rupiah(1_660_000), on, profileId) }

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success).value

    private fun <T> LedgerResult<T>.error() = (this as LedgerResult.Failure).error

    // ------------------------------------------------------------------ menambah, mengganti nama, mengarsipkan

    @Test
    fun `profil pertama dibuat otomatis dan profil tambahan butuh Pro`() {
        val free = env(plans = emptySet())
        free.fill(null, 200_000_000)

        val overview = runSuspend { free.service.overview(free.room, awal) }!!
        assertEquals(listOf("Utama"), overview.profiles.map { it.name })
        assertTrue(!overview.canAddProfile)
        assertEquals(LedgerError.FEATURE_LOCKED, runSuspend { free.service.addProfile("Istri") }.error())

        val pro = env()
        pro.fill(null, 200_000_000)
        assertTrue(runSuspend { pro.service.overview(pro.room, awal) }!!.canAddProfile)
        assertEquals("Istri", runSuspend { pro.service.addProfile("  Istri ") }.value().name)
    }

    @Test
    fun `nama profil tidak boleh kosong terlalu panjang atau kembar`() {
        val e = env()
        e.fill(null, 200_000_000)

        assertEquals(LedgerError.INVALID_NAME, runSuspend { e.service.addProfile("   ") }.error())
        assertEquals(LedgerError.INVALID_NAME, runSuspend { e.service.addProfile("x".repeat(ZakatProfile.NAME_MAX + 1)) }.error())
        assertEquals(LedgerError.NAME_TAKEN, runSuspend { e.service.addProfile("utama") }.error())
    }

    @Test
    fun `mengganti nama tetap boleh tanpa Pro dan menolak nama yang dipakai profil lain`() {
        val pro = env()
        pro.fill(null, 200_000_000)
        val istri = runSuspend { pro.service.addProfile("Istri") }.value()
        val lapsed = Env(pro.f, emptySet()).also { it.repo.profileRows.putAll(pro.repo.profileRows) }

        assertIs<LedgerResult.Success<Unit>>(runSuspend { lapsed.service.renameProfile(istri.id, "Usaha") })
        assertEquals(LedgerError.NAME_TAKEN, runSuspend { lapsed.service.renameProfile(istri.id, "Utama") }.error())
        assertEquals(LedgerError.PROFILE_NOT_FOUND, runSuspend { lapsed.service.renameProfile("tidak-ada", "Baru") }.error())
    }

    @Test
    fun `arsip menyembunyikan profil tetapi profil aktif terakhir tidak bisa diarsipkan`() {
        val e = env()
        e.fill(null, 200_000_000)
        val istri = runSuspend { e.service.addProfile("Istri") }.value()

        assertIs<LedgerResult.Success<Unit>>(runSuspend { e.service.archiveProfile(istri.id) })
        val remaining = runSuspend { e.service.overview(e.room, awal) }!!.profiles
        assertEquals(listOf("Utama"), remaining.map { it.name })
        assertEquals(LedgerError.LAST_PROFILE, runSuspend { e.service.archiveProfile(remaining.single().id) }.error())
        assertEquals(LedgerError.PROFILE_NOT_FOUND, runSuspend { e.service.archiveProfile(istri.id) }.error())
    }

    // ------------------------------------------------------------------ profil berdiri sendiri

    @Test
    fun `profil baru belum bisa dinilai dan meminta harta diisi bukan belum mencapai nisab`() {
        val e = env()
        e.fill(null, 200_000_000)
        val istri = runSuspend { e.service.addProfile("Istri") }.value()

        val overview = runSuspend { e.service.overview(e.room, awal, istri.id) }!!

        assertEquals(istri.id, overview.profile!!.id)
        assertIs<GivingStatus.NeedsInput>(overview.status)
        assertTrue(overview.items.isEmpty())
    }

    @Test
    fun `harta dan haul tiap profil terpisah`() {
        val e = env()
        e.fill(null, 200_000_000)
        val istri = runSuspend { e.service.addProfile("Istri") }.value()
        e.fill(istri.id, 1_000_000, on = awal.plusDays(20))

        val utama = runSuspend { e.service.overview(e.room, awal.plusDays(30)) }!!
        val punyaIstri = runSuspend { e.service.overview(e.room, awal.plusDays(30), istri.id) }!!

        val statusUtama = utama.status as GivingStatus.Zakat
        val statusIstri = punyaIstri.status as GivingStatus.Zakat
        assertEquals(rupiah(200_000_000), statusUtama.netWealth)
        assertIs<HaulStatus.Running>(statusUtama.haul)
        assertEquals(rupiah(1_000_000), statusIstri.netWealth)
        assertEquals(HaulStatus.BelowNisab, statusIstri.haul)
    }

    @Test
    fun `menunaikan zakat satu profil tidak mereset haul profil lain`() {
        val e = env()
        e.fill(null, 200_000_000)
        val istri = runSuspend { e.service.addProfile("Istri") }.value()
        e.fill(istri.id, 200_000_000)
        val genap = UmmAlQuraCalendar().plusYears(awal)

        runSuspend { e.service.payZakat(e.room, e.f.account.id, rupiah(5_000_000), genap, profileId = istri.id) }

        val utama = runSuspend { e.service.overview(e.room, genap) }!!
        val punyaIstri = runSuspend { e.service.overview(e.room, genap, istri.id) }!!
        assertIs<HaulStatus.Completed>((utama.status as GivingStatus.Zakat).haul)
        assertIs<HaulStatus.Running>((punyaIstri.status as GivingStatus.Zakat).haul)
        assertTrue(utama.payments.isEmpty())
        assertEquals(1, punyaIstri.payments.size)
    }

    @Test
    fun `menyimpan atau menunaikan untuk profil yang tidak ada ditolak`() {
        val e = env()
        e.fill(null, 200_000_000)

        assertEquals(LedgerError.PROFILE_NOT_FOUND, e.fill("tidak-ada", 1_000_000).error())
        assertEquals(
            LedgerError.PROFILE_NOT_FOUND,
            runSuspend { e.service.payZakat(e.room, e.f.account.id, rupiah(1_000), awal, profileId = "tidak-ada") }.error(),
        )
    }

    @Test
    fun `profil terarsip tidak bisa dipilih lagi dan pilihan tak dikenal jatuh ke profil pertama`() {
        val e = env()
        e.fill(null, 200_000_000)
        val istri = runSuspend { e.service.addProfile("Istri") }.value()
        runSuspend { e.service.archiveProfile(istri.id) }

        val overview = runSuspend { e.service.overview(e.room, awal, istri.id) }!!

        assertEquals("Utama", overview.profile!!.name)
    }

    @Test
    fun `turun paket tidak mengunci profil yang sudah ada`() {
        val pro = env()
        pro.fill(null, 200_000_000)
        val istri = runSuspend { pro.service.addProfile("Istri") }.value()
        val lapsed = Env(pro.f, emptySet()).also { it.repo.profileRows.putAll(pro.repo.profileRows) }

        assertIs<LedgerResult.Success<Unit>>(lapsed.fill(istri.id, 5_000_000))
        val overview = runSuspend { lapsed.service.overview(lapsed.room, awal, istri.id) }!!
        assertEquals(2, overview.profiles.size)
        assertTrue(!overview.canAddProfile)
    }

    // ------------------------------------------------------------------ pengingat per profil

    @Test
    fun `pengingat haul disapa per profil dan menyebut profil bila lebih dari satu`() {
        val e = env()
        e.fill(null, 200_000_000)
        val istri = runSuspend { e.service.addProfile("Istri") }.value()
        e.fill(istri.id, 200_000_000, on = awal.plusDays(1))
        val genap = UmmAlQuraCalendar().plusYears(awal)

        val due = runSuspend { e.reminders.dueReminders(genap.plusDays(1)) }

        assertEquals(setOf("Utama", "Istri"), due.map { it.profileName }.toSet())
        assertTrue(due.all { it.showProfileName })
        val utama = due.first { it.profileName == "Utama" }
        runSuspend { e.reminders.markNotified(utama) }
        assertEquals(listOf("Istri"), runSuspend { e.reminders.dueReminders(genap.plusDays(1)) }.map { it.profileName })
    }

    @Test
    fun `tanpa Pro hanya profil pertama yang disapa`() {
        val pro = env()
        pro.fill(null, 200_000_000)
        val istri = runSuspend { pro.service.addProfile("Istri") }.value()
        pro.fill(istri.id, 200_000_000)
        val lapsed = Env(pro.f, emptySet())
        // Seluruh data profil dibawa supaya profil kedua benar-benar punya haul yang jatuh tempo.
        lapsed.repo.profileRows.putAll(pro.repo.profileRows)
        lapsed.repo.checkRows.addAll(pro.repo.checkRows)
        lapsed.repo.itemRows.putAll(pro.repo.itemRows)
        lapsed.repo.goldPrices.addAll(pro.repo.goldPrices)
        val genap = UmmAlQuraCalendar().plusYears(awal)

        val due = runSuspend { lapsed.reminders.dueReminders(genap.plusDays(1)) }

        assertEquals(listOf("Utama"), due.map { it.profileName })
    }

    @Test
    fun `dengan satu profil pengingat tidak menyebut nama profil`() {
        val e = env()
        e.fill(null, 200_000_000)
        val genap = UmmAlQuraCalendar().plusYears(awal)

        val due = runSuspend { e.reminders.dueReminders(genap) }

        assertEquals(1, due.size)
        assertNotNull(due.single().profileName)
        assertTrue(!due.single().showProfileName)
    }

    @Test
    fun `profil terarsip tidak disapa dan profil tanpa harta tidak menghasilkan pengingat`() {
        val e = env()
        e.fill(null, 200_000_000)
        val istri = runSuspend { e.service.addProfile("Istri") }.value()
        val genap = UmmAlQuraCalendar().plusYears(awal)

        assertEquals(listOf("Utama"), runSuspend { e.reminders.dueReminders(genap) }.map { it.profileName })

        e.fill(istri.id, 200_000_000)
        runSuspend { e.service.archiveProfile(istri.id) }
        assertNull(runSuspend { e.reminders.dueReminders(genap) }.firstOrNull { it.profileName == "Istri" })
    }
}
