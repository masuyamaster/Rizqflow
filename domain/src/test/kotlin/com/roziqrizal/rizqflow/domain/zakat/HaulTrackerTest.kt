package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.calendar.UmmAlQuraCalendar
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HaulTrackerTest {

    private val kalender = UmmAlQuraCalendar()
    private val nisab = Money.rupiah(141_100_000)
    private val diAtas = Money.rupiah(152_000_000)
    private val diBawah = Money.rupiah(100_000_000)

    private fun tracker(kebijakan: HaulBreakPolicy = HaulBreakPolicy.RESET_WHEN_BELOW_NISAB) =
        HaulTracker(kalender, ZakatAssumptions(haulBreakPolicy = kebijakan))

    private fun cek(tanggal: LocalDate, harta: Money) = HaulEvent.WealthChecked(tanggal, harta, nisab)

    private val awal = LocalDate.of(2026, 1, 10)

    @Test
    fun `tanpa pemeriksaan harta belum ada haul`() {
        assertEquals(HaulStatus.BelowNisab, tracker().status(emptyList(), awal))
    }

    @Test
    fun `harta di bawah nisab belum memulai haul`() {
        assertEquals(HaulStatus.BelowNisab, tracker().status(listOf(cek(awal, diBawah)), awal.plusDays(30)))
    }

    @Test
    fun `harta tepat sama dengan nisab sudah memulai haul`() {
        val status = tracker().status(listOf(cek(awal, nisab)), awal)

        assertIs<HaulStatus.Running>(status)
    }

    @Test
    fun `haul dimulai saat harta pertama kali mencapai nisab`() {
        val events = listOf(cek(awal.minusDays(60), diBawah), cek(awal, diAtas))

        val status = assertIs<HaulStatus.Running>(tracker().status(events, awal.plusDays(10)))

        assertEquals(awal, status.start)
        assertEquals(kalender.plusYears(awal), status.due)
        assertEquals(10L, status.elapsedDays)
        assertTrue(status.totalDays in 354L..355L)
        assertEquals(status.totalDays - 10L, status.daysLeft)
    }

    @Test
    fun `haul genap tepat pada tanggal jatuh tempo`() {
        val due = kalender.plusYears(awal)

        assertIs<HaulStatus.Running>(tracker().status(listOf(cek(awal, diAtas)), due.minusDays(1)))
        val status = assertIs<HaulStatus.Completed>(tracker().status(listOf(cek(awal, diAtas)), due))

        assertEquals(awal, status.start)
        assertEquals(due, status.due)
    }

    @Test
    fun `haul tetap genap sampai zakat ditunaikan`() {
        val due = kalender.plusYears(awal)

        assertIs<HaulStatus.Completed>(tracker().status(listOf(cek(awal, diAtas)), due.plusDays(40)))
    }

    @Test
    fun `turun di bawah nisab memutus haul lalu dimulai lagi dari nol`() {
        val kembali = awal.plusDays(80)
        val events = listOf(cek(awal, diAtas), cek(awal.plusDays(40), diBawah), cek(kembali, diAtas))

        val status = assertIs<HaulStatus.Running>(tracker().status(events, kembali.plusDays(5)))

        assertEquals(kembali, status.start)
    }

    @Test
    fun `turun di bawah nisab tanpa kembali berarti tidak ada haul`() {
        val events = listOf(cek(awal, diAtas), cek(awal.plusDays(40), diBawah))

        assertEquals(HaulStatus.BelowNisab, tracker().status(events, awal.plusDays(60)))
    }

    @Test
    fun `kebijakan awal dan akhir mengabaikan penurunan di tengah tahun`() {
        val events = listOf(cek(awal, diAtas), cek(awal.plusDays(40), diBawah), cek(awal.plusDays(80), diAtas))

        val status = assertIs<HaulStatus.Running>(
            tracker(HaulBreakPolicy.CHECK_ONLY_AT_START_AND_END).status(events, awal.plusDays(100)),
        )

        assertEquals(awal, status.start)
    }

    @Test
    fun `kebijakan awal dan akhir tidak menganggap haul genap bila di akhir di bawah nisab`() {
        val due = kalender.plusYears(awal)
        val events = listOf(cek(awal, diAtas), cek(due, diBawah))

        assertEquals(HaulStatus.BelowNisab, tracker(HaulBreakPolicy.CHECK_ONLY_AT_START_AND_END).status(events, due))
    }

    @Test
    fun `kebijakan awal dan akhir menganggap haul genap bila di akhir masih di atas nisab`() {
        val due = kalender.plusYears(awal)
        val events = listOf(cek(awal, diAtas), cek(awal.plusDays(100), diBawah), cek(due, diAtas))

        assertIs<HaulStatus.Completed>(tracker(HaulBreakPolicy.CHECK_ONLY_AT_START_AND_END).status(events, due))
    }

    @Test
    fun `menunaikan zakat memulai haul baru bila harta masih di atas nisab`() {
        val due = kalender.plusYears(awal)
        val events = listOf(cek(awal, diAtas), cek(due, diAtas), HaulEvent.ZakatPaid(due.plusDays(2)))

        val status = assertIs<HaulStatus.Running>(tracker().status(events, due.plusDays(10)))

        assertEquals(due.plusDays(2), status.start)
        assertEquals(8L, status.elapsedDays)
    }

    @Test
    fun `menunaikan zakat saat harta di bawah nisab tidak memulai haul baru`() {
        val due = kalender.plusYears(awal)
        val events = listOf(cek(awal, diAtas), cek(due, diBawah), HaulEvent.ZakatPaid(due.plusDays(1)))

        assertEquals(HaulStatus.BelowNisab, tracker().status(events, due.plusDays(5)))
    }

    @Test
    fun `kejadian setelah hari ini diabaikan`() {
        val events = listOf(cek(awal, diAtas), cek(awal.plusDays(50), diBawah))

        val status = assertIs<HaulStatus.Running>(tracker().status(events, awal.plusDays(20)))

        assertEquals(awal, status.start)
    }

    @Test
    fun `urutan kejadian masukan tidak memengaruhi hasil`() {
        val urut = listOf(cek(awal, diAtas), cek(awal.plusDays(40), diBawah), cek(awal.plusDays(80), diAtas))

        assertEquals(tracker().status(urut, awal.plusDays(90)), tracker().status(urut.reversed(), awal.plusDays(90)))
    }

    @Test
    fun `hari yang lewat dihitung dalam hari Masehi`() {
        val status = assertIs<HaulStatus.Running>(tracker().status(listOf(cek(awal, diAtas)), awal.plusDays(117)))

        assertEquals(ChronoUnit.DAYS.between(awal, awal.plusDays(117)), status.elapsedDays)
    }
}
