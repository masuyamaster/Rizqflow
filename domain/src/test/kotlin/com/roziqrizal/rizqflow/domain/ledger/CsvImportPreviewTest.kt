package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CsvImportPreviewTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    private fun row(
        line: Int,
        date: LocalDate? = LocalDate.of(2026, 9, 20),
        amount: Money? = rupiah(10_000),
        kind: ImportKind? = ImportKind.EXPENSE,
        account: String = "Dompet",
        category: String? = "Investasi",
        note: String? = null,
        error: String? = null,
    ) = ImportRow(line, date, amount, kind, account, category, note, error)

    // ------------------------------------------------------------------ pratinjau: mencocokkan nama

    @Test
    fun `pengeluaran dengan akun dan kategori yang cocok siap diimpor`() {
        val f = LedgerFixture().standard()
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())

        val preview = builder.build(listOf(row(2)))

        val expense = assertIs<ImportExpense>(preview.candidates.single())
        assertEquals(listOf(2), expense.lines)
        assertEquals(rupiah(10_000), expense.expense.amount)
        assertEquals(f.account.id, expense.expense.accountId)
        assertEquals(f.category("Diri", "Investasi").id, expense.expense.categoryId)
        assertEquals(TransactionOrigin.IMPORT, expense.expense.origin)
    }

    @Test
    fun `pemasukan tidak butuh kategori`() {
        val f = LedgerFixture().standard()
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())

        val preview = builder.build(listOf(row(2, kind = ImportKind.INCOME, category = null)))

        val income = assertIs<ImportIncome>(preview.candidates.single())
        assertEquals(rupiah(10_000), income.income.amount)
    }

    @Test
    fun `pencocokan nama akun dan kategori tidak peduli huruf besar kecil`() {
        val f = LedgerFixture().standard()
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())

        val preview = builder.build(listOf(row(2, account = "DOMPET", category = "investasi")))

        assertIs<ImportExpense>(preview.candidates.single())
    }

    @Test
    fun `akun yang tidak ditemukan dilewati dengan alasan`() {
        val f = LedgerFixture().standard()
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())

        val preview = builder.build(listOf(row(2, account = "Rekening Hilang")))

        val skipped = assertIs<ImportSkipped>(preview.candidates.single())
        assertEquals("Akun tidak ditemukan: Rekening Hilang", skipped.reason)
    }

    @Test
    fun `kategori yang tidak ditemukan dilewati dengan alasan`() {
        val f = LedgerFixture().standard()
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())

        val preview = builder.build(listOf(row(2, category = "Kategori Hilang")))

        val skipped = assertIs<ImportSkipped>(preview.candidates.single())
        assertEquals("Kategori tidak ditemukan: Kategori Hilang", skipped.reason)
    }

    @Test
    fun `baris dengan galat penguraian dilewati apa adanya`() {
        val f = LedgerFixture().standard()
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())

        val preview = builder.build(listOf(row(2, error = "Tanggal kosong")))

        val skipped = assertIs<ImportSkipped>(preview.candidates.single())
        assertEquals("Tanggal kosong", skipped.reason)
    }

    @Test
    fun `akun terarsip tidak dianggap cocok`() {
        val f = LedgerFixture().standard()
        val arsip = f.addAccount("Lama", archived = true)
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())

        val preview = builder.build(listOf(row(2, account = "Lama")))

        assertIs<ImportSkipped>(preview.candidates.single())
    }

    // ------------------------------------------------------------------ menyatukan pasangan transfer

    @Test
    fun `pemasukan dan pengeluaran dengan tanggal nominal cocok di akun beda disatukan jadi transfer`() {
        val f = LedgerFixture().standard()
        val bank = f.addAccount("Bank")
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())
        val rows = listOf(
            row(2, kind = ImportKind.EXPENSE, account = "Dompet", category = "Investasi"),
            row(3, kind = ImportKind.INCOME, account = "Bank", category = null),
        )

        val preview = builder.build(rows)

        val transfer = assertIs<ImportTransfer>(preview.candidates.single())
        assertEquals(listOf(2, 3), transfer.lines)
        assertEquals(f.account.id, transfer.transfer.from)
        assertEquals(bank.id, transfer.transfer.to)
        assertEquals(rupiah(10_000), transfer.transfer.amount)
    }

    @Test
    fun `pemasukan dan pengeluaran akun sama tidak disatukan`() {
        val f = LedgerFixture().standard()
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())
        val rows = listOf(
            row(2, kind = ImportKind.EXPENSE, account = "Dompet", category = "Investasi"),
            row(3, kind = ImportKind.INCOME, account = "Dompet", category = null),
        )

        val preview = builder.build(rows)

        assertEquals(2, preview.candidates.size)
        assertTrue(preview.candidates.none { it is ImportTransfer })
    }

    @Test
    fun `pemasukan dan pengeluaran tanggal berbeda tidak disatukan`() {
        val f = LedgerFixture().standard()
        f.addAccount("Bank")
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())
        val rows = listOf(
            row(2, date = LocalDate.of(2026, 9, 20), kind = ImportKind.EXPENSE, account = "Dompet", category = "Investasi"),
            row(3, date = LocalDate.of(2026, 9, 21), kind = ImportKind.INCOME, account = "Bank", category = null),
        )

        val preview = builder.build(rows)

        assertTrue(preview.candidates.none { it is ImportTransfer })
    }

    // ------------------------------------------------------------------ mengomit ke buku kas

    @Test
    fun `mengomit menyimpan yang siap dan menghitung yang dilewati`() {
        val f = LedgerFixture().standard()
        val builder = CsvImportPreviewBuilder(f.store.accountRows.values.toList(), f.store.categoryRows.values.toList())
        val preview = builder.build(
            listOf(
                row(2, kind = ImportKind.EXPENSE, account = "Dompet", category = "Investasi"),
                row(3, account = "Rekening Hilang"),
            ),
        )
        val service = CsvImportService(f.ledger)

        val result = runSuspend { service.commit(preview) }

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertTrue(result.failures.isEmpty())
        assertEquals(1, f.store.transactionRows.size)
    }
}
