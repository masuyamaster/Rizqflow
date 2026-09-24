package com.roziqrizal.rizqflow.domain.smartinput

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.ledger.Account
import com.roziqrizal.rizqflow.domain.ledger.CatatContext
import com.roziqrizal.rizqflow.domain.ledger.CatatContextLoader
import com.roziqrizal.rizqflow.domain.ledger.CatatMode
import com.roziqrizal.rizqflow.domain.ledger.Category
import com.roziqrizal.rizqflow.domain.ledger.LedgerFixture
import com.roziqrizal.rizqflow.domain.ledger.NewExpense
import com.roziqrizal.rizqflow.domain.ledger.Room
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden test parser input cerdas: satu baris contoh nyata per perilaku. Hari ini dibekukan pada
 * Kamis 24 September 2026 supaya semua tanggal relatif bisa dihitung tangan.
 */
class SmartInputParserTest {

    private val today = LocalDate.of(2026, 9, 24)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private val tunai = Account(AccountId("a-tunai"), "Tunai", AccountKind.CASH, Money.rupiah(0), sortOrder = 0)
    private val bca = Account(AccountId("a-bca"), "BCA Utama", AccountKind.BANK, Money.rupiah(0), sortOrder = 1)
    private val gopay = Account(AccountId("a-gopay"), "GoPay", AccountKind.EWALLET, Money.rupiah(0), sortOrder = 2)
    private val ovo = Account(AccountId("a-ovo"), "OVO", AccountKind.EWALLET, Money.rupiah(0), sortOrder = 3)
    private val accounts = listOf(tunai, bca, gopay, ovo)

    private val memberi = Room(RoomId("r-memberi"), "Memberi", RoomKind.MENUNAIKAN, "heart", 1, 0)
    private val diri = Room(RoomId("r-diri"), "Diri", RoomKind.MENUMBUHKAN, "sprout", 2, 1)
    private val keluarga = Room(RoomId("r-keluarga"), "Keluarga", RoomKind.MENCUKUPI, "home", 3, 2)
    private val rooms = listOf(memberi, diri, keluarga)

    private fun cat(id: String, room: Room, name: String, system: Boolean = false, archived: Boolean = false) =
        Category(CategoryId(id), room.id, name, isSystem = system, archived = archived)

    private val sedekah = cat("c-sedekah", memberi, "Sedekah")
    private val zakatMal = cat("c-zakat", memberi, "Zakat mal", system = true)
    private val hiburan = cat("c-hiburan", diri, "Hiburan")
    private val belanja = cat("c-belanja", keluarga, "Belanja bulanan")
    private val listrik = cat("c-listrik", keluarga, "Listrik dan air")
    private val sekolah = cat("c-sekolah", keluarga, "Sekolah")
    private val transport = cat("c-transport", keluarga, "Transportasi")
    private val makanLuar = cat("c-makan", keluarga, "Makan luar")
    private val lama = cat("c-lama", keluarga, "Langganan", archived = true)
    private val categories = mapOf(
        memberi.id to listOf(sedekah, zakatMal),
        diri.id to listOf(hiburan),
        keluarga.id to listOf(belanja, listrik, sekolah, transport, makanLuar, lama),
    )

    private val history = listOf(HistoryHint("bakso mie ayam", keluarga.id, makanLuar.id), HistoryHint("nonton bioskop", diri.id, hiburan.id))

    private fun context(withHistory: Boolean = true, accountList: List<Account> = accounts) =
        SmartInputContext(accountList, rooms, categories, if (withHistory) history else emptyList(), today)

    private fun parse(text: String, withHistory: Boolean = true) = SmartInputParser.parse(text, context(withHistory))

    private fun entry(text: String, withHistory: Boolean = true): ParsedEntry = assertNotNull(parse(text, withHistory), "tidak terbaca: $text")

    private fun rupiah(n: Long) = Money.rupiah(n)

    // ---------------------------------------------------------------------------------------------- nominal

    @Test
    fun `berbagai penulisan nominal menghasilkan angka yang sama`() {
        val expected = rupiah(25_000)
        for (text in listOf("kopi 25rb", "kopi 25000", "kopi 25.000", "kopi 25k", "kopi 25 rb", "kopi 25 ribu", "kopi Rp 25.000", "kopi rp25.000", "kopi Rp25000", "kopi 25RB", "kopi rp. 25.000")) {
            val e = entry(text)
            assertEquals(expected, e.amount, text)
            assertEquals("kopi", e.note, text)
        }
    }

