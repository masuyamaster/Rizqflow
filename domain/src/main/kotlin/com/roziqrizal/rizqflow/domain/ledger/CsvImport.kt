package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

enum class ImportKind { INCOME, EXPENSE }

/** Satu baris mentah dari CSV Transaksi Harian, sebelum dicocokkan ke akun/kategori aplikasi. */
data class ImportRow(
    val lineNumber: Int,
    val date: LocalDate?,
    val amount: Money?,
    val kind: ImportKind?,
    val accountName: String,
    val categoryName: String?,
    val note: String?,
    /** Alasan baris ini tidak bisa diuraikan sama sekali; null berarti siap dicocokkan ke akun/kategori. */
    val parseError: String? = null,
)

/**
 * Mengurai CSV ekspor Notion "Transaksi Harian" (impor CSV, Tahap 6): kolom dicari lewat nama
 * header (tidak peduli urutan atau kolom tambahan), cocok dengan skema aslinya (Deskripsi,
 * Tanggal, Jumlah, Tipe, Akun, Kategori, Catatan). Transaksi Harian tidak punya jenis "Transfer";
 * pasangan pemasukan/pengeluaran yang cocok disatukan belakangan oleh [CsvImportPreviewBuilder].
 */
object TransaksiHarianCsvParser {
    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
    )

    fun parse(csv: String): List<ImportRow> {
        val table = CsvReader.read(csv)
        if (table.isEmpty()) return emptyList()
        val header = table.first().mapIndexed { i, h -> h.trim() to i }.toMap()

        fun column(row: List<String>, name: String): String? = header[name]?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

        return table.drop(1).mapIndexedNotNull { i, row ->
            if (row.all { it.isBlank() }) return@mapIndexedNotNull null
            val lineNumber = i + 2 // baris 1 adalah header

            val dateText = column(row, "Tanggal")
            val date = dateText?.let(::parseDate)
            val amountText = column(row, "Jumlah")
            val amount = amountText?.let(::parseAmount)
            val kind = when (column(row, "Tipe")) {
                "Pemasukan" -> ImportKind.INCOME
                "Pengeluaran" -> ImportKind.EXPENSE
                else -> null
            }
            val accountName = column(row, "Akun").orEmpty()

            val error = when {
                dateText == null -> "Tanggal kosong"
                date == null -> "Tanggal tidak bisa diuraikan: $dateText"
                amountText == null -> "Nominal kosong"
                amount == null -> "Nominal tidak bisa diuraikan: $amountText"
                kind == null -> "Tipe bukan Pemasukan atau Pengeluaran"
                accountName.isEmpty() -> "Akun kosong"
                else -> null
            }

            ImportRow(
                lineNumber = lineNumber,
                date = date,
                amount = amount,
                kind = kind,
                accountName = accountName,
                categoryName = column(row, "Kategori"),
                note = column(row, "Catatan") ?: column(row, "Deskripsi"),
                parseError = error,
            )
        }
    }

    private fun parseDate(text: String): LocalDate? {
        for (format in DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format)
            } catch (_: DateTimeParseException) {
                // coba format berikutnya
            }
        }
        return null
    }

    private fun parseAmount(text: String): Money? {
        val digits = text.filter { it.isDigit() }
        val value = digits.toLongOrNull() ?: return null
        return if (value <= 0) null else Money.rupiah(value)
    }
}
