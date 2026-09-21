package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.auth.runSuspend
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OnboardingDraftTest {

    // ------------------------------------------------------------------ papan angka

    private fun press(vararg keys: String) = keys.fold("") { acc, k -> AmountPad.apply(acc, k) }

    @Test
    fun `papan angka menyusun digit berurutan`() {
        assertEquals("1250", press("1", "2", "5", "0"))
    }

    @Test
    fun `nol di depan diabaikan`() {
        assertEquals("", press("0"))
        assertEquals("", press("0", "0"))
        assertEquals("5", press("0", "5"))
        assertEquals("50", press("5", "0"))
    }

    @Test
    fun `tombol tiga nol menambah hanya bila sudah ada angka`() {
        assertEquals("", press("000"))
        assertEquals("5000", press("5", "000"))
        assertEquals("1000000", press("1", "000", "000"))
    }

    @Test
    fun `panjang dibatasi sebelas digit`() {
        assertEquals("12345678901", press(*"123456789012".map { it.toString() }.toTypedArray()))
        assertEquals("12345678000", press("1", "2", "3", "4", "5", "6", "7", "8", "000"))
        // Sembilan digit sudah tidak muat tambahan 000.
        val sembilan = press(*"123456789".map { it.toString() }.toTypedArray())
        assertEquals(sembilan, AmountPad.apply(sembilan, "000"))
    }

    @Test
    fun `tombol hapus membuang satu digit dan aman pada kosong`() {
        assertEquals("12", press("1", "2", "3", "back"))
        assertEquals("", press("back"))
    }

    @Test
    fun `tombol tak dikenal tidak mengubah apa pun`() {
        assertEquals("12", AmountPad.apply("12", "x"))
        assertEquals("12", AmountPad.apply("12", "12"))
    }

    @Test
    fun `digit menjadi rupiah dan kosong berarti nol`() {
        assertEquals(Money.rupiah(50_000), AmountPad.toRupiah("50000"))
        assertEquals(Money.rupiah(0), AmountPad.toRupiah(""))
    }

    // ------------------------------------------------------------------ penyunting persen

    @Test
    fun `mengubah satu ruang membuat ruang terakhir menerima sisa`() {
        assertEquals(listOf(20, 30, 50), ShareEditor.set(listOf(10, 30, 60), 0, 20))
        assertEquals(listOf(10, 50, 40), ShareEditor.set(listOf(10, 30, 60), 1, 50))
    }

    @Test
    fun `total selalu 100 untuk semua masukan`() {
        for (a in 0..100 step 7) for (b in -5..110 step 9) {
            val hasil = ShareEditor.set(ShareEditor.set(listOf(10, 30, 60), 0, a), 1, b)
            assertEquals(100, hasil.sum(), "a=$a b=$b -> $hasil")
            assertTrue(hasil.all { it in 0..100 }, "a=$a b=$b -> $hasil")
        }
    }

    @Test
    fun `ruang manual dibatasi supaya jumlahnya tidak melebihi 100`() {
        // Memberi 40, Diri 30 -> Diri paling banyak 60 karena Memberi sudah 40.
        val hasil = ShareEditor.set(listOf(40, 30, 30), 1, 90)
        assertEquals(listOf(40, 60, 0), hasil)
    }

    @Test
    fun `nilai negatif dan kelewat besar dijepit`() {
        assertEquals(listOf(0, 30, 70), ShareEditor.set(listOf(10, 30, 60), 0, -20))
        assertEquals(listOf(70, 30, 0), ShareEditor.set(listOf(10, 30, 60), 0, 500))
    }

    @Test
    fun `ruang terakhir tidak bisa diubah langsung`() {
        assertEquals(listOf(10, 30, 60), ShareEditor.set(listOf(10, 30, 60), 2, 5))
    }

    @Test
    fun `tombol tambah dan kurang satu persen`() {
        assertEquals(listOf(11, 30, 59), ShareEditor.step(listOf(10, 30, 60), 0, 1))
        assertEquals(listOf(9, 30, 61), ShareEditor.step(listOf(10, 30, 60), 0, -1))
        assertEquals(listOf(0, 30, 70), ShareEditor.step(listOf(0, 30, 70), 0, -1), "tidak di bawah 0")
    }

    @Test
    fun `batas atas slider memperhitungkan ruang manual lain`() {
        assertEquals(70, ShareEditor.maxFor(listOf(10, 30, 60), 0))
        assertEquals(90, ShareEditor.maxFor(listOf(10, 30, 60), 1))
    }

    @Test
    fun `satu ruang saja selalu 100`() {
        assertEquals(listOf(100), ShareEditor.set(listOf(100), 0, 30))
    }

    @Test
    fun `masukan tidak sah ditolak`() {
        assertFailsWith<IllegalArgumentException> { ShareEditor.set(emptyList(), 0, 1) }
        assertFailsWith<IllegalArgumentException> { ShareEditor.set(listOf(50, 50), 5, 1) }
    }

    // ------------------------------------------------------------------ isian onboarding

    @Test
    fun `bawaan adalah Tiga hak 10 30 60 dengan akun Tunai`() {
        val d = OnboardingDraft()
        assertEquals(RoomTemplate.TIGA_HAK, d.template)
        assertEquals(listOf(10, 30, 60), d.percents)
        assertEquals(AccountKind.CASH, d.accountKind)
        assertEquals("Tunai", d.accountName)
        assertEquals(3, d.totalSteps)
        assertTrue(d.canFinish)
        assertEquals(Money.rupiah(0), d.openingBalance)
    }

    @Test
    fun `Mulai kosong melewati langkah persentase dan tidak punya pembagian`() {
        val d = OnboardingDraft().chooseTemplate(RoomTemplate.KOSONG)
        assertEquals(2, d.totalSteps)
        assertEquals(2, d.accountStepNumber)
        assertTrue(d.percents.isEmpty())
    }

    @Test
    fun `kembali ke Tiga hak memulihkan pembagian bawaan`() {
        val d = OnboardingDraft().setPercent(0, 40).chooseTemplate(RoomTemplate.KOSONG).chooseTemplate(RoomTemplate.TIGA_HAK)
        assertEquals(listOf(10, 30, 60), d.percents)
    }

    @Test
    fun `nama bawaan hanya untuk Tunai dan jenis lain dikosongkan sampai pengguna mengetik`() {
        val bank = OnboardingDraft().chooseKind(AccountKind.BANK)
        assertEquals("", bank.accountName)
        assertFalse(bank.canFinish)
        assertEquals("Tunai", bank.chooseKind(AccountKind.CASH).accountName)
    }

    @Test
    fun `nama yang diketik sendiri tidak ditimpa saat ganti jenis`() {
        val d = OnboardingDraft().chooseKind(AccountKind.EWALLET).typeName("GoPay").chooseKind(AccountKind.BANK)
        assertEquals("GoPay", d.accountName)
        assertEquals(AccountKind.BANK, d.accountKind)
    }

    @Test
    fun `nama dipotong 30 karakter dan spasi saja tidak cukup untuk selesai`() {
        assertEquals(30, OnboardingDraft().typeName("x".repeat(50)).accountName.length)
        assertFalse(OnboardingDraft().typeName("   ").canFinish)
    }

    @Test
    fun `saldo awal dari papan angka`() {
        val d = "50000".map { it.toString() }.fold(OnboardingDraft()) { draft, k -> draft.pressKey(k) }
        assertEquals(Money.rupiah(50_000), d.openingBalance)
        assertEquals(Money.rupiah(5_000), d.pressKey(AmountPad.BACK).openingBalance)
    }

    @Test
    fun `ringkasan Rp 1 juta memakai pembulatan yang sama dengan pencatatan`() {
        val hasil = OnboardingDraft().sample(Money.rupiah(1_000_000))
        assertEquals(listOf(100_000L, 300_000L, 600_000L), hasil.shares.map { it.amount.minor })
        assertEquals(Money.rupiah(0), hasil.unallocated)

        val ganjil = OnboardingDraft().sample(Money.rupiah(1_234_567))
        assertEquals(listOf(123_457L, 370_370L, 740_740L), ganjil.shares.map { it.amount.minor })
    }

    @Test
    fun `isian menjadi akun pertama yang siap disimpan`() {
        val d = OnboardingDraft().chooseKind(AccountKind.EWALLET).typeName("GoPay").pressKey("5").pressKey("000")
        assertEquals(FirstAccount("GoPay", AccountKind.EWALLET, Money.rupiah(5_000)), d.firstAccount())
        assertEquals(listOf(1_000, 3_000, 6_000), d.shares().map { it.value })
    }

    @Test
    fun `isian yang diubah menghasilkan onboarding yang benar di layanan`() {
        val f = LedgerFixture()
        val d = OnboardingDraft().setPercent(0, 20).setPercent(1, 20).chooseKind(AccountKind.BANK).typeName("Bank Jago").pressKey("1").pressKey("000")

        val result = runSuspend { f.setup.setUp(d.template, d.firstAccount(), d.shares()) }

        assertIs<LedgerResult.Success<Unit>>(result)
        assertEquals(listOf(2_000, 2_000, 6_000), runSuspend { f.store.rules() }.map { it.share.value })
        assertEquals("Bank Jago", f.account.name)
        assertEquals(Money.rupiah(1_000), f.account.openingBalance)
    }

    @Test
    fun `pola Kosong dari isian menghasilkan ruang kerja tanpa ruang`() {
        val f = LedgerFixture()
        val d = OnboardingDraft().chooseTemplate(RoomTemplate.KOSONG)

        val result = runSuspend { f.setup.setUp(d.template, d.firstAccount(), d.shares()) }

        assertIs<LedgerResult.Success<Unit>>(result)
        assertTrue(f.store.roomRows.isEmpty())
    }
}