    @Test
    fun `nominal dengan pecahan dan juta`() {
        assertEquals(rupiah(1_500_000), entry("servis motor 1,5jt").amount)
        assertEquals(rupiah(1_500_000), entry("servis motor 1.5jt").amount)
        assertEquals(rupiah(1_500_000), entry("servis motor 1,5 juta").amount)
        assertEquals(rupiah(2_500), entry("permen 2,5rb").amount)
        assertEquals(rupiah(1_250_000), entry("laptop 1,25jt").amount)
        assertEquals(rupiah(500_000), entry("bensin 0,5jt").amount)
        assertEquals(rupiah(8_000_000), entry("gaji 8jt").amount)
        assertEquals(rupiah(1_250_000), entry("sewa 1.250.000").amount)
    }

    @Test
    fun `nominal kecil tanpa satuan tetap terbaca bila hanya itu angkanya`() {
        assertEquals(rupiah(500), entry("parkir 500").amount)
        assertEquals(rupiah(3_000), entry("parkir 3rb").amount)
    }

    @Test
    fun `nominal bersatuan dipilih mengalahkan jumlah barang`() {
        val e = entry("beli 2 kopi 50rb")
        assertEquals(rupiah(50_000), e.amount)
        assertEquals("2 kopi", e.note)
        assertEquals(rupiah(50_000), entry("beli 2 kopi 50000").amount)
        assertEquals("servis 2 motor", entry("servis 2 motor 45rb").note)
        assertEquals(rupiah(45_000), entry("servis 2 motor 45rb").amount)
    }

    @Test
    fun `tanpa nominal atau nominal nol tidak terbaca`() {
        assertNull(parse("kopi"))
        assertNull(parse(""))
        assertNull(parse("    "))
        assertNull(parse("kopi 0rb"))
        assertNull(parse("kopi 0"))
        assertNull(parse("kopi 25,5"))
    }

    @Test
    fun `hanya nominal menghasilkan pengeluaran tanpa catatan`() {
        val e = entry("25rb")
        assertEquals(TransactionKind.EXPENSE, e.kind)
        assertEquals("", e.note)
        assertEquals(rupiah(25_000), e.amount)
        assertEquals(setOf(SmartField.AMOUNT), e.understood)
    }

    @Test
    fun `nominal terlalu panjang ditolak tanpa meledak`() {
        assertNull(parse("kopi 99999999999999999999rb"))
    }

    // ---------------------------------------------------------------------------------------------- jenis

    @Test
    fun `kata penerimaan dan kata benda pemasukan menghasilkan pemasukan`() {
        for ((text, note) in listOf(
            "gaji masuk 8jt" to "gaji",
            "terima gaji 8jt" to "gaji",
            "cair gaji 8jt" to "gaji",
            "dapat thr 1jt" to "thr",
            "bonus 2jt" to "bonus",
            "gajian 5jt" to "gajian",
            "dikasih ibu 200rb" to "ibu",
        )) {
            val e = entry(text)
            assertEquals(TransactionKind.INCOME, e.kind, text)
            assertEquals(note, e.note, text)
            assertTrue(SmartField.KIND in e.understood, text)
        }
    }

    @Test
    fun `sumber pemasukan dikenali dari kata gaji dan usaha`() {
        assertEquals("Gaji", entry("gaji masuk 8jt").source)
        assertEquals("Gaji", entry("gajian 5jt").source)
        assertEquals("Usaha", entry("jualan masuk 300rb").source)
        assertEquals("Usaha", entry("omzet 1jt").source)
        assertNull(entry("dapat thr 1jt").source)
        assertTrue(SmartField.SOURCE in entry("gaji masuk 8jt").understood)
        assertFalse(SmartField.SOURCE in entry("dapat thr 1jt").understood)
    }

    @Test
    fun `kata kerja pengeluaran mengalahkan kata benda pemasukan`() {
        val e = entry("bayar gaji karyawan 5jt")
        assertEquals(TransactionKind.EXPENSE, e.kind)
        assertEquals("gaji karyawan", e.note)
    }

