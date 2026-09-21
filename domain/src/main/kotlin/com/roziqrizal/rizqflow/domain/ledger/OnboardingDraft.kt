package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationEngine
import com.roziqrizal.rizqflow.domain.allocation.AllocationResult
import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.money.Money

/**
 * Papan angka (Catat dan saldo awal): aturan yang sama dengan prototipe. Angka berupa teks digit
 * tanpa nol di depan, paling banyak 11 digit; tombol `000` hanya menambah bila sudah ada angka
 * dan masih muat; `back` menghapus satu digit.
 */
object AmountPad {
    const val MAX_DIGITS = 11
    const val BACK = "back"
    const val TRIPLE_ZERO = "000"

    fun apply(digits: String, key: String): String = when {
        key == BACK -> digits.dropLast(1)
        key == TRIPLE_ZERO -> if (digits.isNotEmpty() && digits.length <= MAX_DIGITS - 3) digits + TRIPLE_ZERO else digits
        key.length == 1 && key[0] in '0'..'9' ->
            if (digits.isEmpty() && key == "0") digits else if (digits.length < MAX_DIGITS) digits + key else digits

        else -> digits
    }

    /** Nilai rupiah dari teks digit; kosong berarti nol. */
    fun toRupiah(digits: String): Money = Money.rupiah(digits.toLongOrNull() ?: 0L)
}

/**
 * Penyunting pembagian persen bulat untuk onboarding (S03). **Total selalu 100%:** ruang terakhir
 * tidak punya slider dan menerima sisanya; ruang lain dibatasi supaya jumlahnya tidak melebihi 100%.
 */
object ShareEditor {

    /** Mengisi [percents] ke ruang [index] dengan [value], dibatasi, lalu menghitung ulang sisa untuk ruang terakhir. */
    fun set(percents: List<Int>, index: Int, value: Int): List<Int> {
        require(percents.isNotEmpty()) { "Butuh minimal satu ruang" }
        require(index in percents.indices) { "Ruang $index tidak ada" }
        val last = percents.lastIndex
        if (index == last) return percents // ruang terakhir otomatis: menerima sisa
        val othersManual = percents.filterIndexed { i, _ -> i != index && i != last }.sum()
        val clamped = value.coerceIn(0, 100 - othersManual)
        val result = percents.toMutableList()
        result[index] = clamped
        result[last] = 100 - result.take(last).sum()
        return result
    }

    fun step(percents: List<Int>, index: Int, delta: Int): List<Int> = set(percents, index, percents[index] + delta)

    /** Batas atas slider ruang [index]: sisa setelah ruang manual lain, tanpa ruang terakhir. */
    fun maxFor(percents: List<Int>, index: Int): Int {
        val last = percents.lastIndex
        if (index == last) return percents[last]
        return 100 - percents.filterIndexed { i, _ -> i != index && i != last }.sum()
    }
}

/**
 * Isian onboarding (S02 sampai S04) yang belum disimpan. Murni dan tak berubah: setiap perubahan
 * menghasilkan salinan, sehingga mudah diuji dan aman disimpan saat layar diputar.
 */
data class OnboardingDraft(
    val template: RoomTemplate = RoomTemplate.TIGA_HAK,
    /** Persen bulat tiap ruang pola, berurutan; kosong bila pola Kosong. */
    val percents: List<Int> = defaultPercents(RoomTemplate.TIGA_HAK),
    val accountKind: AccountKind = AccountKind.CASH,
    val accountName: String = defaultName(AccountKind.CASH),
    /** Setelah pengguna mengetik sendiri, mengganti jenis akun tidak menimpa nama lagi. */
    val nameTouched: Boolean = false,
    val balanceDigits: String = "",
) {
    /** Tiga langkah untuk Tiga hak; Mulai kosong melewati S03. */
    val totalSteps: Int get() = if (template == RoomTemplate.TIGA_HAK) 3 else 2

    /** Nomor langkah tampilan untuk layar akun: terakhir. */
    val accountStepNumber: Int get() = totalSteps

    val canFinish: Boolean get() = accountName.isNotBlank()

    val openingBalance: Money get() = AmountPad.toRupiah(balanceDigits)

    fun chooseTemplate(template: RoomTemplate): OnboardingDraft =
        copy(template = template, percents = defaultPercents(template))

    fun setPercent(index: Int, value: Int): OnboardingDraft = copy(percents = ShareEditor.set(percents, index, value))

    fun stepPercent(index: Int, delta: Int): OnboardingDraft = copy(percents = ShareEditor.step(percents, index, delta))

    fun chooseKind(kind: AccountKind): OnboardingDraft =
        copy(accountKind = kind, accountName = if (nameTouched) accountName else defaultName(kind))

    fun typeName(text: String): OnboardingDraft = copy(accountName = text.take(Account.NAME_MAX), nameTouched = true)

    fun pressKey(key: String): OnboardingDraft = copy(balanceDigits = AmountPad.apply(balanceDigits, key))

    /** Ringkasan S03: bagaimana [amount] terbagi dengan persen sekarang, memakai aturan pembulatan yang sama dengan S07. */
    fun sample(amount: Money): AllocationResult {
        val rules = percents.mapIndexed { i, p -> AllocationRule(RoomId("sample-$i"), BasisPoints.percent(p)) }
        return AllocationEngine.allocate(amount, rules)
    }

    fun shares(): List<BasisPoints> = percents.map(BasisPoints::percent)

    fun firstAccount(): FirstAccount = FirstAccount(accountName, accountKind, openingBalance)

    companion object {
        /** Nama bawaan hanya untuk Tunai; jenis lain memberi contoh sebagai petunjuk isian. */
        fun defaultName(kind: AccountKind): String = if (kind == AccountKind.CASH) "Tunai" else ""

        fun defaultPercents(template: RoomTemplate): List<Int> =
            RoomTemplates.rooms(template).map { it.defaultShare.value / 100 }
    }
}
