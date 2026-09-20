package com.roziqrizal.rizqflow.domain.allocation

import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Currency
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.money.sum
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AllocationEngineTest {

    private fun rule(room: String, basisPoints: Int) = AllocationRule(RoomId(room), BasisPoints(basisPoints))

    private fun percentRules(vararg pairs: Pair<String, Int>) = pairs.map { (room, p) -> AllocationRule(RoomId(room), BasisPoints.percent(p)) }

    private fun AllocationResult.amounts(): List<Long> = shares.map { it.amount.minor }

    @Test
    fun `contoh keputusan pembulatan sisa terbesar`() {
        // Persis: 123.456,7 / 617.283,5 / 493.826,8. Dibulatkan biasa jumlahnya lebih Rp 1.
        val hasil = AllocationEngine.allocate(
            Money.rupiah(1_234_567),
            percentRules("memberi" to 10, "diri" to 50, "keluarga" to 40),
        )

        assertEquals(listOf(123_457L, 617_283L, 493_827L), hasil.amounts())
        assertEquals(Money.zero(), hasil.unallocated)
    }

    @Test
    fun `pembagian yang habis dibagi tidak memakai sisa`() {
        val hasil = AllocationEngine.allocate(
            Money.rupiah(8_500_000),
            percentRules("memberi" to 10, "diri" to 30, "keluarga" to 60),
        )

        assertEquals(listOf(850_000L, 2_550_000L, 5_100_000L), hasil.amounts())
        assertEquals(Money.zero(), hasil.unallocated)
    }

    @Test
    fun `hasil mengikuti urutan aturan dan membawa pengenal ruang`() {
        val hasil = AllocationEngine.allocate(Money.rupiah(1_000), listOf(rule("b", 2_000), rule("a", 8_000)))

        assertEquals(listOf(RoomId("b"), RoomId("a")), hasil.shares.map { it.roomId })
        assertEquals(listOf(200L, 800L), hasil.amounts())
    }

    @Test
    fun `sisa satu rupiah jatuh ke pecahan terbesar`() {
        // Rp 100 dengan 33,33% / 33,33% / 33,34%: lantai 33, 33, 33 (99), sisa 1 ke pecahan 0,34.
        val hasil = AllocationEngine.allocate(
            Money.rupiah(100),
            listOf(rule("a", 3_333), rule("b", 3_333), rule("c", 3_334)),
        )

        assertEquals(listOf(33L, 33L, 34L), hasil.amounts())
    }

    @Test
    fun `pecahan sama diputus oleh urutan prioritas`() {
        val awalDulu = AllocationEngine.allocate(Money.rupiah(1), listOf(rule("a", 5_000), rule("b", 5_000)))
        val urutanTerbalik = AllocationEngine.allocate(Money.rupiah(1), listOf(rule("b", 5_000), rule("a", 5_000)))

        assertEquals(listOf(1L, 0L), awalDulu.amounts())
        assertEquals(RoomId("a"), awalDulu.shares.first().roomId)
        assertEquals(listOf(1L, 0L), urutanTerbalik.amounts())
        assertEquals(RoomId("b"), urutanTerbalik.shares.first().roomId)
    }

    @Test
    fun `dua sisa dibagikan ke dua pecahan terbesar`() {
        // Rp 5 dibagi 3 ruang sama besar (33,33%, 33,33%, 33,34%): tepat 1,6665 / 1,6665 / 1,667.
        val hasil = AllocationEngine.allocate(Money.rupiah(5), listOf(rule("a", 3_333), rule("b", 3_333), rule("c", 3_334)))

        assertEquals(5L, hasil.amounts().sum())
        assertEquals(listOf(2L, 1L, 2L), hasil.amounts())
    }

    @Test
    fun `total aturan di bawah 100 persen menyisakan belum dialirkan`() {
        val hasil = AllocationEngine.allocate(Money.rupiah(1_000_000), percentRules("memberi" to 10, "diri" to 30))

        assertEquals(listOf(100_000L, 300_000L), hasil.amounts())
        assertEquals(Money.rupiah(600_000), hasil.unallocated)
    }

    @Test
    fun `sisa pembulatan tidak mengambil bagian yang belum dialirkan`() {
        // 9.999 bp dari Rp 100 = 99,99: yang dialirkan Rp 99, sisa Rp 1 tetap belum dialirkan.
        val hasil = AllocationEngine.allocate(
            Money.rupiah(100),
            listOf(rule("a", 3_333), rule("b", 3_333), rule("c", 3_333)),
        )

        assertEquals(listOf(33L, 33L, 33L), hasil.amounts())
        assertEquals(Money.rupiah(1), hasil.unallocated)
    }

    @Test
    fun `tanpa aturan seluruh nominal belum dialirkan`() {
        val hasil = AllocationEngine.allocate(Money.rupiah(2_000_000), emptyList())

        assertTrue(hasil.shares.isEmpty())
        assertEquals(Money.rupiah(2_000_000), hasil.unallocated)
    }

    @Test
    fun `nominal nol menghasilkan bagian nol`() {
        val hasil = AllocationEngine.allocate(Money.zero(), percentRules("a" to 60, "b" to 40))

        assertEquals(listOf(0L, 0L), hasil.amounts())
        assertEquals(Money.zero(), hasil.unallocated)
    }

    @Test
    fun `satu rupiah tetap habis terbagi`() {
        val hasil = AllocationEngine.allocate(Money.rupiah(1), percentRules("a" to 10, "b" to 30, "c" to 60))

        assertEquals(1L, hasil.amounts().sum())
        assertEquals(listOf(0L, 0L, 1L), hasil.amounts())
    }

    @Test
    fun `ruang dengan nol persen tidak pernah menerima sisa`() {
        val hasil = AllocationEngine.allocate(Money.rupiah(7), listOf(rule("a", 5_000), rule("nol", 0), rule("b", 5_000)))

        assertEquals(0L, hasil.shares[1].amount.minor)
        assertEquals(7L, hasil.amounts().sum())
    }

    @Test
    fun `nominal sangat besar tidak meluap`() {
        val besar = Money.rupiah(900_000_000_000_000)
        val hasil = AllocationEngine.allocate(besar, listOf(rule("a", 3_333), rule("b", 3_333), rule("c", 3_334)))

        assertEquals(besar.minor, hasil.amounts().sum())
    }

    @Test
    fun `mata uang masukan dipertahankan`() {
        val hasil = AllocationEngine.allocate(Money(1_001, Currency.USD), percentRules("a" to 50, "b" to 50))

        assertTrue(hasil.shares.all { it.amount.currency == Currency.USD })
        assertEquals(Currency.USD, hasil.unallocated.currency)
        assertEquals(1_001L, hasil.amounts().sum())
    }

    @Test
    fun `nominal negatif ditolak`() {
        assertFailsWith<IllegalArgumentException> {
            AllocationEngine.allocate(Money.rupiah(-1), percentRules("a" to 100))
        }
    }

    @Test
    fun `ruang ganda dalam aturan ditolak`() {
        assertFailsWith<IllegalArgumentException> {
            AllocationEngine.allocate(Money.rupiah(100), percentRules("a" to 50, "a" to 50))
        }
    }

    @Test
    fun `total aturan lebih dari 100 persen ditolak`() {
        assertFailsWith<IllegalArgumentException> {
            AllocationEngine.allocate(Money.rupiah(100), percentRules("a" to 60, "b" to 50))
        }
    }

    @Test
    fun `basis point di luar jangkauan ditolak`() {
        assertFailsWith<IllegalArgumentException> { BasisPoints(-1) }
        assertFailsWith<IllegalArgumentException> { BasisPoints(10_001) }
        assertEquals(1_000, BasisPoints.percent(10).value)
    }

    @Test
    fun `persentase pecahan tetap eksak`() {
        // 2,5% dari Rp 1.000.000 tepat Rp 25.000.
        val hasil = AllocationEngine.allocate(Money.rupiah(1_000_000), listOf(rule("zakat", 250), rule("sisa", 9_750)))

        assertEquals(listOf(25_000L, 975_000L), hasil.amounts())
    }

    @Test
    fun `sifat umum pada seribu kasus acak`() {
        val random = Random(20260921)
        val full = BigInteger.valueOf(BasisPoints.FULL.toLong())

        repeat(2_000) {
            val count = random.nextInt(1, 7)
            var budget = random.nextInt(0, BasisPoints.FULL + 1)
            val rules = (0 until count).map { i ->
                val share = if (i == count - 1) budget else random.nextInt(0, budget + 1)
                budget -= share
                rule("r$i", share)
            }
            val amount = Money.rupiah(random.nextLong(0, 1_000_000_000_000_000L))

            val hasil = AllocationEngine.allocate(amount, rules)

            // 1. Bagian ditambah belum dialirkan selalu persis nominal.
            assertEquals(amount, hasil.shares.map { it.amount }.sum() + hasil.unallocated)
            // 2. Tiap bagian hanya boleh lantai atau lantai + 1 dari nilai persisnya.
            rules.forEachIndexed { i, r ->
                val floor = (BigInteger.valueOf(amount.minor) * BigInteger.valueOf(r.share.value.toLong()) / full).toLong()
                val diff = hasil.shares[i].amount.minor - floor
                assertTrue(diff == 0L || diff == 1L, "bagian ${r.roomId.value} menyimpang $diff dari lantai")
            }
            // 3. Yang belum dialirkan tidak pernah negatif.
            assertTrue(!hasil.unallocated.isNegative)
        }
    }
}