    @Test
    fun `masuk sebagai tiket bukan pemasukan`() {
        val e = entry("tiket masuk museum 50rb")
        assertEquals(TransactionKind.EXPENSE, e.kind)
        assertEquals("tiket masuk museum", e.note)
        assertEquals(TransactionKind.EXPENSE, entry("parkir masuk mall 5rb").kind)
    }

    @Test
    fun `uang masuk dari orang adalah pemasukan`() {
        val e = entry("uang masuk 500rb dari ibu")
        assertEquals(TransactionKind.INCOME, e.kind)
        assertEquals("uang dari ibu", e.note)
    }

    @Test
    fun `kata kerja beli menandai jenis dikenali tetapi tanpa kata kunci hanya bawaan`() {
        assertTrue(SmartField.KIND in entry("beli kopi 25rb").understood)
        assertFalse(SmartField.KIND in entry("kopi 25rb").understood)
        assertEquals("kopi", entry("beli kopi 25rb").note)
        assertEquals("kopi", entry("jajan kopi 25rb").note)
    }

    // ---------------------------------------------------------------------------------------------- akun

    @Test
    fun `akun disebut dengan kata depan atau tanpa`() {
        for (text in listOf("gojek 23rb dari gopay", "gojek 23rb pakai gopay", "gojek 23rb pake gopay", "gojek 23rb via gopay", "gojek 23rb lewat gopay", "gojek 23rb gopay", "gojek gopay 23rb")) {
            val e = entry(text)
            assertEquals(TransactionKind.EXPENSE, e.kind, text)
            assertEquals(gopay.id, e.accountId, text)
            assertEquals("gojek", e.note, text)
            assertTrue(SmartField.ACCOUNT in e.understood, text)
        }
    }

    @Test
    fun `nama akun tanpa memandang huruf besar dan sebagian nama yang unik`() {
        assertEquals(gopay.id, entry("gojek 23rb dari GOPAY").accountId)
        assertEquals(bca.id, entry("servis 500rb dari bca").accountId)
        assertEquals(bca.id, entry("servis 500rb dari bca utama").accountId)
        assertEquals("servis", entry("servis 500rb dari bca utama").note)
        assertEquals(ovo.id, entry("kopi 20rb ovo").accountId)
    }

    @Test
    fun `kata tunai dan cash menunjuk akun bertipe tunai`() {
        assertEquals(tunai.id, entry("servis 35rb tunai").accountId)
        assertEquals(tunai.id, entry("servis 35rb cash").accountId)
        assertEquals(tunai.id, entry("servis 35rb pakai cash").accountId)
        assertEquals("servis", entry("servis 35rb pakai cash").note)
    }

    @Test
    fun `alias tunai tidak dipakai bila tidak ada akun tunai`() {
        val e = SmartInputParser.parse("servis 35rb cash", context(accountList = listOf(bca, gopay)))!!
        assertNull(e.accountId)
        assertEquals("servis cash", e.note)
    }

    @Test
    fun `akun yang tidak dikenal tetap bagian catatan`() {
        val e = entry("servis 35rb dari mandiri")
        assertNull(e.accountId)
        assertEquals("servis dari mandiri", e.note)
        assertFalse(SmartField.ACCOUNT in e.understood)
    }

    @Test
    fun `pemasukan ke akun tertentu`() {
        val e = entry("gaji masuk 8jt ke bca")
        assertEquals(TransactionKind.INCOME, e.kind)
        assertEquals(bca.id, e.accountId)
        assertEquals("gaji", e.note)
    }

    // ---------------------------------------------------------------------------------------------- transfer

    @Test
    fun `transfer antar dua akun dengan berbagai urutan kata`() {
        for (text in listOf("tf bca ke gopay 100rb", "transfer ke gopay 100rb dari bca", "transfer dari bca ke gopay 100rb", "transfer bca ke gopay 100rb", "tf 100rb dari bca ke gopay")) {
            val e = entry(text)
            assertEquals(TransactionKind.TRANSFER, e.kind, text)
            assertEquals(bca.id, e.accountId, text)
            assertEquals(gopay.id, e.toAccountId, text)
            assertEquals(rupiah(100_000), e.amount, text)
            assertEquals("", e.note, text)
            assertTrue(SmartField.ACCOUNT in e.understood && SmartField.TO_ACCOUNT in e.understood, text)
            assertNull(e.roomId, text)
        }
    }

