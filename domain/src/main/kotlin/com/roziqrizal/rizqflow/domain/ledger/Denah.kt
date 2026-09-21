package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.money.sum
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/**
 * Status satu ruang pada satu bulan (S05, S11). Selalu ditampilkan sebagai ikon plus teks, bukan warna saja.
 * [MENUNGGU]: belum ada jatah bulan itu, jadi belum ada yang bisa dinilai (netral, bukan peringatan).
 */
enum class RoomStatus { MENUNGGU, BERJALAN, TERPENUHI, PERLU_PERHATIAN }

/**
 * Arti "hak terpenuhi" per tipe ruang (dikonfirmasi 2026-09-20, docs/konsep.md):
 *
 * - **Menunaikan** dan **Menumbuhkan**: terpenuhi saat terpakai mencapai jatah; selebihnya berjalan.
 * - **Mencukupi**: perlu perhatian saat terpakai mencapai 85% jatah atau lebih (termasuk melewati jatah).
 *   Setelah bulannya berakhir, terpenuhi bila terpakai tidak melebihi jatah. Penafsiran "kebutuhan
 *   tertutup" ini usulan yang menunggu konfirmasi pemilik.
 *
 * Bulan tanpa jatah selalu [RoomStatus.MENUNGGU]. Batasnya inklusif: tepat 85% sudah perlu perhatian.
 */
object RoomStatusRules {
    /** Ambang perlu perhatian tipe Mencukupi, dalam basis point (85%). */
    const val WARN_AT_BP = 8_500

    private val TEN_THOUSAND = BigInteger.valueOf(10_000)

    /** Terpakai dibanding jatah dalam basis point, dibulatkan ke bawah; null bila jatah tidak ada. Bisa lebih dari 10.000. */
    fun progressBp(allocated: Money, spent: Money): Int? {
        if (!allocated.isPositive) return null
        val bp = BigInteger.valueOf(spent.minor.coerceAtLeast(0)).multiply(TEN_THOUSAND).divide(BigInteger.valueOf(allocated.minor))
        return bp.min(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
    }

    fun statusOf(kind: RoomKind, allocated: Money, spent: Money, monthOver: Boolean): RoomStatus {
        val progress = progressBp(allocated, spent) ?: return RoomStatus.MENUNGGU
        return when (kind) {
            RoomKind.MENUNAIKAN, RoomKind.MENUMBUHKAN ->
                if (progress >= 10_000) RoomStatus.TERPENUHI else RoomStatus.BERJALAN

            RoomKind.MENCUKUPI -> when {
                // Dibandingkan sebagai uang, bukan persentase yang dibulatkan: lebih Rp 1 tetap melewati jatah.
                monthOver -> if (spent <= allocated) RoomStatus.TERPENUHI else RoomStatus.PERLU_PERHATIAN
                progress >= WARN_AT_BP -> RoomStatus.PERLU_PERHATIAN
                else -> RoomStatus.BERJALAN
            }
        }
    }
}

/** Kartu satu ruang di Denah. [remaining] negatif bila terpakai melewati jatah. */
data class RoomCard(
    val room: Room,
    val allocated: Money,
    val spent: Money,
    /** Null bila belum ada jatah. Bisa lebih dari 10.000 (100%). */
    val progressBp: Int?,
    val status: RoomStatus,
) {
    val remaining: Money get() = allocated - spent
    val isOver: Boolean get() = allocated.isPositive && spent > allocated
}

/** Butir di bagian "Perlu perhatian" S05. */
sealed interface AttentionItem {
    /** Terpakai sudah melewati jatah. */
    data class RoomOverLimit(val room: Room, val allocated: Money, val spent: Money) : AttentionItem

    /** Terpakai mendekati jatah (85% ke atas, belum lewat). */
    data class RoomNearLimit(val room: Room, val progressBp: Int) : AttentionItem

    /** Ada rezeki bulan ini yang belum dialirkan ke ruang mana pun. */
    data class Unallocated(val amount: Money) : AttentionItem
}

/**
 * Isi Denah (S05) untuk satu bulan. [income] dan [unallocated] mengikuti bulan Masehi.
 * [attention] hanya berisi butir untuk bulan berjalan, paling banyak [MAX_ATTENTION].
 */
data class DenahOverview(
    val month: YearMonth,
    val income: Money,
    val unallocated: Money,
    val cards: List<RoomCard>,
    val attention: List<AttentionItem>,
) {
    val hasRooms: Boolean get() = cards.isNotEmpty()
    val hasIncome: Boolean get() = income.isPositive

    companion object {
        const val MAX_ATTENTION = 3
    }
}

/**
 * Menyusun Denah dari transaksi. Semua angka diturunkan dari transaksi, tidak ada yang disimpan
 * (docs/model-data.md). Ruang terarsip tidak tampil, tetapi alokasinya yang sudah terjadi tetap
 * terhitung sebagai sudah dialirkan.
 */
class DenahLoader(
    private val rooms: RoomRepository,
    private val transactions: TransactionRepository,
) {
    suspend fun load(month: YearMonth, today: LocalDate): DenahOverview {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        val totals = transactions.roomTotals(from, to)
        val income = transactions.between(from, to).filter { it.kind == TransactionKind.INCOME }.map { it.amount }.sum()
        val allocatedAll = totals.allocated.values.sum()
        val unallocated = if (income > allocatedAll) income - allocatedAll else Money.zero()

        val current = YearMonth.from(today)
        val monthOver = month < current
        val cards = rooms.activeRooms().map { room ->
            val allocated = totals.allocated[room.id] ?: Money.zero()
            val spent = totals.spent[room.id] ?: Money.zero()
            RoomCard(room, allocated, spent, RoomStatusRules.progressBp(allocated, spent), RoomStatusRules.statusOf(room.kind, allocated, spent, monthOver))
        }
        return DenahOverview(month, income, unallocated, cards, if (month == current) attentionFor(cards, unallocated) else emptyList())
    }

    /** Urutan: ruang yang sudah lewat jatah, lalu yang mendekati, lalu rezeki belum dialirkan. */
    private fun attentionFor(cards: List<RoomCard>, unallocated: Money): List<AttentionItem> {
        val over = cards.filter { it.isOver && it.room.kind == RoomKind.MENCUKUPI }
            .sortedByDescending { it.progressBp }
            .map { AttentionItem.RoomOverLimit(it.room, it.allocated, it.spent) }
        val near = cards.filter { it.status == RoomStatus.PERLU_PERHATIAN && !it.isOver }
            .sortedByDescending { it.progressBp }
            .map { AttentionItem.RoomNearLimit(it.room, it.progressBp ?: 0) }
        val loose = if (unallocated.isPositive) listOf(AttentionItem.Unallocated(unallocated)) else emptyList()
        return (over + near + loose).take(DenahOverview.MAX_ATTENTION)
    }
}
