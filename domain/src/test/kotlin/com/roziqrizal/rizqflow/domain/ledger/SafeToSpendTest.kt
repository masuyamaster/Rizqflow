package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeToSpendTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    // 24 September 2026: sisa 7 hari termasuk hari ini (24..30).
    private val today = LocalDate.of(2026, 9, 24)

    private fun card(id: String, kind: RoomKind, allocated: Long, spent: Long) = RoomCard(
        Room(RoomId(id), id, kind, "home", 1, sortOrder = 1),
        rupiah(allocated),
        rupiah(spent),
        RoomStatusRules.progressBp(rupiah(allocated), rupiah(spent)),
        RoomStatusRules.statusOf(kind, rupiah(allocated), rupiah(spent), monthOver = false),
    )

    private fun compute(cards: List<RoomCard>, spentToday: Map<String, Long> = emptyMap(), on: LocalDate = today) =
        SafeToSpend.compute(cards, spentToday.map { RoomId(it.key) to rupiah(it.value) }.toMap(), on)

    @Test
    fun `jatah tersisa dibagi sisa hari termasuk hari ini`() {
        val safe = assertNotNull(compute(listOf(card("Kebutuhan", RoomKind.MENCUKUPI, 3_000_000, 1_600_000))))

        assertEquals(7, safe.daysLeft)
        assertEquals(rupiah(200_000), safe.dailyBudget)
        assertEquals(rupiah(200_000), safe.remaining)
        assertFalse(safe.isOver)
    }

    @Test
    fun `pengeluaran hari ini mengurangi sisa tetapi tidak mengubah jatah harian`() {
        val cards = listOf(card("Kebutuhan", RoomKind.MENCUKUPI, 3_000_000, 1_750_000))
        // Sebelum hari ini terpakai 1.600.000 (1.750.000 - 150.000), jadi jatah harian tetap 200.000.
        val safe = assertNotNull(compute(cards, mapOf("Kebutuhan" to 150_000)))

        assertEquals(rupiah(200_000), safe.dailyBudget)
        assertEquals(rupiah(150_000), safe.spentToday)
        assertEquals(rupiah(50_000), safe.remaining)
    }

    @Test
    fun `hari terakhir bulan memberi seluruh sisa jatah`() {
        val safe = assertNotNull(compute(listOf(card("Kebutuhan", RoomKind.MENCUKUPI, 1_000_000, 400_000)), on = LocalDate.of(2026, 9, 30)))

        assertEquals(1, safe.daysLeft)
        assertEquals(rupiah(600_000), safe.dailyBudget)
    }

    @Test
    fun `hari pertama bulan membagi ke seluruh hari dalam bulan`() {
        val safe = assertNotNull(compute(listOf(card("Kebutuhan", RoomKind.MENCUKUPI, 3_000_000, 0)), on = LocalDate.of(2026, 9, 1)))

        assertEquals(30, safe.daysLeft)
        assertEquals(rupiah(100_000), safe.dailyBudget)
    }

    @Test
    fun `pembagian dibulatkan ke bawah`() {
        val safe = assertNotNull(compute(listOf(card("Kebutuhan", RoomKind.MENCUKUPI, 1_000_000, 0))))

        // 1.000.000 / 7 = 142.857,14
        assertEquals(rupiah(142_857), safe.dailyBudget)
    }

    @Test
    fun `hanya ruang mencukupi yang dihitung`() {
        val cards = listOf(
            card("Kebutuhan", RoomKind.MENCUKUPI, 700_000, 0),
            card("Memberi", RoomKind.MENUNAIKAN, 5_000_000, 0),
            card("Diri", RoomKind.MENUMBUHKAN, 5_000_000, 0),
        )

        assertEquals(rupiah(100_000), assertNotNull(compute(cards)).dailyBudget)
    }

    @Test
    fun `beberapa ruang mencukupi dijumlahkan`() {
        val cards = listOf(
            card("Makan", RoomKind.MENCUKUPI, 1_400_000, 50_000),
            card("Transport", RoomKind.MENCUKUPI, 700_000, 25_000),
        )
        val safe = assertNotNull(compute(cards, mapOf("Makan" to 50_000, "Transport" to 25_000)))

        assertEquals(rupiah(75_000), safe.spentToday)
        assertEquals(rupiah(300_000), safe.dailyBudget)
        assertEquals(rupiah(225_000), safe.remaining)
    }

    @Test
    fun `ruang yang sudah melewati jatah dihitung nol dan tidak memakan jatah ruang lain`() {
        val cards = listOf(
            card("Makan", RoomKind.MENCUKUPI, 700_000, 900_000),
            card("Transport", RoomKind.MENCUKUPI, 700_000, 0),
        )

        assertEquals(rupiah(100_000), assertNotNull(compute(cards)).dailyBudget)
    }

    @Test
    fun `jatah hari ini terlampaui tampil nol bukan negatif`() {
        val cards = listOf(card("Kebutuhan", RoomKind.MENCUKUPI, 700_000, 300_000))
        val safe = assertNotNull(compute(cards, mapOf("Kebutuhan" to 120_000)))

        // Sebelum hari ini terpakai 180.000: sisa 520.000 / 7 = 74.285; hari ini terpakai 120.000.
        assertEquals(rupiah(74_285), safe.dailyBudget)
        assertTrue(safe.isOver)
        assertEquals(Money.zero(), safe.remaining)
    }

    @Test
    fun `jatah bulan habis membuat sisa nol tanpa dianggap terlampaui`() {
        val safe = assertNotNull(compute(listOf(card("Kebutuhan", RoomKind.MENCUKUPI, 700_000, 700_000))))

        assertEquals(Money.zero(), safe.dailyBudget)
        assertEquals(Money.zero(), safe.remaining)
        assertFalse(safe.isOver)
    }

    @Test
    fun `tanpa ruang mencukupi berjatah tidak ada hasil`() {
        assertNull(compute(emptyList()))
        assertNull(compute(listOf(card("Kebutuhan", RoomKind.MENCUKUPI, 0, 0))))
        assertNull(compute(listOf(card("Memberi", RoomKind.MENUNAIKAN, 1_000_000, 0))))
    }

    @Test
    fun `denah bulan berjalan menyertakan sisa aman dan bulan lain tidak`() {
        val f = LedgerFixture().standard()
        val denah = runSuspend { DenahLoader(f.store, f.store, f.store).load(java.time.YearMonth.from(f.today), f.today) }
        val lain = runSuspend { DenahLoader(f.store, f.store, f.store).load(java.time.YearMonth.from(f.today).minusMonths(1), f.today) }

        assertNull(lain.safeToSpend)
        // Fixture standar belum punya pemasukan, jadi belum ada jatah yang bisa dibagi.
        assertNull(denah.safeToSpend)
    }
}