    @Test
    fun `top up satu akun berarti tujuannya`() {
        val e = entry("topup gopay 100rb")
        assertEquals(TransactionKind.TRANSFER, e.kind)
        assertNull(e.accountId)
        assertEquals(gopay.id, e.toAccountId)
        assertFalse(SmartField.ACCOUNT in e.understood)
        assertTrue(SmartField.TO_ACCOUNT in e.understood)
        assertEquals("", e.note)
    }

    @Test
    fun `top up dua kata dengan sumber`() {
        val e = entry("top up gopay 100rb dari bca")
        assertEquals(TransactionKind.TRANSFER, e.kind)
        assertEquals(bca.id, e.accountId)
        assertEquals(gopay.id, e.toAccountId)
        assertEquals("", e.note)
    }

    @Test
    fun `tarik tunai dari bank menuju akun tunai`() {
        val e = entry("tarik tunai 500rb dari bca")
        assertEquals(TransactionKind.TRANSFER, e.kind)
        assertEquals(bca.id, e.accountId)
        assertEquals(tunai.id, e.toAccountId)
        assertEquals(rupiah(500_000), e.amount)
    }

    @Test
    fun `pindah dari satu akun ke akun lain`() {
        val e = entry("pindah dari bca ke tunai 200rb")
        assertEquals(TransactionKind.TRANSFER, e.kind)
        assertEquals(bca.id, e.accountId)
        assertEquals(tunai.id, e.toAccountId)
    }

    @Test
    fun `transfer ke orang bukan akun tetap pengeluaran dengan catatan lengkap`() {
        val e = entry("transfer ke ibu 500rb")
        assertEquals(TransactionKind.EXPENSE, e.kind)
        assertEquals("transfer ke ibu", e.note)
        val one = entry("tf gopay 100rb")
        assertEquals(TransactionKind.EXPENSE, one.kind)
        assertEquals(gopay.id, one.accountId)
    }

    @Test
    fun `transfer ke akun yang sama tidak menghasilkan akun tujuan`() {
        val e = entry("pindah dari gopay ke gopay 100rb")
        assertEquals(TransactionKind.TRANSFER, e.kind)
        assertEquals(gopay.id, e.accountId)
        assertNull(e.toAccountId)
    }

    // ---------------------------------------------------------------------------------------------- tanggal

    @Test
    fun `tanggal relatif`() {
        val cases = listOf(
            "kopi 25rb kemarin" to d(2026, 9, 23),
            "kemarin kopi 25rb" to d(2026, 9, 23),
            "kopi 25rb kemaren" to d(2026, 9, 23),
            "kopi 25rb kemarin lusa" to d(2026, 9, 22),
            "kopi 25rb tadi" to d(2026, 9, 24),
            "kopi 25rb tadi pagi" to d(2026, 9, 24),
            "kopi 25rb tadi siang" to d(2026, 9, 24),
            "kopi 25rb tadi malam" to d(2026, 9, 23),
            "kopi 25rb semalam" to d(2026, 9, 23),
            "kopi 25rb hari ini" to d(2026, 9, 24),
            "kopi 25rb 3 hari lalu" to d(2026, 9, 21),
            "kopi 25rb 3 hari yang lalu" to d(2026, 9, 21),
            "kopi 25rb seminggu lalu" to d(2026, 9, 17),
        )
        for ((text, expected) in cases) {
            val e = entry(text)
            assertEquals(expected, e.date, text)
            assertEquals("kopi", e.note, text)
            assertEquals(rupiah(25_000), e.amount, text)
            assertTrue(SmartField.DATE in e.understood, text)
        }
    }

    @Test
    fun `tanpa kata tanggal memakai hari ini dan tidak menandai tanggal dikenali`() {
        val e = entry("kopi 25rb")
        assertEquals(today, e.date)
        assertFalse(SmartField.DATE in e.understood)
    }

    @Test
    fun `nama hari mengarah ke hari itu yang terakhir`() {
        assertEquals(d(2026, 9, 21), entry("kopi 25rb hari senin").date)
        assertEquals(d(2026, 9, 22), entry("kopi 25rb hari selasa").date)
        assertEquals(d(2026, 9, 24), entry("kopi 25rb hari kamis").date)
        assertEquals(d(2026, 9, 23), entry("kopi 25rb hari rabu").date)
        assertEquals(d(2026, 9, 19), entry("kopi 25rb hari sabtu").date)
        assertEquals(d(2026, 9, 20), entry("kopi 25rb hari minggu").date)
    }

