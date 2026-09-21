package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DenahTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private val sept = YearMonth.of(2026, 9)

    private fun status(kind: RoomKind, allocated: Long, spent: Long, monthOver: Boolean = false) =
        RoomStatusRules.statusOf(kind, rupiah(allocated), rupiah(spent), monthOver)

    // ------------------------------------------------------------------ aturan status per tipe

    @Test
    fun `tanpa jatah statusnya menunggu untuk semua tipe`() {
        RoomKind.entries.forEach { kind ->
            assertEquals(RoomStatus.MENUNGGU, status(kind, 0, 0), "$kind")
            assertEquals(RoomStatus.MENUNGGU, status(kind, 0, 50_000), "$kind dengan pengeluaran tanpa jatah")
        }
    }

    @Test
    fun `menunaikan dan menumbuhkan terpenuhi tepat saat terpakai mencapai jatah`() {
        listOf(RoomKind.MENUNAIKAN, RoomKind.MENUMBUHKAN).forEach { kind ->
            assertEquals(RoomStatus.BERJALAN, status(kind, 1_000_000, 0), "$kind belum ada")
            assertEquals(RoomStatus.BERJALAN, status(kind, 1_000_000, 999_999), "$kind kurang sedikit")
            assertEquals(RoomStatus.TERPENUHI, status(kind, 1_000_000, 1_000_000), "$kind tepat")
            assertEquals(RoomStatus.TERPENUHI, status(kind, 1_000_000, 1_500_000), "$kind lebih")
        }
    }

    @Test
    fun `menunaikan dan menumbuhkan yang belum tercapai di bulan lalu netral bukan peringatan`() {
        listOf(RoomKind.MENUNAIKAN, RoomKind.MENUMBUHKAN).forEach { kind ->
            assertEquals(RoomStatus.BELUM_TERCAPAI, status(kind, 1_000_000, 400_000, monthOver = true), "$kind")
            assertEquals(RoomStatus.BELUM_TERCAPAI, status(kind, 1_000_000, 0, monthOver = true), "$kind tanpa pemakaian")
            assertEquals(RoomStatus.TERPENUHI, status(kind, 1_000_000, 1_000_000, monthOver = true), "$kind tepat")
        }
    }

    @Test
    fun `menunaikan dan menumbuhkan tidak pernah perlu perhatian karena persentase`() {
        assertEquals(RoomStatus.BERJALAN, status(RoomKind.MENUNAIKAN, 1_000_000, 900_000))
        assertEquals(RoomStatus.BERJALAN, status(RoomKind.MENUMBUHKAN, 1_000_000, 850_000))
    }

    @Test
    fun `mencukupi berjalan di bawah 85 persen dan perlu perhatian tepat di ambang`() {
        assertEquals(RoomStatus.BERJALAN, status(RoomKind.MENCUKUPI, 1_000_000, 0))
        assertEquals(RoomStatus.BERJALAN, status(RoomKind.MENCUKUPI, 1_000_000, 849_999))
        assertEquals(RoomStatus.PERLU_PERHATIAN, status(RoomKind.MENCUKUPI, 1_000_000, 850_000))
        assertEquals(RoomStatus.PERLU_PERHATIAN, status(RoomKind.MENCUKUPI, 1_000_000, 1_000_000))
        assertEquals(RoomStatus.PERLU_PERHATIAN, status(RoomKind.MENCUKUPI, 1_000_000, 1_200_000))
    }

    @Test
    fun `mencukupi pada bulan yang sudah berakhir terpenuhi bila tidak melebihi jatah`() {
        assertEquals(RoomStatus.TERPENUHI, status(RoomKind.MENCUKUPI, 1_000_000, 950_000, monthOver = true))
        assertEquals(RoomStatus.TERPENUHI, status(RoomKind.MENCUKUPI, 1_000_000, 1_000_000, monthOver = true))
        assertEquals(RoomStatus.PERLU_PERHATIAN, status(RoomKind.MENCUKUPI, 1_000_000, 1_000_001, monthOver = true))
    }

    @Test
    fun `ambang 85 persen tepat pada jatah yang tidak habis dibagi`() {
        // 85% dari 333 adalah 283,05: 283 masih di bawah, 284 sudah di atas ambang.
        assertEquals(RoomStatus.BERJALAN, status(RoomKind.MENCUKUPI, 333, 283))
        assertEquals(RoomStatus.PERLU_PERHATIAN, status(RoomKind.MENCUKUPI, 333, 284))
    }

    @Test
    fun `persentase terpakai dibulatkan ke bawah dan boleh lebih dari seratus`() {
        assertNull(RoomStatusRules.progressBp(rupiah(0), rupiah(10)))
        assertEquals(0, RoomStatusRules.progressBp(rupiah(1_000_000), rupiah(0)))
        assertEquals(8_500, RoomStatusRules.progressBp(rupiah(3_000_000), rupiah(2_550_000)))
        assertEquals(3_333, RoomStatusRules.progressBp(rupiah(3), rupiah(1)))
        assertEquals(15_000, RoomStatusRules.progressBp(rupiah(1_000_000), rupiah(1_500_000)))
    }

    @Test
    fun `nominal sangat besar tidak meluap`() {
        val besar = Long.MAX_VALUE / 2
        assertEquals(10_000, RoomStatusRules.progressBp(rupiah(besar), rupiah(besar)))
    }

    // ------------------------------------------------------------------ pemuat Denah

    private fun loader(f: LedgerFixture) = DenahLoader(f.store, f.store)

    private fun load(f: LedgerFixture, month: YearMonth = sept, today: LocalDate = f.today) = runSuspend { loader(f).load(month, today) }

    private fun expense(f: LedgerFixture, room: String, category: String, amount: Long, day: Int = 10, month: Int = 9) =
        runSuspend { f.ledger.recordExpense(NewExpense(rupiah(amount), f.account.id, f.room(room).id, f.category(room, category).id, LocalDate.of(2026, month, day))) }

    @Test
    fun `bulan tanpa pemasukan menampilkan semua ruang menunggu dan tanpa butir perhatian`() {
        val f = LedgerFixture().standard()

        val denah = load(f)

        assertEquals(listOf("Memberi", "Diri", "Keluarga"), denah.cards.map { it.room.name })
        assertTrue(denah.cards.all { it.status == RoomStatus.MENUNGGU && it.progressBp == null })
        assertEquals(rupiah(0), denah.income)
        assertEquals(rupiah(0), denah.unallocated)
        assertTrue(denah.attention.isEmpty())
        assertTrue(!denah.hasIncome)
    }

    @Test
    fun `pemasukan dialirkan menurut aturan dan kartu memuat jatah dan terpakai`() {
        val f = LedgerFixture().standard()
        f.income(8_500_000)
        expense(f, "Keluarga", "Belanja bulanan", 1_000_000)

        val denah = load(f)

        assertEquals(rupiah(8_500_000), denah.income)
        val keluarga = denah.cards.first { it.room.name == "Keluarga" }
        assertEquals(rupiah(5_100_000), keluarga.allocated)
        assertEquals(rupiah(1_000_000), keluarga.spent)
        assertEquals(rupiah(4_100_000), keluarga.remaining)
        assertEquals(1_960, keluarga.progressBp)
        assertEquals(RoomStatus.BERJALAN, keluarga.status)
        assertEquals(rupiah(0), denah.unallocated)
    }

    @Test
    fun `keluarga hampir lewat jatah muncul di perlu perhatian dan lewat jatah lebih dulu`() {
        val f = LedgerFixture().standard()
        f.income(10_000_000)
        expense(f, "Keluarga", "Belanja bulanan", 5_100_000)

        val hampir = load(f)
        val near = hampir.attention.single()
        assertIs<AttentionItem.RoomNearLimit>(near)
        assertEquals("Keluarga", near.room.name)
        assertEquals(RoomStatus.PERLU_PERHATIAN, hampir.cards.first { it.room.name == "Keluarga" }.status)

        expense(f, "Keluarga", "Listrik dan air", 1_500_000)
        val lewat = load(f)
        val over = lewat.attention.single()
        assertIs<AttentionItem.RoomOverLimit>(over)
        assertEquals(rupiah(6_600_000), over.spent)
    }

    @Test
    fun `memberi yang melebihi jatah bukan peringatan`() {
        val f = LedgerFixture().standard()
        f.income(10_000_000)
        expense(f, "Memberi", "Sedekah", 1_500_000)

        val denah = load(f)

        assertEquals(RoomStatus.TERPENUHI, denah.cards.first { it.room.name == "Memberi" }.status)
        assertTrue(denah.attention.isEmpty())
    }

    @Test
    fun `rezeki yang belum dialirkan muncul sebagai butir dan cocok dengan sisa`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000)
        // Aturan disimpan 90% saja lewat pengurangan bagian Keluarga, jadi 10% sisa belum dialirkan.
        val keluarga = f.room("Keluarga")
        runSuspend { f.store.replaceRules(f.store.rules().map { if (it.roomId == keluarga.id) it.copy(share = com.roziqrizal.rizqflow.domain.allocation.BasisPoints.percent(50)) else it }) }
        f.income(2_000_000)

        val denah = load(f)

        assertEquals(rupiah(3_000_000), denah.income)
        assertEquals(rupiah(200_000), denah.unallocated)
        assertIs<AttentionItem.Unallocated>(denah.attention.single())
    }

    @Test
    fun `alokasi ruang terarsip tetap terhitung sudah dialirkan`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000)
        runSuspend { f.rules.archiveRoom(f.room("Diri").id) }

        val denah = load(f)

        assertEquals(listOf("Memberi", "Keluarga"), denah.cards.map { it.room.name })
        assertEquals(rupiah(0), denah.unallocated)
    }

    @Test
    fun `perlu perhatian paling banyak tiga butir dengan urutan prioritas`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000)
        val a = runSuspend { f.rules.addRoom(NewRoom("Kontrakan", RoomKind.MENCUKUPI, "home", 4)) }
        val b = runSuspend { f.rules.addRoom(NewRoom("Sekolah", RoomKind.MENCUKUPI, "star", 4)) }
        assertIs<LedgerResult.Success<*>>(a)
        assertIs<LedgerResult.Success<*>>(b)
        // Bagi ulang: Memberi 5, Diri 5, Keluarga 40, Kontrakan 30, Sekolah 20; lalu pemasukan baru.
        val rooms = runSuspend { f.store.activeRooms() }.associateBy { it.name }
        val rules = listOf("Memberi" to 5, "Diri" to 5, "Keluarga" to 40, "Kontrakan" to 30, "Sekolah" to 20).map { (n, p) ->
            com.roziqrizal.rizqflow.domain.allocation.AllocationRule(rooms.getValue(n).id, com.roziqrizal.rizqflow.domain.allocation.BasisPoints.percent(p))
        }
        assertIs<LedgerResult.Success<*>>(runSuspend { f.rules.changeRules(rules) })
        f.income(10_000_000)
        // Tiga ruang Mencukupi masing-masing terpakai melewati jatah Rp 1.
        listOf("Keluarga", "Kontrakan", "Sekolah").forEach { name ->
            val category = f.category(name, if (name == "Keluarga") "Belanja bulanan" else "Lain-lain")
            val allocated = runSuspend { f.store.roomTotals(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)) }.allocated.getValue(rooms.getValue(name).id)
            runSuspend { f.ledger.recordExpense(NewExpense(allocated + rupiah(1), f.account.id, rooms.getValue(name).id, category.id, LocalDate.of(2026, 9, 10))) }
        }

        val denah = load(f)

        assertEquals(3, denah.attention.size)
        assertTrue(denah.attention.all { it is AttentionItem.RoomOverLimit })
    }

    @Test
    fun `bulan yang sudah lewat tidak punya butir perhatian dan mencukupi dinilai akhir bulan`() {
        val f = LedgerFixture().standard()
        runSuspend { f.ledger.recordIncome(NewIncome(rupiah(1_000_000), f.account.id, "Gaji", LocalDate.of(2026, 8, 1))) }
        runSuspend { f.ledger.recordExpense(NewExpense(rupiah(590_000), f.account.id, f.room("Keluarga").id, f.category("Keluarga", "Belanja bulanan").id, LocalDate.of(2026, 8, 20))) }

        val agustus = load(f, YearMonth.of(2026, 8))

        val keluarga = agustus.cards.first { it.room.name == "Keluarga" }
        assertEquals(RoomStatus.TERPENUHI, keluarga.status)
        // Memberi dan Diri tidak dipakai sama sekali di bulan itu: netral, bukan peringatan.
        assertEquals(RoomStatus.BELUM_TERCAPAI, agustus.cards.first { it.room.name == "Memberi" }.status)
        assertEquals(RoomStatus.BELUM_TERCAPAI, agustus.cards.first { it.room.name == "Diri" }.status)
        assertTrue(agustus.attention.isEmpty())
        // Bulan berjalan tidak ikut terhitung di bulan lalu.
        assertEquals(rupiah(1_000_000), agustus.income)
        assertEquals(rupiah(0), load(f).income)
    }

    @Test
    fun `transaksi pada batas bulan masuk ke bulan yang benar`() {
        val f = LedgerFixture().standard()
        runSuspend { f.ledger.recordIncome(NewIncome(rupiah(1_000_000), f.account.id, "Gaji", LocalDate.of(2026, 8, 31))) }
        runSuspend { f.ledger.recordIncome(NewIncome(rupiah(2_000_000), f.account.id, "Gaji", LocalDate.of(2026, 9, 1))) }

        assertEquals(rupiah(1_000_000), load(f, YearMonth.of(2026, 8)).income)
        assertEquals(rupiah(2_000_000), load(f, sept).income)
    }

    @Test
    fun `tanpa ruang denah kosong dan rezeki menunggu sebagai belum dialirkan`() {
        val f = LedgerFixture()
        runSuspend { f.setup.setUp(RoomTemplate.KOSONG, FirstAccount("Dompet", com.roziqrizal.rizqflow.domain.model.AccountKind.CASH, rupiah(0))) }
        f.income(750_000)

        val denah = load(f)

        assertTrue(!denah.hasRooms)
        assertEquals(rupiah(750_000), denah.unallocated)
        assertIs<AttentionItem.Unallocated>(denah.attention.single())
    }
}
