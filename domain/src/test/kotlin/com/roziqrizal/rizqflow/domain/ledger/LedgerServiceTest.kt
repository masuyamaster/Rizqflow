package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Currency
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerServiceTest {

    private fun <T> LedgerResult<T>.value(): T = (this as LedgerResult.Success<T>).value

    private fun LedgerResult<*>.error(): LedgerError = (this as LedgerResult.Failure).error

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun entriesOf(f: LedgerFixture, id: TransactionId) = runSuspend { f.store.entriesOf(id) }

    // ------------------------------------------------------------------ pratinjau

    @Test
    fun `pratinjau membagi nominal sesuai aturan dengan metode sisa terbesar`() {
        val f = LedgerFixture().standard()

        val preview = runSuspend { f.ledger.previewIncome(rupiah(1_234_567)) }

        assertEquals(listOf(123_457L, 370_370L, 740_740L), preview.shares.map { it.amount.minor })
        assertEquals(rupiah(0), preview.unallocated)
    }

    @Test
    fun `pratinjau tidak menyimpan apa pun`() {
        val f = LedgerFixture().standard()
        runSuspend { f.ledger.previewIncome(rupiah(1_000_000)) }
        assertTrue(f.store.transactionRows.isEmpty() && f.store.entryRows.isEmpty())
    }

    // ------------------------------------------------------------------ pemasukan

    @Test
    fun `pemasukan tersimpan sebagai satu transaksi dengan potret alokasi tiap ruang`() {
        val f = LedgerFixture().standard()

        val receipt = f.income(1_000_000).value()

        assertEquals(1, f.store.transactionRows.size)
        val entries = entriesOf(f, receipt.transaction.id)
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), entries.map { f.store.roomRows.getValue(it.roomId).name })
        assertEquals(listOf(100_000L, 300_000L, 600_000L), entries.map { it.amount.minor })
        assertEquals(listOf(1_000, 3_000, 6_000), entries.map { it.share.value })
        assertEquals(rupiah(1_000_000), entries.map { it.amount }.fold(rupiah(0)) { a, b -> a + b })
    }

    @Test
    fun `pemasukan menambah saldo akun seluruhnya, bukan hanya bagian yang dialirkan`() {
        val f = LedgerFixture().standard()
        f.income(1_000_000)
        assertEquals(rupiah(1_500_000), f.balance())
    }

    @Test
    fun `pemasukan yang jumlahnya tidak habis dibagi tetap menjumlah tepat`() {
        val f = LedgerFixture().standard()
        val receipt = f.income(1_234_567).value()
        assertEquals(1_234_567L, entriesOf(f, receipt.transaction.id).sumOf { it.amount.minor })
    }

    @Test
    fun `nominal sangat kecil tetap menyimpan persentase semua ruang walau jumlahnya nol`() {
        val f = LedgerFixture().standard()

        val receipt = f.income(1).value()

        val entries = entriesOf(f, receipt.transaction.id)
        assertEquals(3, entries.size)
        assertEquals(listOf(1_000, 3_000, 6_000), entries.map { it.share.value })
        assertEquals(listOf(0L, 0L, 1L), entries.map { it.amount.minor })
    }

    @Test
    fun `tanpa aturan alokasi pemasukan tetap satu transaksi dan seluruhnya belum dialirkan`() {
        val f = LedgerFixture()
        runSuspend { f.setup.setUp(RoomTemplate.KOSONG, FirstAccount("Dompet", com.roziqrizal.rizqflow.domain.model.AccountKind.CASH, rupiah(0))) }

        val receipt = f.income(750_000).value()

        assertTrue(entriesOf(f, receipt.transaction.id).isEmpty())
        assertEquals(rupiah(750_000), receipt.allocation.unallocated)
        assertTrue(receipt.allocation.shares.isEmpty())
        assertEquals(rupiah(750_000), f.balance())
    }

    @Test
    fun `aturan yang totalnya di bawah 100 persen menyisakan bagian belum dialirkan`() {
        val f = LedgerFixture().standard()
        val ruangKeluarga = f.room("Keluarga")
        val ruangMemberi = f.room("Memberi")
        f.store.ruleRows = listOf(AllocationRule(ruangMemberi.id, BasisPoints.percent(10)), AllocationRule(ruangKeluarga.id, BasisPoints.percent(50)))

        val receipt = f.income(1_000_000).value()

        assertEquals(rupiah(400_000), receipt.allocation.unallocated)
        assertEquals(2, entriesOf(f, receipt.transaction.id).size)
    }

    @Test
    fun `nominal nol atau negatif ditolak dan tidak menyimpan apa pun`() {
        val f = LedgerFixture().standard()
        listOf(0L, -1L, -500_000L).forEach {
            assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, f.income(it).error(), "nominal $it")
        }
        assertTrue(f.store.transactionRows.isEmpty())
    }

    @Test
    fun `akun tidak ada, terarsip, atau beda mata uang ditolak`() {
        val f = LedgerFixture().standard()
        val arsip = f.addAccount("Lama", archived = true)

        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, f.income(1_000, accountId = AccountId("tidak-ada")).error())
        assertEquals(LedgerError.ACCOUNT_ARCHIVED, f.income(1_000, accountId = arsip.id).error())
        val usd = runSuspend { f.ledger.recordIncome(NewIncome(Money(1_000, Currency.USD), f.account.id, "Gaji", f.today)) }
        assertEquals(LedgerError.CURRENCY_MISMATCH, usd.error())
        assertTrue(f.store.transactionRows.isEmpty())
    }

    @Test
    fun `catatan dan sumber dirapikan, kosong menjadi null, terlalu panjang ditolak`() {
        val f = LedgerFixture().standard()

        val tx = f.income(1_000, source = "  Freelance ", note = "  proyek desain  ").value().transaction
        assertEquals("Freelance", tx.incomeSource)
        assertEquals("proyek desain", tx.note)

        val kosong = f.income(1_000, source = "   ", note = "").value().transaction
        assertNull(kosong.incomeSource)
        assertNull(kosong.note)

        assertEquals(LedgerError.NOTE_TOO_LONG, f.income(1_000, note = "x".repeat(201)).error())
        assertEquals(LedgerError.SOURCE_TOO_LONG, f.income(1_000, source = "x".repeat(41)).error())
        assertIs<LedgerResult.Success<IncomeReceipt>>(f.income(1_000, note = "x".repeat(200)))
    }

    @Test
    fun `asal transaksi dan waktu tersimpan`() {
        val f = LedgerFixture().standard()
        f.now = 5_000L

        val tx = runSuspend {
            f.ledger.recordIncome(NewIncome(rupiah(1_000), f.account.id, "Gaji", f.today, origin = TransactionOrigin.QUICK))
        }.value().transaction

        assertEquals(TransactionOrigin.QUICK, tx.origin)
        assertEquals(5_000L, tx.createdAtMillis)
        assertEquals(5_000L, tx.updatedAtMillis)
        assertEquals(f.today, tx.occurredOn)
    }

    @Test
    fun `galat penyimpanan merambat dan tidak meninggalkan data setengah jadi`() {
        val f = LedgerFixture().standard()
        f.store.failNextWrite = IllegalStateException("disk penuh")

        assertFailsWith<IllegalStateException> { f.income(1_000_000) }

        assertTrue(f.store.transactionRows.isEmpty() && f.store.entryRows.isEmpty())
    }

    // ------------------------------------------------------------------ pengeluaran

    private fun expense(f: LedgerFixture, amount: Long = 25_000, roomName: String = "Keluarga", cat: String = "Belanja bulanan", accountId: AccountId = f.account.id) =
        runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(amount), accountId, f.room(roomName).id, f.category(roomName, cat).id, f.today, "makan"))
        }

    @Test
    fun `pengeluaran tersimpan dengan ruang dan kategori lalu mengurangi saldo`() {
        val f = LedgerFixture().standard()

        val tx = expense(f).value()

        assertEquals(TransactionKind.EXPENSE, tx.kind)
        assertEquals(f.room("Keluarga").id, tx.roomId)
        assertEquals(f.category("Keluarga", "Belanja bulanan").id, tx.categoryId)
        assertEquals(rupiah(475_000), f.balance())
    }

    @Test
    fun `pengeluaran tidak membuat potret alokasi`() {
        val f = LedgerFixture().standard()
        expense(f)
        assertTrue(f.store.entryRows.isEmpty())
    }

    @Test
    fun `pengeluaran ke ruang yang tidak ada atau terarsip ditolak`() {
        val f = LedgerFixture().standard()
        val kategori = f.category("Keluarga", "Sekolah")

        val hilang = runSuspend { f.ledger.recordExpense(NewExpense(rupiah(1_000), f.account.id, RoomId("tidak-ada"), kategori.id, f.today)) }
        assertEquals(LedgerError.ROOM_NOT_FOUND, hilang.error())

        val keluarga = f.room("Keluarga")
        f.store.roomRows[keluarga.id] = keluarga.copy(archived = true)
        assertEquals(LedgerError.ROOM_ARCHIVED, expense(f).error())
    }

    @Test
    fun `kategori dari ruang lain, tidak ada, atau terarsip ditolak`() {
        val f = LedgerFixture().standard()
        val dariRuangLain = f.category("Diri", "Investasi")

        val salahRuang = runSuspend { f.ledger.recordExpense(NewExpense(rupiah(1_000), f.account.id, f.room("Keluarga").id, dariRuangLain.id, f.today)) }
        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, salahRuang.error())

        val hilang = runSuspend { f.ledger.recordExpense(NewExpense(rupiah(1_000), f.account.id, f.room("Keluarga").id, CategoryId("tidak-ada"), f.today)) }
        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, hilang.error())

        val sekolah = f.category("Keluarga", "Sekolah")
        f.store.categoryRows[sekolah.id] = sekolah.copy(archived = true)
        assertEquals(LedgerError.CATEGORY_NOT_IN_ROOM, expense(f, cat = "Sekolah").error())
        assertTrue(f.store.transactionRows.isEmpty())
    }

    @Test
    fun `kategori sistem Tak terlacak bisa dipakai di ruang mana pun`() {
        val f = LedgerFixture().standard()
        assertIs<LedgerResult.Success<MoneyTransaction>>(expense(f, roomName = "Diri", cat = RoomTemplates.UNTRACKED))
    }

    @Test
    fun `pengeluaran nol, akun bermasalah, dan catatan panjang ditolak`() {
        val f = LedgerFixture().standard()
        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, expense(f, amount = 0).error())
        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, expense(f, accountId = AccountId("tidak-ada")).error())
        val panjang = runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(1_000), f.account.id, f.room("Keluarga").id, f.category("Keluarga", "Sekolah").id, f.today, "x".repeat(201)))
        }
        assertEquals(LedgerError.NOTE_TOO_LONG, panjang.error())
    }

    @Test
    fun `pengeluaran boleh melebihi saldo, karena aplikasi mencatat dan tidak memblokir`() {
        val f = LedgerFixture().standard()
        assertIs<LedgerResult.Success<MoneyTransaction>>(expense(f, amount = 900_000))
        assertEquals(rupiah(-400_000), f.balance())
    }

    // ------------------------------------------------------------------ transfer

    private fun transfer(f: LedgerFixture, from: AccountId, to: AccountId, amount: Long = 100_000) =
        runSuspend { f.ledger.recordTransfer(NewTransfer(rupiah(amount), from, to, f.today)) }

    @Test
    fun `transfer memindahkan saldo antar akun tanpa mengubah total dan bukan pemasukan atau pengeluaran`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank", opening = 200_000)

        val tx = transfer(f, f.account.id, bank.id).value()

        assertEquals(TransactionKind.TRANSFER, tx.kind)
        assertEquals(rupiah(400_000), f.balance(f.account.id))
        assertEquals(rupiah(300_000), f.balance(bank.id))
        assertEquals(rupiah(700_000), f.balance(f.account.id) + f.balance(bank.id))
        assertNull(tx.roomId)
        assertTrue(f.store.entryRows.isEmpty(), "transfer tidak dialirkan")
    }

    @Test
    fun `transfer ke akun yang sama ditolak`() {
        val f = LedgerFixture().standard()
        assertEquals(LedgerError.SAME_ACCOUNT, transfer(f, f.account.id, f.account.id).error())
    }

    @Test
    fun `transfer dengan akun hilang, terarsip, atau nominal nol ditolak`() {
        val f = LedgerFixture().standard()
        val arsip = f.addAccount("Lama", archived = true)
        val bank = f.addAccount("Bank")

        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, transfer(f, f.account.id, AccountId("tidak-ada")).error())
        assertEquals(LedgerError.ACCOUNT_NOT_FOUND, transfer(f, AccountId("tidak-ada"), bank.id).error())
        assertEquals(LedgerError.ACCOUNT_ARCHIVED, transfer(f, f.account.id, arsip.id).error())
        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, transfer(f, f.account.id, bank.id, amount = 0).error())
        assertTrue(f.store.transactionRows.isEmpty())
    }

    // ------------------------------------------------------------------ ubah nominal pemasukan

    @Test
    fun `mengubah nominal menghitung ulang dengan persentase potret dan menjaga baris yang sama`() {
        val f = LedgerFixture().standard()
        val receipt = f.income(1_000_000).value()
        val idsSebelum = entriesOf(f, receipt.transaction.id).map { it.id }

        f.now = 9_000L
        val hasil = runSuspend { f.ledger.editIncomeAmount(receipt.transaction.id, rupiah(2_000_000)) }.value()

        val entries = entriesOf(f, receipt.transaction.id)
        assertEquals(listOf(200_000L, 600_000L, 1_200_000L), entries.map { it.amount.minor })
        assertEquals(idsSebelum, entries.map { it.id })
        assertEquals(rupiah(2_000_000), hasil.transaction.amount)
        assertEquals(receipt.transaction.createdAtMillis, hasil.transaction.createdAtMillis)
        assertEquals(9_000L, hasil.transaction.updatedAtMillis)
        assertEquals(rupiah(2_500_000), f.balance())
    }

    @Test
    fun `mengubah aturan alokasi tidak mengubah riwayat, dan ubah nominal tetap memakai potret lama`() {
        val f = LedgerFixture().standard()
        val receipt = f.income(1_000_000).value()
        val semua = listOf("Memberi", "Diri", "Keluarga").map { f.room(it).id }
        runSuspend {
            f.rules.changeRules(listOf(AllocationRule(semua[0], BasisPoints.percent(50)), AllocationRule(semua[1], BasisPoints.percent(25)), AllocationRule(semua[2], BasisPoints.percent(25))))
        }

        assertEquals(listOf(100_000L, 300_000L, 600_000L), entriesOf(f, receipt.transaction.id).map { it.amount.minor }, "riwayat tidak berubah")

        runSuspend { f.ledger.editIncomeAmount(receipt.transaction.id, rupiah(2_000_000)) }

        assertEquals(listOf(200_000L, 600_000L, 1_200_000L), entriesOf(f, receipt.transaction.id).map { it.amount.minor }, "tetap 10/30/60")
        val baru = f.income(1_000_000).value()
        assertEquals(listOf(500_000L, 250_000L, 250_000L), entriesOf(f, baru.transaction.id).map { it.amount.minor }, "pemasukan baru memakai aturan baru")
    }

    @Test
    fun `nominal yang awalnya terlalu kecil untuk dibagi pulih saat diubah menjadi besar`() {
        val f = LedgerFixture().standard()
        val receipt = f.income(1).value()

        runSuspend { f.ledger.editIncomeAmount(receipt.transaction.id, rupiah(1_000_000)) }

        assertEquals(listOf(100_000L, 300_000L, 600_000L), entriesOf(f, receipt.transaction.id).map { it.amount.minor })
    }

    @Test
    fun `mengubah nominal pemasukan tanpa alokasi tetap tanpa alokasi`() {
        val f = LedgerFixture()
        runSuspend { f.setup.setUp(RoomTemplate.KOSONG, FirstAccount("Dompet", com.roziqrizal.rizqflow.domain.model.AccountKind.CASH, rupiah(0))) }
        val receipt = f.income(100_000).value()

        val hasil = runSuspend { f.ledger.editIncomeAmount(receipt.transaction.id, rupiah(300_000)) }.value()

        assertTrue(entriesOf(f, receipt.transaction.id).isEmpty())
        assertEquals(rupiah(300_000), hasil.allocation.unallocated)
    }

    @Test
    fun `ubah nominal ditolak untuk transaksi hilang, bukan pemasukan, nol, atau beda mata uang`() {
        val f = LedgerFixture().standard()
        val income = f.income(1_000_000).value().transaction
        val expense = expense(f).value()

        assertEquals(LedgerError.TRANSACTION_NOT_FOUND, runSuspend { f.ledger.editIncomeAmount(TransactionId("tidak-ada"), rupiah(1)) }.error())
        assertEquals(LedgerError.NOT_AN_INCOME, runSuspend { f.ledger.editIncomeAmount(expense.id, rupiah(1)) }.error())
        assertEquals(LedgerError.AMOUNT_NOT_POSITIVE, runSuspend { f.ledger.editIncomeAmount(income.id, rupiah(0)) }.error())
        assertEquals(LedgerError.CURRENCY_MISMATCH, runSuspend { f.ledger.editIncomeAmount(income.id, Money(1_000, Currency.USD)) }.error())
        assertEquals(rupiah(1_000_000), f.store.transactionRows.getValue(income.id).amount, "tidak berubah")
    }

    // ------------------------------------------------------------------ hapus dan daftar

    @Test
    fun `menghapus pemasukan menghapus potret alokasinya dan mengembalikan saldo`() {
        val f = LedgerFixture().standard()
        val receipt = f.income(1_000_000).value()

        assertIs<LedgerResult.Success<Unit>>(runSuspend { f.ledger.delete(receipt.transaction.id) })

        assertTrue(f.store.transactionRows.isEmpty() && f.store.entryRows.isEmpty())
        assertEquals(rupiah(500_000), f.balance())
    }

    @Test
    fun `menghapus pengeluaran mengembalikan saldo`() {
        val f = LedgerFixture().standard()
        val tx = expense(f).value()
        runSuspend { f.ledger.delete(tx.id) }
        assertEquals(rupiah(500_000), f.balance())
    }

    @Test
    fun `menghapus transaksi yang tidak ada ditolak`() {
        val f = LedgerFixture().standard()
        assertEquals(LedgerError.TRANSACTION_NOT_FOUND, runSuspend { f.ledger.delete(TransactionId("tidak-ada")) }.error())
    }

    @Test
    fun `daftar transaksi antara dua hari berurutan terbaru dulu dan hanya dalam rentang`() {
        val f = LedgerFixture().standard()
        fun at(day: Int, amount: Long) = runSuspend {
            f.ledger.recordIncome(NewIncome(rupiah(amount), f.account.id, "x", LocalDate.of(2026, 9, day)))
        }.value().transaction

        val a = at(10, 1_000)
        val c = at(20, 3_000)
        f.now += 1
        val b = at(20, 2_000)
        at(30, 4_000)

        val hasil = runSuspend { f.store.between(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20)) }
        assertEquals(listOf(b.id, c.id, a.id), hasil.map { it.id })
    }

    // ------------------------------------------------------------------ invarian model

    @Test
    fun `model transaksi menolak kombinasi yang tidak masuk akal`() {
        val base = MoneyTransaction(
            id = TransactionId("t"), kind = TransactionKind.INCOME, amount = rupiah(1), accountId = AccountId("a"),
            occurredOn = LocalDate.of(2026, 9, 21), createdAtMillis = 0, updatedAtMillis = 0,
        )
        assertFailsWith<IllegalArgumentException> { base.copy(amount = rupiah(0)) }
        assertFailsWith<IllegalArgumentException> { base.copy(roomId = RoomId("r")) }
        assertFailsWith<IllegalArgumentException> { base.copy(kind = TransactionKind.EXPENSE) }
        assertFailsWith<IllegalArgumentException> { base.copy(kind = TransactionKind.EXPENSE, roomId = RoomId("r")) }
        assertFailsWith<IllegalArgumentException> { base.copy(kind = TransactionKind.TRANSFER) }
        assertFailsWith<IllegalArgumentException> { base.copy(kind = TransactionKind.TRANSFER, toAccountId = AccountId("a")) }
        assertFailsWith<IllegalArgumentException> { base.copy(kind = TransactionKind.TRANSFER, toAccountId = AccountId("b"), roomId = RoomId("r")) }
    }

    @Test
    fun `model akun, ruang, dan kategori menolak nama tidak sah`() {
        assertFailsWith<IllegalArgumentException> { Account(AccountId("a"), " ", com.roziqrizal.rizqflow.domain.model.AccountKind.CASH, rupiah(0)) }
        assertFailsWith<IllegalArgumentException> { Account(AccountId("a"), "x", com.roziqrizal.rizqflow.domain.model.AccountKind.CASH, Money(-1)) }
        assertFailsWith<IllegalArgumentException> { Room(RoomId("r"), "x".repeat(25), com.roziqrizal.rizqflow.domain.model.RoomKind.MENCUKUPI, "home", 1, 0) }
        assertFailsWith<IllegalArgumentException> { Room(RoomId("r"), "ok", com.roziqrizal.rizqflow.domain.model.RoomKind.MENCUKUPI, "home", 0, 0) }
        assertFailsWith<IllegalArgumentException> { Category(CategoryId("c"), RoomId("r"), "") }
        assertFailsWith<IllegalArgumentException> { Category(CategoryId("c"), RoomId("r"), "ok", weight = 0) }
    }
}