    @Test
    fun `tanggal kalender dengan tanggal dan bulan`() {
        assertEquals(d(2026, 9, 20), entry("kopi 25rb tgl 20").date)
        assertEquals(d(2026, 9, 20), entry("kopi 25rb tanggal 20").date)
        assertEquals(d(2026, 8, 27), entry("kopi 25rb tgl 27").date)
        assertEquals(d(2026, 9, 20), entry("kopi 25rb 20/9").date)
        assertEquals(d(2026, 9, 20), entry("kopi 25rb 20-09-2026").date)
        assertEquals(d(2026, 9, 20), entry("kopi 25rb 20/9/26").date)
        assertEquals(d(2025, 12, 25), entry("kopi 25rb 25/12").date)
        assertEquals("kopi", entry("kopi 25rb 20/9").note)
    }

    @Test
    fun `tanggal yang tidak ada atau di masa depan tidak dipakai`() {
        val invalid = entry("kopi 25rb 31/2")
        assertEquals(today, invalid.date)
        assertEquals("kopi 31/2", invalid.note)
        assertEquals(today, entry("kopi 25rb 25/12/2026").date)
        assertEquals(today, entry("kopi 25rb tgl 45").date)
        assertEquals("kopi tgl 45", entry("kopi 25rb tgl 45").note)
    }

    @Test
    fun `tanggal tidak dianggap nominal`() {
        val e = entry("kopi 20/9 25rb")
        assertEquals(rupiah(25_000), e.amount)
        assertEquals(d(2026, 9, 20), e.date)
        val e2 = entry("kopi tgl 20 25000")
        assertEquals(rupiah(25_000), e2.amount)
        assertEquals(d(2026, 9, 20), e2.date)
        val e3 = entry("kopi 3 hari lalu 25000")
        assertEquals(rupiah(25_000), e3.amount)
        assertEquals(d(2026, 9, 21), e3.date)
    }

    // ---------------------------------------------------------------------------------------------- kategori

    @Test
    fun `kategori dikenali dari kata dalam namanya`() {
        for ((text, category) in listOf(
            "bayar listrik 350rb" to listrik,
            "listrik dan air 350rb" to listrik,
            "bayar sekolah 500rb" to sekolah,
            "belanja bulanan 500rb" to belanja,
            "belanja 300rb" to belanja,
            "transportasi 20rb" to transport,
            "sedekah 20rb" to sedekah,
            "makan luar 80rb" to makanLuar,
        )) {
            val e = entry(text, withHistory = false)
            assertEquals(category.id, e.categoryId, text)
            assertEquals(category.roomId, e.roomId, text)
            assertTrue(SmartField.CATEGORY in e.understood, text)
        }
    }

    @Test
    fun `kategori dipilih yang paling banyak katanya cocok`() {
        val e = entry("belanja bulanan 500rb", withHistory = false)
        assertEquals(belanja.id, e.categoryId)
    }

    @Test
    fun `kategori sistem dan yang diarsipkan tidak pernah ditebak`() {
        assertNull(entry("zakat mal 100rb", withHistory = false).categoryId)
        assertNull(entry("langganan 50rb", withHistory = false).categoryId)
    }

    @Test
    fun `nama ruang menentukan ruang tanpa kategori`() {
        val e = entry("jajan keluarga 50rb", withHistory = false)
        assertEquals(keluarga.id, e.roomId)
        assertNull(e.categoryId)
        assertTrue(SmartField.ROOM in e.understood)
        assertFalse(SmartField.CATEGORY in e.understood)
    }

    @Test
    fun `riwayat pengeluaran menebak kategori dari kata yang pernah dipakai`() {
        val e = entry("bakso 25rb")
        assertEquals(makanLuar.id, e.categoryId)
        assertEquals(keluarga.id, e.roomId)
        assertEquals(diri.id, entry("nonton 50rb").roomId)
        assertEquals(hiburan.id, entry("nonton 50rb").categoryId)
    }

