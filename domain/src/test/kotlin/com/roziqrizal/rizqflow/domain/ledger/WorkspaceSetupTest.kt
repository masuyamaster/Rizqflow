package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceSetupTest {

    private val dompet = FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(500_000))

    private fun setUp(f: LedgerFixture, template: RoomTemplate = RoomTemplate.TIGA_HAK, account: FirstAccount = dompet, shares: List<BasisPoints> = emptyList()) =
        runSuspend { f.setup.setUp(template, account, shares) }

    @Test
    fun `pola Tiga hak membuat tiga ruang berurutan dengan tipe dan ikonnya`() {
        val f = LedgerFixture()
        assertIs<LedgerResult.Success<Unit>>(setUp(f))

        val rooms = f.store.roomRows.values.toList()
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), rooms.map { it.name })
        assertEquals(listOf(RoomKind.MENUNAIKAN, RoomKind.MENUMBUHKAN, RoomKind.MENCUKUPI), rooms.map { it.kind })
        assertEquals(listOf(0, 1, 2), rooms.map { it.sortOrder })
        assertEquals(listOf(1, 2, 3), rooms.map { it.colorSlot })
        assertEquals(listOf("heart", "sprout", "home"), rooms.map { it.iconKey })
    }

    @Test
    fun `ruang Memberi memakai mode persentase donasi sebagai bawaan dan ruang lain tanpa mode`() {
        val f = LedgerFixture().standard()
        assertEquals("percentage", f.room("Memberi").givingMode)
        assertNull(f.room("Diri").givingMode)
        assertNull(f.room("Keluarga").givingMode)
    }

    @Test
    fun `pembagian bawaan 10 30 60 disimpan sebagai aturan menurut urutan ruang`() {
        val f = LedgerFixture().standard()
        val rules = runSuspend { f.store.rules() }
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), rules.map { f.store.roomRows.getValue(it.roomId).name })
        assertEquals(listOf(1_000, 3_000, 6_000), rules.map { it.share.value })
    }

    @Test
    fun `pembagian pilihan pengguna dipakai`() {
        val f = LedgerFixture()
        setUp(f, shares = listOf(BasisPoints.percent(20), BasisPoints.percent(20), BasisPoints.percent(60)))
        assertEquals(listOf(2_000, 2_000, 6_000), runSuspend { f.store.rules() }.map { it.share.value })
    }

    @Test
    fun `pembagian yang tidak berjumlah 100 persen ditolak dan tidak menyimpan apa pun`() {
        val f = LedgerFixture()
        val result = setUp(f, shares = listOf(BasisPoints.percent(10), BasisPoints.percent(30), BasisPoints.percent(50)))

        assertEquals(LedgerResult.Failure(LedgerError.RULES_NOT_100_PERCENT), result)
        assertTrue(f.store.roomRows.isEmpty() && f.store.accountRows.isEmpty())
    }

    @Test
    fun `jumlah pembagian yang tidak sama dengan jumlah ruang ditolak`() {
        val f = LedgerFixture()
        assertEquals(LedgerResult.Failure(LedgerError.RULES_INVALID), setUp(f, shares = listOf(BasisPoints.percent(100))))
    }

    @Test
    fun `kategori awal tiap ruang mengikuti pola dan berurutan tanpa nomor ganda`() {
        val f = LedgerFixture().standard()
        val keluarga = f.store.categoryRows.values.filter { it.roomId == f.room("Keluarga").id }
        assertEquals(
            listOf("Belanja bulanan", "Listrik dan air", "Sekolah", "Lain-lain", RoomTemplates.UNTRACKED),
            keluarga.sortedBy { it.sortOrder }.map { it.name },
        )
        assertEquals(keluarga.size, keluarga.map { it.sortOrder }.toSet().size)
    }

    @Test
    fun `kategori sistem Tak terlacak ada di setiap ruang dan Zakat mal hanya di Memberi`() {
        val f = LedgerFixture().standard()
        f.store.roomRows.values.forEach { room ->
            val system = f.store.categoryRows.values.filter { it.roomId == room.id && it.isSystem }.map { it.name }
            val expected = if (room.name == "Memberi") setOf(RoomTemplates.ZAKAT, RoomTemplates.UNTRACKED) else setOf(RoomTemplates.UNTRACKED)
            assertEquals(expected, system.toSet(), room.name)
        }
        assertTrue(f.store.categoryRows.values.filter { !it.isSystem }.none { it.name == RoomTemplates.UNTRACKED || it.name == RoomTemplates.ZAKAT })
    }

    @Test
    fun `akun pertama dibuat dengan saldo awal dan bukan sebagai pemasukan`() {
        val f = LedgerFixture().standard()

        assertEquals("Dompet", f.account.name)
        assertEquals(Money.rupiah(500_000), f.account.openingBalance)
        assertEquals(Money.rupiah(500_000), f.balance())
        assertTrue(f.store.transactionRows.isEmpty(), "saldo awal bukan rezeki")
    }

    @Test
    fun `nama akun dirapikan`() {
        val f = LedgerFixture()
        setUp(f, account = FirstAccount("  Dompet  ", AccountKind.CASH, Money.zero()))
        assertEquals("Dompet", f.account.name)
    }

    @Test
    fun `saldo awal nol diperbolehkan`() {
        val f = LedgerFixture()
        assertIs<LedgerResult.Success<Unit>>(setUp(f, account = FirstAccount("Dompet", AccountKind.CASH, Money.zero())))
    }

    @Test
    fun `nama akun kosong atau terlalu panjang dan saldo negatif ditolak`() {
        listOf(
            FirstAccount("   ", AccountKind.CASH, Money.zero()),
            FirstAccount("x".repeat(31), AccountKind.CASH, Money.zero()),
            FirstAccount("Dompet", AccountKind.CASH, Money(-1)),
        ).forEach {
            val f = LedgerFixture()
            assertEquals(LedgerResult.Failure(LedgerError.INVALID_NAME), setUp(f, account = it), it.toString())
            assertTrue(f.store.accountRows.isEmpty())
        }
    }

    @Test
    fun `pola Kosong hanya membuat akun tanpa ruang dan tanpa aturan`() {
        val f = LedgerFixture()
        assertIs<LedgerResult.Success<Unit>>(setUp(f, RoomTemplate.KOSONG))

        assertTrue(f.store.roomRows.isEmpty() && f.store.categoryRows.isEmpty())
        assertTrue(runSuspend { f.store.rules() }.isEmpty())
        assertEquals(1, f.store.accountRows.size)
    }

    @Test
    fun `pengaturan awal hanya berjalan sekali sehingga ketukan ganda tidak menggandakan ruang`() {
        val f = LedgerFixture()
        setUp(f)

        assertEquals(LedgerResult.Failure(LedgerError.WORKSPACE_NOT_EMPTY), setUp(f))
        assertEquals(3, f.store.roomRows.size)
        assertEquals(1, f.store.accountRows.size)
    }

    @Test
    fun `semua pengenal yang dibuat unik`() {
        val f = LedgerFixture().standard()
        val ids = f.store.roomRows.keys.map { it.value } + f.store.categoryRows.keys.map { it.value } + f.store.accountRows.keys.map { it.value }
        assertEquals(ids.size, ids.toSet().size)
    }
}
