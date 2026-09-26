package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.AccountId
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

class CatatTest {

    private fun context(f: LedgerFixture) = runSuspend { CatatContextLoader(f.store, f.store, f.store).load() }

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun keys(draft: CatatDraft, digits: String) = digits.fold(draft) { d, c -> d.pressKey(c.toString()) }

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success<T>).value

    // ------------------------------------------------------------------ konteks

    @Test
    fun `konteks memuat akun ruang kategori dan aturan`() {
        val f = LedgerFixture().standard()
        val c = context(f)

        assertEquals(listOf("Dompet"), c.accounts.map { it.name })
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), c.rooms.map { it.name })
        assertEquals(5, c.categoriesOf(f.room("Keluarga").id).size)
        assertEquals(listOf(1_000, 3_000, 6_000), c.rules.map { it.share.value })
        assertNull(c.lastAccountId)
        assertNull(c.lastRoomId)
    }

    @Test
    fun `pilihan terakhir diambil dari transaksi terakhir`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank")
        runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(1_000), f.account.id, f.room("Diri").id, f.category("Diri", "Investasi").id, f.today))
        }
        f.now += 10
        f.income(50_000, accountId = bank.id)

        val c = context(f)

        assertEquals(bank.id, c.lastAccountId, "akun dari transaksi terakhir jenis apa pun")
        assertEquals(f.room("Diri").id, c.lastRoomId, "ruang dari pengeluaran terakhir")
        assertEquals(f.category("Diri", "Investasi").id, c.lastCategoryId)
    }

    @Test
    fun `kategori bawaan ruang mengutamakan yang terakhir dipakai lalu yang pertama bukan sistem`() {
        val f = LedgerFixture().standard()
        val c = context(f)
        val keluarga = f.room("Keluarga").id
        assertEquals(f.category("Keluarga", "Belanja bulanan").id, c.defaultCategory(keluarga))

        val sekolah = f.category("Keluarga", "Sekolah").id
        assertEquals(sekolah, c.copy(lastCategoryId = sekolah).defaultCategory(keluarga))
        assertEquals(f.category("Keluarga", "Belanja bulanan").id, c.copy(lastCategoryId = f.category("Diri", "Belajar").id).defaultCategory(keluarga))
    }

    // ------------------------------------------------------------------ isian

    @Test
    fun `isian awal memakai akun pertama, ruang pertama, dan hari ini`() {
        val f = LedgerFixture().standard()
        val c = context(f)

        val d = CatatDraft.start(c, f.today)

        assertEquals(CatatMode.INCOME, d.mode)
        assertEquals(f.account.id, d.accountId)
        assertEquals(f.room("Memberi").id, d.roomId)
        assertEquals(f.today, d.date)
        assertEquals("Gaji", d.source)
        assertEquals(rupiah(0), d.amount)
    }

    @Test
    fun `isian awal mengikuti akun ruang dan kategori terakhir`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank")
        val c = context(f).copy(lastAccountId = bank.id, lastRoomId = f.room("Keluarga").id, lastCategoryId = f.category("Keluarga", "Sekolah").id)

        val d = CatatDraft.start(c, f.today, CatatMode.EXPENSE)

        assertEquals(bank.id, d.accountId)
        assertEquals(f.room("Keluarga").id, d.roomId)
        assertEquals(f.category("Keluarga", "Sekolah").id, d.categoryId)
    }

    @Test
    fun `pilihan terakhir yang sudah tidak ada diabaikan`() {
        val f = LedgerFixture().standard()
        val c = context(f).copy(lastAccountId = AccountId("hilang"), lastRoomId = RoomId("hilang"))

        val d = CatatDraft.start(c, f.today)

        assertEquals(f.account.id, d.accountId)
        assertEquals(f.room("Memberi").id, d.roomId)
    }

    @Test
    fun `papan angka mengisi nominal dan catatan dipotong 200 karakter`() {
        val f = LedgerFixture().standard()
        val d = keys(CatatDraft.start(context(f), f.today), "150000")

        assertEquals(rupiah(150_000), d.amount)
        assertEquals(200, d.withNote("x".repeat(300)).note.length)
    }

    @Test
    fun `berganti ruang mengganti kategori ke bawaan ruang itu`() {
        val f = LedgerFixture().standard()
        val c = context(f)
        val d = CatatDraft.start(c, f.today, CatatMode.EXPENSE).withRoom(f.room("Diri").id, c)

        assertEquals(f.room("Diri").id, d.roomId)
        assertEquals(f.category("Diri", "Dana darurat").id, d.categoryId)
        assertEquals(d, d.withRoom(f.room("Diri").id, c), "memilih ruang yang sama tidak mengubah apa pun")
    }

    @Test
    fun `tujuan transfer tidak pernah sama dengan asal`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank")
        val c = context(f)
        var d = CatatDraft.start(c, f.today, CatatMode.TRANSFER)
        assertEquals(bank.id, d.toAccountId, "tujuan bawaan: akun lain")

        d = d.withAccount(bank.id, c)
        assertEquals(f.account.id, d.toAccountId, "asal diganti ke akun tujuan: tujuan berpindah")

        d = d.withToAccount(bank.id, c)
        assertTrue(d.toAccountId != d.accountId)
    }

    // ------------------------------------------------------------------ masalah isian

    @Test
    fun `nominal kosong atau nol menghalangi simpan`() {
        val f = LedgerFixture().standard()
        val c = context(f)
        val d = CatatDraft.start(c, f.today)
        assertEquals(CatatIssue.NO_AMOUNT, d.issue(c))
        assertEquals(CatatIssue.NO_AMOUNT, d.pressKey("0").issue(c))
        assertNull(keys(d, "5").issue(c))
    }

    @Test
    fun `pengeluaran tanpa ruang tidak bisa disimpan`() {
        val f = LedgerFixture()
        runSuspend { f.setup.setUp(RoomTemplate.KOSONG, FirstAccount("Dompet", com.roziqrizal.rizqflow.domain.model.AccountKind.CASH, rupiah(0))) }
        val c = context(f)
        val d = keys(CatatDraft.start(c, f.today, CatatMode.EXPENSE), "5000")

        assertEquals(CatatIssue.NO_ROOM, d.issue(c))
        assertNull(keys(d.withMode(CatatMode.INCOME, c), "").issue(c), "pemasukan tetap bisa tanpa ruang")
    }

    @Test
    fun `transfer butuh dua akun`() {
        val f = LedgerFixture().standard()
        val c = context(f)
        val d = keys(CatatDraft.start(c, f.today, CatatMode.TRANSFER), "5000")
        assertEquals(CatatIssue.TRANSFER_NEEDS_TWO_ACCOUNTS, d.issue(c))
        assertTrue(!c.canTransfer)

        f.addAccount("Bank")
        val c2 = context(f)
        assertNull(keys(CatatDraft.start(c2, f.today, CatatMode.TRANSFER), "5000").issue(c2))
    }

    // ------------------------------------------------------------------ dari isian ke transaksi

    @Test
    fun `isian pengeluaran menjadi transaksi yang tersimpan`() {
        val f = LedgerFixture().standard()
        val c = context(f)
        val d = keys(CatatDraft.start(c, f.today, CatatMode.EXPENSE).withRoom(f.room("Keluarga").id, c), "150000").withNote("Belanja pasar")

        val tx = runSuspend { f.ledger.recordExpense(d.toExpense()) }.value()

        assertEquals(rupiah(150_000), tx.amount)
        assertEquals("Belanja pasar", tx.note)
        assertEquals(f.category("Keluarga", "Belanja bulanan").id, tx.categoryId)
    }

    @Test
    fun `isian pemasukan menjadi transaksi dengan sumber dan akun`() {
        val f = LedgerFixture().standard()
        val c = context(f)
        val d = keys(CatatDraft.start(c, f.today), "8500000").withSource("Usaha").withNote("Proyek")

        val receipt = runSuspend { f.ledger.recordIncome(d.toIncome()) }.value()

        assertEquals("Usaha", receipt.transaction.incomeSource)
        assertEquals(rupiah(8_500_000), receipt.transaction.amount)
    }

    @Test
    fun `isian transfer menjadi transaksi antar akun`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank")
        val c = context(f)
        val d = keys(CatatDraft.start(c, f.today, CatatMode.TRANSFER), "100000")

        val tx = runSuspend { f.ledger.recordTransfer(d.toTransfer()) }.value()

        assertEquals(f.account.id, tx.accountId)
        assertEquals(bank.id, tx.toAccountId)
    }

    // ------------------------------------------------------------------ banner jatah

    @Test
    fun `pengeluaran yang melewati jatah bulan itu memunculkan peringatan lembut`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000) // Keluarga menerima 600.000 bulan ini
        val keluarga = f.room("Keluarga").id

        val aman = runSuspend { f.ledger.budgetWarning(keluarga, rupiah(600_000), f.today) }
        val lewat = runSuspend { f.ledger.budgetWarning(keluarga, rupiah(600_001), f.today) }

        assertNull(aman, "tepat sebesar jatah belum melewati")
        val w = assertNotNull(lewat)
        assertEquals(rupiah(600_000), w.allocated)
        assertEquals(rupiah(600_001), w.spentAfter)
        assertEquals("Keluarga", w.room.name)
    }

    @Test
    fun `peringatan memperhitungkan pengeluaran yang sudah tercatat bulan itu`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000)
        val keluarga = f.room("Keluarga")
        runSuspend { f.ledger.recordExpense(NewExpense(rupiah(500_000), f.account.id, keluarga.id, f.category("Keluarga", "Sekolah").id, f.today)) }

        assertNull(runSuspend { f.ledger.budgetWarning(keluarga.id, rupiah(100_000), f.today) })
        assertNotNull(runSuspend { f.ledger.budgetWarning(keluarga.id, rupiah(100_001), f.today) })
    }

    @Test
    fun `bulan tanpa pemasukan tidak memunculkan peringatan terus-menerus`() {
        val f = LedgerFixture().standard()
        assertNull(runSuspend { f.ledger.budgetWarning(f.room("Keluarga").id, rupiah(9_999_999), f.today) })
    }

    @Test
    fun `jatah dan terpakai dihitung per bulan Masehi`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000) // 21 September
        val keluarga = f.room("Keluarga").id

        val oktober = LocalDate.of(2026, 10, 3)
        assertNull(runSuspend { f.ledger.budgetWarning(keluarga, rupiah(9_999_999), oktober) }, "Oktober belum punya jatah")
        assertNotNull(runSuspend { f.ledger.budgetWarning(keluarga, rupiah(600_001), LocalDate.of(2026, 9, 1)) }, "tanggal 1 termasuk bulan yang sama")
        assertNotNull(runSuspend { f.ledger.budgetWarning(keluarga, rupiah(600_001), LocalDate.of(2026, 9, 30)) }, "tanggal terakhir termasuk bulan yang sama")
    }

    @Test
    fun `peringatan untuk ruang yang tidak ada adalah kosong`() {
        val f = LedgerFixture().standard()
        assertNull(runSuspend { f.ledger.budgetWarning(RoomId("hilang"), rupiah(1), f.today) })
    }

    // ------------------------------------------------------------------ ubah sekali ini

    private fun rule(f: LedgerFixture, room: String, percent: Int) = AllocationRule(f.room(room).id, BasisPoints.percent(percent))

    @Test
    fun `pembagian sekali ini mengalirkan sesuai pilihan tanpa mengubah aturan`() {
        val f = LedgerFixture().standard()
        val split = OneTimeSplit.from(context(f).rules).set(0, 50).set(1, 30)

        val receipt = runSuspend { f.ledger.recordIncome(f.incomeCommand(1_000_000).copy(overrideRules = split.toRules())) }.value()

        assertEquals(listOf(500_000L, 300_000L, 200_000L), runSuspend { f.store.entriesOf(receipt.transaction.id) }.map { it.amount.minor })
        assertEquals(listOf(1_000, 3_000, 6_000), runSuspend { f.store.rules() }.map { it.share.value }, "aturan tetap")
    }

    @Test
    fun `potret pembagian sekali ini tersimpan dan dipakai saat nominal diubah`() {
        val f = LedgerFixture().standard()
        val split = OneTimeSplit.from(context(f).rules).set(0, 50).set(1, 30)
        val receipt = runSuspend { f.ledger.recordIncome(f.incomeCommand(1_000_000).copy(overrideRules = split.toRules())) }.value()

        runSuspend { f.ledger.editIncomeAmount(receipt.transaction.id, rupiah(2_000_000)) }

        assertEquals(listOf(1_000_000L, 600_000L, 400_000L), runSuspend { f.store.entriesOf(receipt.transaction.id) }.map { it.amount.minor })
    }

    @Test
    fun `pembagian sekali ini yang tidak sah ditolak`() {
        val f = LedgerFixture().standard()
        fun coba(rules: List<AllocationRule>) = runSuspend { f.ledger.recordIncome(f.incomeCommand(1_000).copy(overrideRules = rules)) }

        assertEquals(LedgerResult.Failure(LedgerError.RULES_INVALID), coba(listOf(rule(f, "Memberi", 60), rule(f, "Diri", 60))), "lebih dari 100%")
        assertEquals(LedgerResult.Failure(LedgerError.RULES_INVALID), coba(listOf(rule(f, "Memberi", 50), rule(f, "Memberi", 50))), "ruang ganda")
        assertEquals(LedgerResult.Failure(LedgerError.RULES_INVALID), coba(listOf(AllocationRule(RoomId("hilang"), BasisPoints.percent(100)))), "ruang tidak ada")
        val diri = f.room("Diri")
        f.store.roomRows[diri.id] = diri.copy(archived = true)
        assertEquals(LedgerResult.Failure(LedgerError.RULES_INVALID), coba(listOf(rule(f, "Diri", 100))), "ruang terarsip")
        assertTrue(f.store.transactionRows.isEmpty())
    }

    @Test
    fun `pembagian sekali ini di bawah 100 persen menyisakan bagian belum dialirkan`() {
        val f = LedgerFixture().standard()
        val receipt = runSuspend {
            f.ledger.recordIncome(f.incomeCommand(1_000_000).copy(overrideRules = listOf(rule(f, "Memberi", 10), rule(f, "Keluarga", 50))))
        }.value()
        assertEquals(rupiah(400_000), receipt.allocation.unallocated)
    }

    @Test
    fun `pratinjau dengan pembagian sekali ini tidak menyimpan apa pun`() {
        val f = LedgerFixture().standard()
        val preview = runSuspend { f.ledger.previewIncome(rupiah(1_000_000), listOf(rule(f, "Memberi", 100))) }
        assertEquals(listOf(1_000_000L), preview.shares.map { it.amount.minor })
        assertTrue(f.store.transactionRows.isEmpty())
    }

    @Test
    fun `pembagian sekali ini dari aturan pecahan dibulatkan ke persen dan totalnya tetap 100`() {
        val rules = listOf(
            AllocationRule(RoomId("a"), BasisPoints(250)),
            AllocationRule(RoomId("b"), BasisPoints(2_750)),
            AllocationRule(RoomId("c"), BasisPoints(7_000)),
        )
        val split = OneTimeSplit.from(rules)
        assertEquals(100, split.percents.sum())
        assertEquals(listOf(3, 28, 69), split.percents) // 2,5 -> 3, 27,5 -> 28, sisanya 69
    }

    @Test
    fun `pembagian sekali ini tanpa aturan kosong dan satu ruang selalu 100`() {
        assertTrue(OneTimeSplit.from(emptyList()).roomIds.isEmpty())
        assertEquals(listOf(100), OneTimeSplit.from(listOf(AllocationRule(RoomId("a"), BasisPoints.percent(40)))).percents)
    }

    @Test
    fun `tombol tambah dan kurang pada pembagian sekali ini menjaga total 100`() {
        val split = OneTimeSplit(listOf(RoomId("a"), RoomId("b"), RoomId("c")), listOf(10, 30, 60)).step(0, 5).step(1, -30)
        assertEquals(listOf(15, 0, 85), split.percents)
        assertEquals(100, split.percents.sum())
        assertFailsWith<IllegalArgumentException> { OneTimeSplit(listOf(RoomId("a")), listOf(50, 50)) }
    }

    @Test
    fun `jenis transaksi terakhir memilih yang paling baru`() {
        val f = LedgerFixture().standard()
        f.income(1_000)
        f.now += 5
        val expense = runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(1), f.account.id, f.room("Diri").id, f.category("Diri", "Belajar").id, f.today))
        }.value()

        assertEquals(expense.id, runSuspend { f.store.latest(null) }!!.id)
        assertEquals(expense.id, runSuspend { f.store.latest(TransactionKind.EXPENSE) }!!.id)
        assertIs<Any>(runSuspend { f.store.latest(TransactionKind.INCOME) }!!)
        assertNull(runSuspend { f.store.latest(TransactionKind.TRANSFER) })
    }
}