    @Test
    fun `tanpa riwayat dan tanpa nama kategori tidak menebak`() {
        val e = entry("bakso 25rb", withHistory = false)
        assertNull(e.roomId)
        assertNull(e.categoryId)
        assertTrue(SmartField.CATEGORY !in e.understood && SmartField.ROOM !in e.understood)
    }

    @Test
    fun `nama kategori mengalahkan riwayat`() {
        val e = SmartInputParser.parse("bakso sekolah 25rb", SmartInputContext(accounts, rooms, categories, history, today))!!
        assertEquals(sekolah.id, e.categoryId)
    }

    @Test
    fun `pemasukan dan transfer tidak memilih ruang`() {
        assertNull(entry("gaji masuk sekolah 8jt").roomId)
        assertNull(entry("tf bca ke gopay 100rb").categoryId)
    }

    // ---------------------------------------------------------------------------------------------- catatan

    @Test
    fun `catatan mempertahankan huruf asli dan merapikan spasi dan tanda baca`() {
        assertEquals("Servis Motor", entry("Servis Motor 35K").note)
        assertEquals(rupiah(35_000), entry("Servis Motor 35K").amount)
        assertEquals("kopi", entry("  kopi   25rb  ").note)
        assertEquals("kopi", entry("kopi, 25rb").note)
        assertEquals("kopi", entry("untuk kopi 25rb").note)
        assertEquals("kopi susu", entry("kopi susu 25rb").note)
    }

    @Test
    fun `contoh riset gojek dari gopay`() {
        val e = entry("gojek 23rb dari gopay")
        assertEquals(TransactionKind.EXPENSE, e.kind)
        assertEquals(rupiah(23_000), e.amount)
        assertEquals(gopay.id, e.accountId)
        assertEquals("gojek", e.note)
        assertEquals(setOf(SmartField.AMOUNT, SmartField.ACCOUNT), e.understood)
    }

    @Test
    fun `contoh riset gaji masuk dan bakso`() {
        val gaji = entry("gaji masuk 8jt")
        assertEquals(TransactionKind.INCOME, gaji.kind)
        assertEquals(rupiah(8_000_000), gaji.amount)
        val bakso = entry("bakso 25rb")
        assertEquals(TransactionKind.EXPENSE, bakso.kind)
        assertEquals("bakso", bakso.note)
    }

    // ---------------------------------------------------------------------------------------------- ke isian Catat

    private fun catatContext(last: AccountId? = tunai.id) = CatatContext(
        accounts = accounts, rooms = rooms, categories = categories, rules = emptyList(),
        lastAccountId = last, lastRoomId = keluarga.id, lastCategoryId = belanja.id,
    )

    private fun draftOf(text: String, last: AccountId? = tunai.id) =
        SmartInputParser.toDraft(entry(text), catatContext(last), today)

    @Test
    fun `pengeluaran menjadi isian Catat lengkap`() {
        val draft = draftOf("gojek 23rb dari gopay kemarin bayar transportasi")
        assertEquals(CatatMode.EXPENSE, draft.mode)
        assertEquals("23000", draft.digits)
        assertEquals(gopay.id, draft.accountId)
        assertEquals(transport.roomId, draft.roomId)
        assertEquals(transport.id, draft.categoryId)
        assertEquals(d(2026, 9, 23), draft.date)
        assertEquals("gojek", draft.note)
        assertNull(draft.issue(catatContext()))
    }

    @Test
    fun `kolom yang tidak dikenali memakai bawaan Catat`() {
        val draft = draftOf("kopi 25rb", last = bca.id)
        assertEquals(bca.id, draft.accountId)
        assertEquals(keluarga.id, draft.roomId)
        assertEquals(belanja.id, draft.categoryId)
        assertEquals(today, draft.date)
    }

    @Test
    fun `pemasukan mengisi sumber dan memakai Lainnya bila tidak dikenali`() {
        val gaji = draftOf("gaji masuk 8jt ke bca")
        assertEquals(CatatMode.INCOME, gaji.mode)
        assertEquals("Gaji", gaji.source)
        assertEquals(bca.id, gaji.accountId)
        assertEquals("8000000", gaji.digits)
        assertEquals("Lainnya", draftOf("dapat thr 1jt").source)
        assertNull(draftOf("gaji masuk 8jt").issue(catatContext()))
    }

