package com.roziqrizal.rizqflow.domain.allocation

import com.roziqrizal.rizqflow.domain.money.Money
import java.math.BigInteger

/**
 * Hasil pembagian rezeki. [shares] mengikuti urutan aturan masukan. [unallocated] adalah
 * bagian yang memang tidak diatur (total aturan di bawah 100%), tampil sebagai "belum dialirkan".
 * Jumlah semua [shares] ditambah [unallocated] selalu sama dengan nominal masukan.
 */
data class AllocationResult(val shares: List<AllocationShare>, val unallocated: Money)

/**
 * Rule engine alokasi persentase.
 *
 * **Aturan pembulatan (diputuskan 2026-09-21): metode sisa terbesar.** Setiap ruang lebih dulu
 * menerima bagian yang dibulatkan ke bawah. Sisa rupiah yang muncul (selalu kurang dari jumlah
 * ruang) dibagikan satu-satu ke ruang dengan pecahan terbesar; bila pecahan sama, ruang yang
 * lebih awal dalam urutan aturan (prioritas lebih tinggi) didahulukan. Dengan begitu jumlah
 * bagian selalu persis nominal, dan tidak ada ruang yang selalu diuntungkan.
 *
 * Contoh: Rp 1.234.567 dengan 10%, 50%, 40% menjadi 123.457, 617.283, 493.827.
 */
object AllocationEngine {

    fun allocate(amount: Money, rules: List<AllocationRule>): AllocationResult {
        require(!amount.isNegative) { "Nominal yang dialirkan tidak boleh negatif: ${amount.minor}" }
        require(rules.map { it.roomId }.toSet().size == rules.size) { "Satu ruang muncul lebih dari sekali di aturan" }
        val totalShare = rules.sumOf { it.share.value }
        require(totalShare <= BasisPoints.FULL) { "Total aturan melebihi 100%: $totalShare basis point" }

        val full = BigInteger.valueOf(BasisPoints.FULL.toLong())
        val total = BigInteger.valueOf(amount.minor)
        val exact = rules.map { total * BigInteger.valueOf(it.share.value.toLong()) }
        val floors = exact.map { (it / full).toLong() }
        val fractions = exact.map { (it % full).toInt() }

        // Bagian yang memang diatur; sisanya ([unallocated]) tidak dibagikan ke ruang mana pun.
        val target = (total * BigInteger.valueOf(totalShare.toLong()) / full).toLong()
        var leftover = target - floors.sum()

        val amounts = floors.toMutableList()
        val byFractionThenPriority = rules.indices.sortedWith(
            compareByDescending<Int> { fractions[it] }.thenBy { it },
        )
        for (index in byFractionThenPriority) {
            if (leftover == 0L || fractions[index] == 0) break
            amounts[index] += 1
            leftover -= 1
        }
        check(leftover == 0L) { "Sisa pembulatan tidak habis dibagikan: $leftover" }

        return AllocationResult(
            shares = rules.mapIndexed { i, rule -> AllocationShare(rule.roomId, Money(amounts[i], amount.currency)) },
            unallocated = Money(amount.minor - target, amount.currency),
        )
    }
}
