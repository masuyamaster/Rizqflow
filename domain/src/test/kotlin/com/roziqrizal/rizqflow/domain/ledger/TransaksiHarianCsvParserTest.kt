package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransaksiHarianCsvParserTest {

    @Test
    fun `mengurai baris lewat nama header apa pun urutannya`() {
        val csv = "Tipe,Tanggal,Akun,Kategori,Jumlah,Catatan\nPengeluaran,2026-09-20,Tunai,Makan,25000,Kopi pagi"

        val rows = TransaksiHarianCsvParser.parse(csv)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(2, row.lineNumber)
        assertEquals(LocalDate.of(2026, 9, 20), row.date)
        assertEquals(Money.rupiah(25_000), row.amount)
        assertEquals(ImportKind.EXPENSE, row.kind)
        assertEquals("Tunai", row.accountName)
        assertEquals("Makan", row.categoryName)
        assertEquals("Kopi pagi", row.note)
        assertNull(row.parseError)
    }

    @Test
    fun `kolom tambahan dari Notion diabaikan`() {
        val csv = "Deskripsi,Bulan,Klasifikasi,Tipe,Tanggal,Akun,Jumlah\nGaji,Sep,Pendapatan,Pemasukan,2026-09-01,Bank,5000000"

        val row = TransaksiHarianCsvParser.parse(csv).single()

        assertEquals(ImportKind.INCOME, row.kind)
        assertEquals(Money.rupiah(5_000_000), row.amount)
        assertEquals("Gaji", row.note, "Catatan kosong jatuh balik ke Deskripsi")
    }

    @Test
    fun `format tanggal ala Notion bisa diuraikan`() {
        val csv = "Tanggal,Tipe,Akun,Jumlah\n\"September 20, 2026\",Pengeluaran,Tunai,10000"

        val row = TransaksiHarianCsvParser.parse(csv).single()

        assertEquals(LocalDate.of(2026, 9, 20), row.date)
    }

    @Test
    fun `nominal dengan pemisah ribuan dibersihkan dulu`() {
        val csv = "Tanggal,Tipe,Akun,Jumlah\n2026-09-20,Pengeluaran,Tunai,\"Rp10.000\""

        val row = TransaksiHarianCsvParser.parse(csv).single()

        assertEquals(Money.rupiah(10_000), row.amount)
    }

    @Test
    fun `baris kosong dilewati bukan dianggap galat`() {
        val csv = "Tanggal,Tipe,Akun,Jumlah\n2026-09-20,Pengeluaran,Tunai,10000\n,,,\n"

        assertEquals(1, TransaksiHarianCsvParser.parse(csv).size)
    }

    @Test
    fun `tanggal kosong nominal kosong dan tipe tak dikenal masing-masing punya alasan sendiri`() {
        val csv = "Tanggal,Tipe,Akun,Jumlah\n" +
            ",Pengeluaran,Tunai,10000\n" +
            "2026-09-20,Pengeluaran,Tunai,\n" +
            "2026-09-20,Netral,Tunai,10000\n" +
            "tanggal aneh,Pengeluaran,Tunai,10000"

        val rows = TransaksiHarianCsvParser.parse(csv)

        assertEquals("Tanggal kosong", rows[0].parseError)
        assertEquals("Nominal kosong", rows[1].parseError)
        assertEquals("Tipe bukan Pemasukan atau Pengeluaran", rows[2].parseError)
        assertEquals("Tanggal tidak bisa diuraikan: tanggal aneh", rows[3].parseError)
    }

    @Test
    fun `hanya header tidak menghasilkan baris`() {
        assertEquals(emptyList(), TransaksiHarianCsvParser.parse("Tanggal,Tipe,Akun,Jumlah"))
    }

    @Test
    fun `csv kosong tidak menghasilkan baris`() {
        assertEquals(emptyList(), TransaksiHarianCsvParser.parse(""))
    }
}