    @Test
    fun `transfer mengisi akun asal dan tujuan`() {
        val draft = draftOf("tf bca ke gopay 100rb")
        assertEquals(CatatMode.TRANSFER, draft.mode)
        assertEquals(bca.id, draft.accountId)
        assertEquals(gopay.id, draft.toAccountId)
        assertNull(draft.issue(catatContext()))
    }

    @Test
    fun `transfer tanpa tujuan diisi akun lain supaya tetap sah`() {
        val draft = draftOf("topup 100rb tarik bca")
        assertEquals(CatatMode.TRANSFER, draft.mode)
        assertNotNull(draft.toAccountId)
        assertTrue(draft.toAccountId != draft.accountId)
        assertNull(draft.issue(catatContext()))
    }

    @Test
    fun `catatan panjang dipotong sebatas batas catatan`() {
        val long = "x".repeat(300)
        val draft = SmartInputParser.toDraft(entry("$long 25rb"), catatContext(), today)
        assertEquals(200, draft.note.length)
    }

    @Test
    fun `semua contoh menghasilkan isian yang sah`() {
        val samples = listOf(
            "kopi 25rb", "gojek 23rb dari gopay", "gaji masuk 8jt", "bakso 25rb", "tf bca ke gopay 100rb", "topup gopay 100rb",
            "tarik tunai 500rb dari bca", "bayar listrik 350rb kemarin", "dapat thr 1jt", "transfer ke ibu 500rb", "belanja 300rb tunai",
        )
        for (text in samples) {
            val draft = draftOf(text)
            assertNull(draft.issue(catatContext()), text)
        }
    }
}

class SmartInputServiceTest {

    private fun rupiah(n: Long) = Money.rupiah(n)

    @Test
    fun `riwayat pengeluaran menebak kategori lewat layanan`() {
        val f = LedgerFixture().standard()
        val keluarga = f.room("Keluarga")
        val sekolah = f.category("Keluarga", "Sekolah")
        runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(30_000), f.account.id, keluarga.id, sekolah.id, f.today, note = "fotokopi tugas"))
        }
        val catat = runSuspend { CatatContextLoader(f.store, f.store, f.store).load() }
        val service = SmartInputService(f.store)

        val result = assertNotNull(runSuspend { service.draftFor("fotokopi 5rb", catat, f.today) })

        assertEquals(sekolah.id, result.first.categoryId)
        assertEquals(sekolah.id, result.second.categoryId)
        assertEquals("5000", result.second.digits)
    }

    @Test
    fun `layanan tidak menyimpan apa pun`() {
        val f = LedgerFixture().standard()
        val before = f.store.transactionRows.size
        val catat = runSuspend { CatatContextLoader(f.store, f.store, f.store).load() }

        runSuspend { SmartInputService(f.store).draftFor("gojek 23rb dari dompet", catat, f.today) }

        assertEquals(before, f.store.transactionRows.size)
    }

    @Test
    fun `tanpa nominal layanan mengembalikan null`() {
        val f = LedgerFixture().standard()
        val catat = runSuspend { CatatContextLoader(f.store, f.store, f.store).load() }

        assertNull(runSuspend { SmartInputService(f.store).draftFor("gojek dari dompet", catat, f.today) })
    }

    @Test
    fun `riwayat hanya pengeluaran bercatatan dalam sembilan puluh hari`() {
        val f = LedgerFixture().standard()
        val keluarga = f.room("Keluarga")
        val sekolah = f.category("Keluarga", "Sekolah")
        val lampau = f.today.minusDays(120)
        runSuspend {
            f.ledger.recordExpense(NewExpense(rupiah(1_000), f.account.id, keluarga.id, sekolah.id, lampau, note = "lama sekali"))
            f.ledger.recordExpense(NewExpense(rupiah(2_000), f.account.id, keluarga.id, sekolah.id, f.today))
            f.ledger.recordExpense(NewExpense(rupiah(3_000), f.account.id, keluarga.id, sekolah.id, f.today, note = "baru"))
        }
        val catat = runSuspend { CatatContextLoader(f.store, f.store, f.store).load() }

        val context = runSuspend { SmartInputService(f.store).contextFor(catat, f.today) }

        assertEquals(listOf("baru"), context.history.map { it.note })
    }
}
