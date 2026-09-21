package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.entitlement.Plan
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleServiceTest {

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success<T>).value

    private fun rule(f: LedgerFixture, room: String, percent: Int) = AllocationRule(f.room(room).id, BasisPoints.percent(percent))

    private fun change(f: LedgerFixture, vararg rules: AllocationRule) = runSuspend { f.rules.changeRules(rules.toList()) }

    private fun addRoom(f: LedgerFixture, name: String = "Orang tua", slot: Int = 4) =
        runSuspend { f.rules.addRoom(NewRoom(name, RoomKind.MENCUKUPI, "home", slot)) }

    private fun rulesOf(f: LedgerFixture) = runSuspend { f.store.rules() }.map { f.store.roomRows.getValue(it.roomId).name to it.share.value }

    // ------------------------------------------------------------------ aturan alokasi

    @Test
    fun `aturan yang tepat 100 persen tersimpan`() {
        val f = LedgerFixture().standard()

        val result = change(f, rule(f, "Memberi", 50), rule(f, "Diri", 25), rule(f, "Keluarga", 25))

        assertIs<LedgerResult.Success<Unit>>(result)
        assertEquals(listOf("Memberi" to 5_000, "Diri" to 2_500, "Keluarga" to 2_500), rulesOf(f))
    }

    @Test
    fun `persentase pecahan seperti 2,5 persen tersimpan eksak`() {
        val f = LedgerFixture().standard()
        val result = change(
            f,
            AllocationRule(f.room("Memberi").id, BasisPoints(250)),
            AllocationRule(f.room("Diri").id, BasisPoints(2_750)),
            AllocationRule(f.room("Keluarga").id, BasisPoints(7_000)),
        )
        assertIs<LedgerResult.Success<Unit>>(result)
        assertEquals(listOf(250, 2_750, 7_000), runSuspend { f.store.rules() }.map { it.share.value })
    }

    @Test
    fun `urutan tersimpan mengikuti prioritas ruang walau masukan disusun terbalik`() {
        val f = LedgerFixture().standard()

        change(f, rule(f, "Keluarga", 60), rule(f, "Diri", 30), rule(f, "Memberi", 10))

        assertEquals(listOf("Memberi", "Diri", "Keluarga"), rulesOf(f).map { it.first })
    }

    @Test
    fun `total bukan 100 persen ditolak dan aturan lama tetap`() {
        val f = LedgerFixture().standard()
        val sebelum = rulesOf(f)

        assertEquals(LedgerResult.Failure(LedgerError.RULES_NOT_100_PERCENT), change(f, rule(f, "Memberi", 10), rule(f, "Diri", 30), rule(f, "Keluarga", 59)))
        assertEquals(LedgerResult.Failure(LedgerError.RULES_NOT_100_PERCENT), change(f, rule(f, "Memberi", 60), rule(f, "Diri", 30), rule(f, "Keluarga", 20)))
        assertEquals(sebelum, rulesOf(f))
    }

    @Test
    fun `aturan kosong, ganda, atau untuk ruang yang tidak ada ditolak`() {
        val f = LedgerFixture().standard()
        val invalid = LedgerResult.Failure(LedgerError.RULES_INVALID)

        assertEquals(invalid, change(f))
        assertEquals(invalid, change(f, rule(f, "Memberi", 50), rule(f, "Memberi", 50)))
        assertEquals(invalid, change(f, rule(f, "Memberi", 50), AllocationRule(RoomId("tidak-ada"), BasisPoints.percent(50))))
    }

    @Test
    fun `ruang terarsip tidak boleh ada di aturan`() {
        val f = LedgerFixture().standard()
        val diri = f.room("Diri")
        f.store.roomRows[diri.id] = diri.copy(archived = true)

        assertEquals(LedgerResult.Failure(LedgerError.RULES_INVALID), change(f, rule(f, "Memberi", 40), rule(f, "Diri", 20), rule(f, "Keluarga", 40)))
        assertIs<LedgerResult.Success<Unit>>(change(f, rule(f, "Memberi", 40), rule(f, "Keluarga", 60)))
    }

    // ------------------------------------------------------------------ menambah ruang

    @Test
    fun `ruang baru masuk paling belakang dengan kategori Lain-lain dan tanpa persentase`() {
        val f = LedgerFixture().standard()

        val id = (addRoom(f) as LedgerResult.Success<RoomId>).value

        val room = f.store.roomRows.getValue(id)
        assertEquals("Orang tua", room.name)
        assertEquals(3, room.sortOrder)
        assertEquals(RoomKind.MENCUKUPI, room.kind)
        assertEquals(listOf("Lain-lain", "Tak terlacak"), runSuspend { f.store.categories(id) }.map { it.name })
        assertTrue(rulesOf(f).none { it.first == "Orang tua" }, "belum menerima alokasi")
    }

    @Test
    fun `ruang baru tidak menerima alokasi sampai aturan diubah`() {
        val f = LedgerFixture().standard()
        addRoom(f)

        val receipt = f.income(1_000_000).value()
        assertEquals(3, runSuspend { f.store.entriesOf(receipt.transaction.id) }.size)

        change(f, rule(f, "Memberi", 10), rule(f, "Diri", 30), rule(f, "Keluarga", 50), rule(f, "Orang tua", 10))
        val baru = f.income(1_000_000).value()
        assertEquals(4, runSuspend { f.store.entriesOf(baru.transaction.id) }.size)
    }

    @Test
    fun `paket gratis dibatasi lima ruang dan ruang keenam ditolak`() {
        val f = LedgerFixture().standard()

        assertIs<LedgerResult.Success<RoomId>>(addRoom(f, "Orang tua", 4))
        assertIs<LedgerResult.Success<RoomId>>(addRoom(f, "Tabungan haji", 5))
        assertEquals(5, f.store.roomRows.size)

        assertEquals(LedgerResult.Failure(LedgerError.ROOM_LIMIT_REACHED), addRoom(f, "Liburan", 6))
        assertEquals(5, f.store.roomRows.size)
    }

    @Test
    fun `paket Pro tidak dibatasi`() {
        val f = LedgerFixture(plans = setOf(Plan.PRO)).standard()
        repeat(6) { assertIs<LedgerResult.Success<RoomId>>(addRoom(f, "Ruang ${it + 1}", it + 4)) }
        assertEquals(9, f.store.roomRows.size)
    }

    @Test
    fun `ruang terarsip tidak dihitung dalam batas`() {
        val f = LedgerFixture().standard()
        addRoom(f, "Orang tua", 4)
        addRoom(f, "Tabungan haji", 5)
        val diri = f.room("Diri")
        f.store.roomRows[diri.id] = diri.copy(archived = true)

        assertIs<LedgerResult.Success<RoomId>>(addRoom(f, "Liburan", 6))
    }

    @Test
    fun `nama ruang kosong, terlalu panjang, atau slot warna nol ditolak`() {
        val f = LedgerFixture().standard()
        val invalid = LedgerResult.Failure(LedgerError.INVALID_NAME)

        assertEquals(invalid, addRoom(f, "   "))
        assertEquals(invalid, addRoom(f, "x".repeat(25)))
        assertEquals(invalid, addRoom(f, "Orang tua", slot = 0))
        assertIs<LedgerResult.Success<RoomId>>(addRoom(f, "x".repeat(24)))
    }

    @Test
    fun `nama ruang yang sudah dipakai ditolak walau beda huruf besar dan spasi tepi`() {
        val f = LedgerFixture().standard()
        assertEquals(LedgerResult.Failure(LedgerError.NAME_TAKEN), addRoom(f, "  keluarga "))
        assertEquals(3, f.store.roomRows.size)
    }

    @Test
    fun `galat penyimpanan saat menambah ruang tidak meninggalkan ruang tanpa kategori`() {
        val f = LedgerFixture().standard()
        f.store.failNextWrite = IllegalStateException("disk penuh")

        runCatching { addRoom(f) }

        assertEquals(3, f.store.roomRows.size)
        assertEquals(f.store.roomRows.keys, f.store.categoryRows.values.map { it.roomId }.toSet())
    }
}
