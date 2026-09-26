package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.entitlement.Plan
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomManagementTest {

    private fun overview(f: LedgerFixture) = runSuspend { f.rules.overview() }

    @Test
    fun `urungkan arsip mengembalikan urutan dan persentase seperti semula`() {
        val f = LedgerFixture().standard()
        val diri = f.room("Diri")

        val undo = (runSuspend { f.rules.archiveWithUndo(diri.id) } as LedgerResult.Success).value
        assertEquals(listOf("Memberi", "Keluarga"), runSuspend { f.rules.overview() }.active.map { it.room.name })

        assertIs<LedgerResult.Success<*>>(runSuspend { f.rules.undoArchive(undo) })

        val overview = runSuspend { f.rules.overview() }
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), overview.active.map { it.room.name })
        assertEquals(listOf(1_000, 3_000, 6_000), overview.active.map { it.shareBp })
        assertTrue(overview.isBalanced)
    }

    @Test
    fun `urungkan arsip ditolak bila batas ruang atau nama sudah bentrok`() {
        val f = LedgerFixture().standard()
        val undo = (runSuspend { f.rules.archiveWithUndo(f.room("Diri").id) } as LedgerResult.Success).value
        runSuspend { f.rules.addRoom(NewRoom("Diri", RoomKind.MENUMBUHKAN, "sprout", 4)) }

        assertEquals(LedgerResult.Failure(LedgerError.NAME_TAKEN), runSuspend { f.rules.undoArchive(undo) })
        assertEquals(LedgerResult.Failure(LedgerError.ROOM_NOT_FOUND), runSuspend { f.rules.archiveWithUndo(RoomId("hilang")) })
    }

    private fun archive(f: LedgerFixture, name: String) = runSuspend { f.rules.archiveRoom(f.room(name).id) }

    private fun restore(f: LedgerFixture, name: String) = runSuspend { f.rules.restoreRoom(f.room(name).id) }

    private fun move(f: LedgerFixture, name: String, delta: Int) = runSuspend { f.rules.moveRoom(f.room(name).id, delta) }

    private fun names(f: LedgerFixture) = runSuspend { f.store.activeRooms() }.map { it.name }

    private fun addRoom(f: LedgerFixture, name: String = "Orang tua", slot: Int = 4) =
        runSuspend { f.rules.addRoom(NewRoom(name, RoomKind.MENCUKUPI, "home", slot)) }

    // ------------------------------------------------------------------ ikhtisar

    @Test
    fun `ikhtisar memuat ruang aktif berurutan dengan persentasenya dan batas paket`() {
        val f = LedgerFixture().standard()

        val o = overview(f)

        assertEquals(listOf("Memberi" to 1_000, "Diri" to 3_000, "Keluarga" to 6_000), o.active.map { it.room.name to it.shareBp })
        assertEquals(10_000, o.totalBp)
        assertTrue(o.isBalanced)
        assertEquals(5, o.roomLimit)
        assertTrue(o.canAdd)
        assertTrue(o.archived.isEmpty())
    }

    @Test
    fun `ikhtisar paket Pro tanpa batas dan paket gratis penuh tidak bisa menambah`() {
        assertNull(overview(LedgerFixture(plans = setOf(Plan.PRO)).standard()).roomLimit)

        val f = LedgerFixture().standard()
        addRoom(f, "Orang tua", 4)
        addRoom(f, "Tabungan haji", 5)
        assertTrue(!overview(f).canAdd)
    }

    @Test
    fun `ruang baru tanpa aturan tercatat 0 persen dan menandai total belum genap bila lain sudah 100`() {
        val f = LedgerFixture().standard()
        addRoom(f)

        val o = overview(f)

        assertEquals(0, o.active.last().shareBp)
        assertTrue(o.isBalanced, "ruang baru 0%: total tetap 100%")
    }

    @Test
    fun `ikhtisar tanpa ruang dianggap seimbang`() {
        val f = LedgerFixture()
        runSuspend { f.setup.setUp(RoomTemplate.KOSONG, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(0))) }
        assertTrue(overview(f).isBalanced)
        assertTrue(overview(f).active.isEmpty())
    }

    // ------------------------------------------------------------------ arsip dan pulihkan

    @Test
    fun `mengarsipkan ruang menyisihkannya dari daftar aktif dan aturan tetapi menyimpan persentasenya`() {
        val f = LedgerFixture().standard()

        assertIs<LedgerResult.Success<Unit>>(archive(f, "Diri"))

        val o = overview(f)
        assertEquals(listOf("Memberi", "Keluarga"), o.active.map { it.room.name })
        assertEquals(listOf("Diri"), o.archived.map { it.name })
        assertEquals(7_000, o.totalBp, "total tidak lagi 100%: perlu diatur ulang")
        assertTrue(!o.isBalanced)
        assertTrue(f.store.ruleRows.any { it.roomId == f.room("Diri").id }, "persentase lama masih tersimpan")
    }

    @Test
    fun `pemasukan setelah mengarsipkan tidak dialirkan ke ruang terarsip dan sisanya belum dialirkan`() {
        val f = LedgerFixture().standard()
        archive(f, "Diri")

        val receipt = (f.income(1_000_000) as LedgerResult.Success).value

        assertEquals(Money.rupiah(300_000), receipt.allocation.unallocated)
        assertEquals(2, runSuspend { f.store.entriesOf(receipt.transaction.id) }.size)
    }

    @Test
    fun `mengarsipkan dua kali atau ruang yang tidak ada`() {
        val f = LedgerFixture().standard()
        archive(f, "Diri")
        assertIs<LedgerResult.Success<Unit>>(archive(f, "Diri"))
        assertEquals(LedgerResult.Failure(LedgerError.ROOM_NOT_FOUND), runSuspend { f.rules.archiveRoom(RoomId("hilang")) })
    }

    @Test
    fun `memulihkan ruang menaruhnya di belakang dengan persentase 0 dan tidak mengubah aturan lain`() {
        val f = LedgerFixture().standard()
        archive(f, "Diri")
        // Pengguna mengatur ulang aturan menjadi 100% tanpa Diri.
        runSuspend { f.rules.changeRules(listOf(rule(f, "Memberi", 20), rule(f, "Keluarga", 80))) }

        assertIs<LedgerResult.Success<Unit>>(restore(f, "Diri"))

        val o = overview(f)
        assertEquals(listOf("Memberi", "Keluarga", "Diri"), o.active.map { it.room.name })
        assertEquals(listOf(2_000, 8_000, 0), o.active.map { it.shareBp }, "Diri kembali 0%, bukan 30% lamanya")
        assertTrue(o.isBalanced)
        assertTrue(o.archived.isEmpty())
    }

    @Test
    fun `memulihkan ruang yang tidak terarsip tidak mengubah apa pun`() {
        val f = LedgerFixture().standard()
        val sebelum = names(f)
        assertIs<LedgerResult.Success<Unit>>(restore(f, "Diri"))
        assertEquals(sebelum, names(f))
    }

    @Test
    fun `memulihkan ditolak bila batas ruang paket gratis tercapai`() {
        val f = LedgerFixture().standard()
        archive(f, "Diri")
        addRoom(f, "Orang tua", 4)
        addRoom(f, "Tabungan haji", 5)
        addRoom(f, "Liburan", 6)
        assertEquals(5, overview(f).active.size)

        assertEquals(LedgerResult.Failure(LedgerError.ROOM_LIMIT_REACHED), restore(f, "Diri"))
    }

    @Test
    fun `memulihkan ditolak bila ada ruang aktif bernama sama`() {
        val f = LedgerFixture().standard()
        archive(f, "Diri")
        addRoom(f, "diri", 4)
        assertEquals(LedgerResult.Failure(LedgerError.NAME_TAKEN), runSuspend { f.rules.restoreRoom(f.store.roomRows.values.first { it.name == "Diri" }.id) })
    }

    @Test
    fun `mengarsipkan ruang membebaskan tempat dalam batas paket`() {
        val f = LedgerFixture().standard()
        addRoom(f, "Orang tua", 4)
        addRoom(f, "Tabungan haji", 5)
        archive(f, "Diri")
        assertIs<LedgerResult.Success<RoomId>>(addRoom(f, "Liburan", 6))
    }

    @Test
    fun `pengeluaran lama di ruang terarsip tetap terbaca dan saldo tidak berubah`() {
        val f = LedgerFixture().standard()
        val tx = (runSuspend {
            f.ledger.recordExpense(NewExpense(Money.rupiah(5_000), f.account.id, f.room("Keluarga").id, f.category("Keluarga", "Sekolah").id, f.today))
        } as LedgerResult.Success).value
        archive(f, "Keluarga")

        assertNotNull(runSuspend { f.store.find(tx.id) })
        assertEquals(Money.rupiah(495_000), f.balance())
    }

    // ------------------------------------------------------------------ urutan

    @Test
    fun `menggeser ruang turun dan naik menukar urutan dan prioritas`() {
        val f = LedgerFixture().standard()

        move(f, "Memberi", +1)
        assertEquals(listOf("Diri", "Memberi", "Keluarga"), names(f))

        move(f, "Keluarga", -1)
        assertEquals(listOf("Diri", "Keluarga", "Memberi"), names(f))
    }

    @Test
    fun `aturan mengikuti urutan ruang yang baru`() {
        val f = LedgerFixture().standard()
        move(f, "Keluarga", -1)
        move(f, "Keluarga", -1)

        val rules = runSuspend { f.store.rules() }.map { f.store.roomRows.getValue(it.roomId).name to it.share.value }

        assertEquals(listOf("Keluarga" to 6_000, "Memberi" to 1_000, "Diri" to 3_000), rules)
    }

    @Test
    fun `urutan baru menjadi pemutus seri pembulatan`() {
        val f = LedgerFixture().standard()
        // Rp 5 dengan 10/30/60: Memberi dan Diri sama-sama berpecahan 0,5; yang lebih awal mendapat sisa.
        val sebelum = (f.income(5) as LedgerResult.Success).value
        assertEquals(listOf(1L, 1L, 3L), runSuspend { f.store.entriesOf(sebelum.transaction.id) }.map { it.amount.minor })

        move(f, "Diri", -1)
        val sesudah = (f.income(5) as LedgerResult.Success).value

        assertEquals(listOf("Diri", "Memberi", "Keluarga"), runSuspend { f.store.entriesOf(sesudah.transaction.id) }.map { f.store.roomRows.getValue(it.roomId).name })
        // Diri kini lebih awal: ia yang menerima sisa rupiah, bukan Memberi.
        assertEquals(listOf(2L, 0L, 3L), runSuspend { f.store.entriesOf(sesudah.transaction.id) }.map { it.amount.minor })
    }

    @Test
    fun `menggeser di ujung atau nol langkah tidak mengubah apa pun`() {
        val f = LedgerFixture().standard()
        assertIs<LedgerResult.Success<Unit>>(move(f, "Memberi", -1))
        assertIs<LedgerResult.Success<Unit>>(move(f, "Keluarga", +1))
        assertIs<LedgerResult.Success<Unit>>(move(f, "Diri", 0))
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), names(f))
    }

    @Test
    fun `menggeser ruang yang tidak aktif ditolak`() {
        val f = LedgerFixture().standard()
        archive(f, "Diri")
        assertEquals(LedgerResult.Failure(LedgerError.ROOM_NOT_FOUND), runSuspend { f.rules.moveRoom(f.room("Diri").id, +1) })
    }

    // ------------------------------------------------------------------ pola dan urungkan aturan

    @Test
    fun `memakai pola Tiga hak pada akun tanpa ruang`() {
        val f = LedgerFixture()
        runSuspend { f.setup.setUp(RoomTemplate.KOSONG, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(0))) }

        assertIs<LedgerResult.Success<Unit>>(runSuspend { f.rules.applyTemplate(RoomTemplate.TIGA_HAK) })

        val o = overview(f)
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), o.active.map { it.room.name })
        assertEquals(listOf(1_000, 3_000, 6_000), o.active.map { it.shareBp })
        assertTrue(f.store.categoryRows.values.any { it.name == RoomTemplates.UNTRACKED })
    }

    @Test
    fun `pola hanya bisa dipakai bila belum ada ruang aktif`() {
        val f = LedgerFixture().standard()
        assertEquals(LedgerResult.Failure(LedgerError.WORKSPACE_NOT_EMPTY), runSuspend { f.rules.applyTemplate(RoomTemplate.TIGA_HAK) })
        assertEquals(3, f.store.roomRows.size)
    }

    @Test
    fun `pola dipakai setelah semua ruang diarsipkan dengan urutan di belakang yang lama`() {
        val f = LedgerFixture().standard()
        listOf("Memberi", "Diri", "Keluarga").forEach { archive(f, it) }

        assertIs<LedgerResult.Success<Unit>>(runSuspend { f.rules.applyTemplate(RoomTemplate.TIGA_HAK) })

        val aktif = runSuspend { f.store.activeRooms() }
        assertEquals(3, aktif.size)
        assertTrue(aktif.all { it.sortOrder > 2 })
    }

    @Test
    fun `pola Kosong tidak menambah apa pun`() {
        val f = LedgerFixture()
        runSuspend { f.setup.setUp(RoomTemplate.KOSONG, FirstAccount("Dompet", AccountKind.CASH, Money.rupiah(0))) }
        assertIs<LedgerResult.Success<Unit>>(runSuspend { f.rules.applyTemplate(RoomTemplate.KOSONG) })
        assertTrue(f.store.roomRows.isEmpty())
    }

    @Test
    fun `mengembalikan aturan tidak memeriksa total sehingga Urungkan selalu berhasil`() {
        val f = LedgerFixture().standard()
        archive(f, "Diri") // total 70%
        val sebelum = runSuspend { f.store.rules() }
        runSuspend { f.rules.changeRules(listOf(rule(f, "Memberi", 40), rule(f, "Keluarga", 60))) }

        runSuspend { f.rules.restoreRules(sebelum) }

        assertEquals(sebelum, runSuspend { f.store.rules() })
    }

    @Test
    fun `ruang baru mendapat kategori Lain-lain dan kategori sistem Tak terlacak`() {
        val f = LedgerFixture().standard()
        val id = (addRoom(f) as LedgerResult.Success).value

        val kategori = runSuspend { f.store.categories(id) }

        assertEquals(listOf("Lain-lain", RoomTemplates.UNTRACKED), kategori.map { it.name })
        assertTrue(kategori.last().isSystem)
    }

    @Test
    fun `ruang Menunaikan baru juga mendapat kategori sistem Zakat mal`() {
        val f = LedgerFixture().standard()
        val id = (runSuspend { f.rules.addRoom(NewRoom("Sedekah kantor", RoomKind.MENUNAIKAN, "heart", 4)) } as LedgerResult.Success).value

        val kategori = runSuspend { f.store.categories(id) }

        assertEquals(listOf("Lain-lain", RoomTemplates.ZAKAT, RoomTemplates.UNTRACKED), kategori.map { it.name })
        assertTrue(kategori[1].isSystem && kategori[2].isSystem)
    }

    private fun rule(f: LedgerFixture, room: String, percent: Int) = AllocationRule(f.room(room).id, BasisPoints.percent(percent))

    // ------------------------------------------------------------------ draf aturan (S12)

    private fun draft(f: LedgerFixture) = RuleDraft.from(runSuspend { f.store.activeRooms() }, runSuspend { f.store.rules() })

    @Test
    fun `draf dari ruang aktif memuat persentase dan menganggap ruang tanpa aturan 0`() {
        val f = LedgerFixture().standard()
        addRoom(f)

        val d = draft(f)

        assertEquals(listOf(1_000, 3_000, 6_000, 0), d.shares)
        assertTrue(d.isBalanced)
        assertEquals(0, d.remainingBp)
    }

    @Test
    fun `semua ruang bebas diubah dan total boleh sementara bukan 100`() {
        val d = RuleDraft(listOf(RoomId("a"), RoomId("b"), RoomId("c")), listOf(1_000, 3_000, 6_000))

        val naik = d.step(0, 5)

        assertEquals(listOf(1_500, 3_000, 6_000), naik.shares)
        assertEquals(10_500, naik.totalBp)
        assertTrue(!naik.isBalanced)
        assertEquals(-500, naik.remainingBp, "melebihi 100% sebesar 5%")

        val kurang = d.step(2, -20)
        assertEquals(4_000, kurang.shares[2])
        assertEquals(2_000, kurang.remainingBp)
    }

    @Test
    fun `persentase dijepit antara 0 dan 100`() {
        val d = RuleDraft(listOf(RoomId("a")), listOf(500))
        assertEquals(0, d.step(0, -50).shares[0])
        assertEquals(10_000, d.step(0, 500).shares[0])
        assertEquals(10_000, d.set(0, 99_999).shares[0])
        assertEquals(0, d.set(0, -1).shares[0])
    }

    @Test
    fun `persentase pecahan yang tidak disentuh tetap utuh`() {
        val d = RuleDraft(listOf(RoomId("a"), RoomId("b")), listOf(250, 9_750))
        val diubah = d.step(1, -1)
        assertEquals(250, diubah.shares[0])
        assertEquals(9_650, diubah.shares[1])
    }

    @Test
    fun `perubahan terdeteksi dan kembali ke semula bukan perubahan`() {
        val asal = RuleDraft(listOf(RoomId("a"), RoomId("b")), listOf(3_000, 7_000))
        assertTrue(!asal.hasChanges(asal))
        assertTrue(asal.step(0, 1).hasChanges(asal))
        assertTrue(!asal.step(0, 1).step(0, -1).hasChanges(asal))
    }

    @Test
    fun `hanya draf 100 persen dan tidak kosong yang seimbang`() {
        assertTrue(!RuleDraft(emptyList(), emptyList()).isBalanced)
        assertTrue(RuleDraft(listOf(RoomId("a")), listOf(10_000)).isBalanced)
        assertTrue(!RuleDraft(listOf(RoomId("a")), listOf(9_999)).isBalanced)
    }

    @Test
    fun `contoh Rp 1 juta ada bila total tidak melebihi 100 dan kosong bila melebihi`() {
        val f = LedgerFixture().standard()
        val d = draft(f).step(0, 5).step(2, -5)
        val contoh = assertNotNull(d.sample(Money.rupiah(1_000_000)))
        assertEquals(listOf(150_000L, 300_000L, 550_000L), contoh.shares.map { it.amount.minor })

        assertNull(draft(f).step(0, 5).sample(Money.rupiah(1_000_000)), "105% tidak sah")
        assertNull(RuleDraft(emptyList(), emptyList()).sample(Money.rupiah(1)))
    }

    @Test
    fun `draf yang seimbang menghasilkan aturan yang diterima layanan`() {
        val f = LedgerFixture().standard()
        val d = draft(f).step(0, 5).step(2, -5)

        assertIs<LedgerResult.Success<Unit>>(runSuspend { f.rules.changeRules(d.toRules()) })

        assertEquals(listOf(1_500, 3_000, 5_500), runSuspend { f.store.rules() }.map { it.share.value })
    }

    @Test
    fun `draf yang tidak seimbang ditolak layanan`() {
        val f = LedgerFixture().standard()
        val d = draft(f).step(0, 5)
        assertEquals(LedgerResult.Failure(LedgerError.RULES_NOT_100_PERCENT), runSuspend { f.rules.changeRules(d.toRules()) })
    }

    @Test
    fun `draf menolak jumlah ruang dan persentase yang berbeda`() {
        assertFailsWith<IllegalArgumentException> { RuleDraft(listOf(RoomId("a")), listOf(1, 2)) }
        assertFailsWith<IllegalArgumentException> { RuleDraft(listOf(RoomId("a")), listOf(1)).set(3, 1) }
    }
}
