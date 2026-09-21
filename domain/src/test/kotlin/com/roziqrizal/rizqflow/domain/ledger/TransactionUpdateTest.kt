package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionUpdateTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success<T>).value

    private fun LedgerResult<*>.error(): LedgerError = (this as LedgerResult.Failure).error

    private fun context(f: LedgerFixture, editing: MoneyTransaction? = null) =
        runSuspend { CatatContextLoader(f.store, f.store, f.store).load(editing) }

    private fun update(f: LedgerFixture, id: TransactionId, draft: CatatDraft) = runSuspend { f.ledger.updateTransaction(id, draft) }

    private fun expenseTx(f: LedgerFixture, amount: Long = 150_000, room: String = "Keluarga", category: String = "Belanja bulanan"): MoneyTransaction =
        runSuspend { f.ledger.recordExpense(NewExpense(rupiah(amount), f.account.id, f.room(room).id, f.category(room, category).id, f.today, "Belanja pasar")) }.value()

    private fun entryAmounts(f: LedgerFixture, id: TransactionId) = runSuspend { f.store.entriesOf(id) }.map { it.amount.minor }

    // ------------------------------------------------------------------ isian dari transaksi

    @Test
    fun `isian dari pengeluaran memuat semua kolomnya apa adanya`() {
        val f = LedgerFixture().standard()
        val tx = expenseTx(f)

        val d = CatatDraft.from(tx)

        assertEquals(CatatMode.EXPENSE, d.mode)
        assertEquals(rupiah(150_000), d.amount)
        assertEquals(tx.roomId, d.roomId)
        assertEquals(tx.categoryId, d.categoryId)
        assertEquals("Belanja pasar", d.note)
        assertEquals(f.today, d.date)
    }

    @Test
    fun `isian dari pemasukan dan transfer memilih tab yang sesuai`() {
        val f = LedgerFixture().standard()
        val income = f.income(1_000_000).value().transaction
        val bank = f.addAccount("Bank")
        val transfer = runSuspend { f.ledger.recordTransfer(NewTransfer(rupiah(1), f.account.id, bank.id, f.today)) }.value()

        assertEquals(CatatMode.INCOME, CatatDraft.from(income).mode)
        assertEquals("Gaji", CatatDraft.from(income).source)
        assertEquals(CatatMode.TRANSFER, CatatDraft.from(transfer).mode)
        assertEquals(bank.id, CatatDraft.from(transfer).toAccountId)
    }

    // ------------------------------------------------------------------ ubah pengeluaran

    @Test
    fun `mengubah pengeluaran menyimpan semua kolom dan menjaga waktu dibuat`() {
        val f = LedgerFixture().standard()
        val tx = expenseTx(f)
        f.now = 9_000L
        val bank = f.addAccount("Bank")
        val draft = CatatDraft.from(tx).copy(digits = "175000", roomId = f.room("Diri").id, categoryId = f.category("Diri", "Investasi").id, accountId = bank.id, date = LocalDate.of(2026, 9, 10), note = "  dipindah  ")

        val before = update(f, tx.id, draft).value()

        val now = runSuspend { f.store.find(tx.id) }!!
        assertEquals(rupiah(175_000), now.amount)
        assertEquals(f.room("Diri").id, now.roomId)
        assertEquals(f.category("Diri", "Investasi").id, now.categoryId)
        assertEquals(bank.id, now.accountId)
        assertEquals(LocalDate.of(2026, 9, 10), now.occurredOn)
        assertEquals("dipindah", now.note)
        assertEquals(tx.createdAtMillis, now.createdAtMillis)
        assertEquals(9_000L, now.updatedAtMillis)
        assertEquals(tx, before.transaction, "hasilnya keadaan sebelum diubah")
    }

    @Test
    fun `saldo mengikuti pengeluaran yang diubah`() {
        val f = LedgerFixture().standard()
        val tx = expenseTx(f)
        assertEquals(rupiah(350_000), f.balance())

        update(f, tx.id, CatatDraft.from(tx).copy(digits = "100000"))

        assertEquals(rupiah(400_000), f.balance())
    }

    @Test
    fun `ubah pengeluaran menolak masukan yang tidak sah dan data tidak berubah`() {
        val f = LedgerFixture().standard()
        val tx = expenseTx(f)
        val d = CatatDraft.from(tx)
        val dariRuangLain = f.category("Diri", "Investasi").id

        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, update(f, tx.id, d.copy(digits = "")).error())
        assertEquals(LedgerError.NOTE_TOO_LONG, update(f, tx.id, d.copy(note = "x".repeat(201))).error())
        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, update(f, tx.id, d.copy(accountId = AccountId("hilang"))).error())
        assertEquals(LedgerError.ROOM_NOT_FOUND, update(f, tx.id, d.copy(roomId = RoomId("hilang"))).error())
        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, update(f, tx.id, d.copy(categoryId = dariRuangLain)).error())
        assertEquals(tx, runSuspend { f.store.find(tx.id) })
    }

    @Test
    fun `transaksi yang tidak ada atau jenisnya berbeda ditolak`() {
        val f = LedgerFixture().standard()
        val tx = expenseTx(f)

        assertEquals(LedgerError.TRANSACTION_NOT_FOUND, update(f, TransactionId("hilang"), CatatDraft.from(tx)).error())
        assertEquals(LedgerError.KIND_MISMATCH, update(f, tx.id, CatatDraft.from(tx).copy(mode = CatatMode.INCOME)).error())
    }

    @Test
    fun `akun ruang dan kategori terarsip boleh tetap dipakai selama tidak diganti`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank")
        val tx = runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(5_000), bank.id, f.room("Keluarga").id, f.category("Keluarga", "Sekolah").id, f.today, "SPP"))
        }.value()
        f.store.accountRows[bank.id] = bank.copy(archived = true)
        val keluarga = f.room("Keluarga")
        f.store.roomRows[keluarga.id] = keluarga.copy(archived = true)
        val sekolah = f.category("Keluarga", "Sekolah")
        f.store.categoryRows[sekolah.id] = sekolah.copy(archived = true)

        assertIs<LedgerResult.Success<TransactionSnapshot>>(update(f, tx.id, CatatDraft.from(tx).copy(note = "SPP September")))
        assertEquals("SPP September", runSuspend { f.store.find(tx.id) }!!.note)

        // Berpindah ke akun terarsip lain tetap ditolak.
        val lain = f.addAccount("Lama", archived = true)
        assertEquals(LedgerError.ACCOUNT_ARCHIVED, update(f, tx.id, CatatDraft.from(tx).copy(accountId = lain.id)).error())
    }

    // ------------------------------------------------------------------ ubah pemasukan

    @Test
    fun `mengubah nominal pemasukan menghitung ulang dengan potret lama`() {
        val f = LedgerFixture().standard()
        val income = f.income(1_000_000).value().transaction
        val d = CatatDraft.from(income).copy(digits = "2000000", source = "Usaha", note = "proyek")

        val before = update(f, income.id, d).value()

        assertEquals(listOf(200_000L, 600_000L, 1_200_000L), entryAmounts(f, income.id))
        val now = runSuspend { f.store.find(income.id) }!!
        assertEquals("Usaha", now.incomeSource)
        assertEquals("proyek", now.note)
        assertEquals(listOf(100_000L, 300_000L, 600_000L), before.entries.map { it.amount.minor })
    }

    @Test
    fun `mengubah kolom lain pemasukan tidak menyentuh potret alokasi`() {
        val f = LedgerFixture().standard()
        val income = f.income(1_000_000).value().transaction
        val idsSebelum = runSuspend { f.store.entriesOf(income.id) }.map { it.id }
        val bank = f.addAccount("Bank")

        update(f, income.id, CatatDraft.from(income).copy(accountId = bank.id, date = LocalDate.of(2026, 9, 2)))

        assertEquals(idsSebelum, runSuspend { f.store.entriesOf(income.id) }.map { it.id })
        assertEquals(listOf(100_000L, 300_000L, 600_000L), entryAmounts(f, income.id))
        assertEquals(bank.id, runSuspend { f.store.find(income.id) }!!.accountId)
    }

    @Test
    fun `potret dari pembagian sekali ini dipakai saat nominal pemasukan diubah lewat detail`() {
        val f = LedgerFixture().standard()
        val split = OneTimeSplit.from(context(f).rules).set(0, 50).set(1, 30)
        val income = runSuspend { f.ledger.recordIncome(f.incomeCommand(1_000_000).copy(overrideRules = split.toRules())) }.value().transaction

        update(f, income.id, CatatDraft.from(income).copy(digits = "2000000"))

        assertEquals(listOf(1_000_000L, 600_000L, 400_000L), entryAmounts(f, income.id))
    }

    @Test
    fun `ubah pemasukan menolak sumber terlalu panjang dan akun terarsip baru`() {
        val f = LedgerFixture().standard()
        val income = f.income(1_000).value().transaction
        val lama = f.addAccount("Lama", archived = true)

        assertEquals(LedgerError.SOURCE_TOO_LONG, update(f, income.id, CatatDraft.from(income).copy(source = "x".repeat(41))).error())
        assertEquals(LedgerError.ACCOUNT_ARCHIVED, update(f, income.id, CatatDraft.from(income).copy(accountId = lama.id)).error())
    }

    @Test
    fun `ubah pemasukan dengan sumber kosong menyimpan tanpa sumber`() {
        val f = LedgerFixture().standard()
        val income = f.income(1_000).value().transaction
        update(f, income.id, CatatDraft.from(income).copy(source = "  "))
        assertNull(runSuspend { f.store.find(income.id) }!!.incomeSource)
    }

    // ------------------------------------------------------------------ ubah transfer

    @Test
    fun `mengubah transfer memindahkan saldo dan menolak akun sama`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank", opening = 200_000)
        val lain = f.addAccount("Lain")
        val tx = runSuspend { f.ledger.recordTransfer(NewTransfer(rupiah(100_000), f.account.id, bank.id, f.today)) }.value()

        update(f, tx.id, CatatDraft.from(tx).copy(digits = "50000", toAccountId = lain.id))

        assertEquals(rupiah(450_000), f.balance(f.account.id))
        assertEquals(rupiah(200_000), f.balance(bank.id))
        assertEquals(rupiah(50_000), f.balance(lain.id))
        assertEquals(LedgerError.SAME_ACCOUNT, update(f, tx.id, CatatDraft.from(tx).copy(accountId = lain.id, toAccountId = lain.id)).error())
    }

    // ------------------------------------------------------------------ urungkan

    @Test
    fun `menghapus lalu memulihkan pengeluaran mengembalikan persis yang sama`() {
        val f = LedgerFixture().standard()
        val tx = expenseTx(f)
        val snapshot = runSuspend { f.ledger.snapshotOf(tx.id) }!!

        runSuspend { f.ledger.delete(tx.id) }
        assertNull(runSuspend { f.store.find(tx.id) })
        runSuspend { f.ledger.restore(snapshot) }

        assertEquals(tx, runSuspend { f.store.find(tx.id) })
        assertEquals(rupiah(350_000), f.balance())
    }

    @Test
    fun `menghapus lalu memulihkan pemasukan mengembalikan potret alokasinya`() {
        val f = LedgerFixture().standard()
        val income = f.income(1_000_000).value().transaction
        val snapshot = runSuspend { f.ledger.snapshotOf(income.id) }!!
        assertEquals(3, snapshot.entries.size)

        runSuspend { f.ledger.delete(income.id) }
        assertTrue(f.store.entryRows.isEmpty())
        runSuspend { f.ledger.restore(snapshot) }

        assertEquals(income, runSuspend { f.store.find(income.id) })
        assertEquals(snapshot.entries.map { it.id to it.amount }, runSuspend { f.store.entriesOf(income.id) }.map { it.id to it.amount })
    }

    @Test
    fun `memulihkan setelah ubah membatalkan perubahan pengeluaran dan pemasukan`() {
        val f = LedgerFixture().standard()
        val tx = expenseTx(f)
        val income = f.income(1_000_000).value().transaction

        val sebelumTx = update(f, tx.id, CatatDraft.from(tx).copy(digits = "1", note = "ubah")).value()
        val sebelumIncome = update(f, income.id, CatatDraft.from(income).copy(digits = "3000000")).value()
        runSuspend { f.ledger.restore(sebelumTx) }
        runSuspend { f.ledger.restore(sebelumIncome) }

        assertEquals(tx, runSuspend { f.store.find(tx.id) })
        assertEquals(income, runSuspend { f.store.find(income.id) })
        assertEquals(listOf(100_000L, 300_000L, 600_000L), entryAmounts(f, income.id))
    }

    @Test
    fun `snapshot transaksi yang tidak ada kosong`() {
        val f = LedgerFixture().standard()
        assertNull(runSuspend { f.ledger.snapshotOf(TransactionId("hilang")) })
    }

    // ------------------------------------------------------------------ konteks untuk ubah

    @Test
    fun `konteks ubah memuat akun ruang dan kategori terarsip yang dipakai transaksi`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank")
        val tx = runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(5_000), bank.id, f.room("Keluarga").id, f.category("Keluarga", "Sekolah").id, f.today))
        }.value()
        f.store.accountRows[bank.id] = bank.copy(archived = true)
        val keluarga = f.room("Keluarga")
        f.store.roomRows[keluarga.id] = keluarga.copy(archived = true)
        val sekolah = f.category("Keluarga", "Sekolah")
        f.store.categoryRows[sekolah.id] = sekolah.copy(archived = true)

        val biasa = context(f)
        val ubah = context(f, tx)

        assertTrue(biasa.accounts.none { it.id == bank.id })
        assertTrue(biasa.rooms.none { it.id == keluarga.id })
        assertNotNull(ubah.accounts.firstOrNull { it.id == bank.id })
        assertNotNull(ubah.rooms.firstOrNull { it.id == keluarga.id })
        assertNotNull(ubah.categoriesOf(keluarga.id).firstOrNull { it.id == sekolah.id })
    }

    @Test
    fun `memilih ruang dan akun di isian ubah tidak menimpa pilihan terarsip`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank")
        val tx = runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(5_000), bank.id, f.room("Keluarga").id, f.category("Keluarga", "Sekolah").id, f.today))
        }.value()
        f.store.accountRows[bank.id] = bank.copy(archived = true)
        val ctx = context(f, tx)

        val d = CatatDraft.from(tx).withNote("catatan baru").withAccount(bank.id, ctx)

        assertEquals(bank.id, d.accountId)
        assertEquals(tx.categoryId, d.categoryId)
    }

    // ------------------------------------------------------------------ banner saat mengubah

    @Test
    fun `banner jatah tidak menghitung pengeluaran yang sedang diubah dua kali`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000) // Keluarga: 600.000
        val tx = expenseTx(f, amount = 500_000)
        val keluarga = f.room("Keluarga").id

        // Mengubah 500.000 menjadi 600.000: total terpakai 600.000, tepat jatah, tidak melewati.
        assertNull(runSuspend { f.ledger.budgetWarning(keluarga, rupiah(600_000), f.today, excluding = tx.id) })
        // Tanpa pengecualian, nilai lamanya terhitung ikut dan banner muncul keliru.
        assertNotNull(runSuspend { f.ledger.budgetWarning(keluarga, rupiah(600_000), f.today) })
        assertNotNull(runSuspend { f.ledger.budgetWarning(keluarga, rupiah(600_001), f.today, excluding = tx.id) })
    }

    @Test
    fun `pengecualian hanya berlaku untuk ruang dan bulan yang sama`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000)
        val tx = expenseTx(f, amount = 500_000, room = "Keluarga")
        val diri = f.room("Diri").id

        // Pengeluaran lama ada di ruang Keluarga: tidak dikurangkan dari ruang Diri.
        assertNull(runSuspend { f.ledger.budgetWarning(diri, rupiah(300_000), f.today, excluding = tx.id) })
        assertNotNull(runSuspend { f.ledger.budgetWarning(diri, rupiah(300_001), f.today, excluding = tx.id) })
    }

    @Test
    fun `transaksi hasil ubah tetap sah menurut model`() {
        val f = LedgerFixture().standard()
        val tx = expenseTx(f)
        update(f, tx.id, CatatDraft.from(tx).copy(digits = "1"))
        val now = runSuspend { f.store.find(tx.id) }!!
        assertEquals(TransactionKind.EXPENSE, now.kind)
        assertNull(now.toAccountId)
    }
}
