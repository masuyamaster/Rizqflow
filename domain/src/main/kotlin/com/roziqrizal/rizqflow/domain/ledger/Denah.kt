package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.money.sum
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Status satu ruang pada satu bulan (S05, S11). Selalu ditampilkan sebagai ikon plus teks, bukan warna saja.
 * [MENUNGGU]: belum ada jatah bulan itu, jadi belum ada yang bisa dinilai (netral, bukan peringatan).
 * [BELUM_TERCAPAI]: bulan yang sudah berakhir dan jatah Menunaikan atau Menumbuhkan belum terpenuhi;
 * penanda netral (bukan peringatan), supaya riwayat tidak menghakimi dan tidak masuk Perlu perhatian.
 */
enum class RoomStatus { MENUNGGU, BERJALAN, TERPENUHI, PERLU_PERHATIAN, BELUM_TERCAPAI }

/**
 * Arti "hak terpenuhi" per tipe ruang (dikonfirmasi 2026-09-20, docs/konsep.md):
 *
 * - **Menunaikan** dan **Menumbuhkan**: terpenuhi saat terpakai mencapai jatah; selebihnya berjalan,
 *   atau belum tercapai bila bulannya sudah berakhir. Investasi dicatat sebagai pengeluaran di ruang Diri
 *   (kategori Investasi atau Dana darurat), tanpa akun tujuan (disetujui 2026-09-21).
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
                when {
                    progress >= 10_000 -> RoomStatus.TERPENUHI
                    monthOver -> RoomStatus.BELUM_TERCAPAI
                    else -> RoomStatus.BERJALAN
                }

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

/**
 * "Sisa aman hari ini" (Tahap 10): berapa yang masih aman dibelanjakan hari ini dari ruang bertipe Mencukupi.
 * Rumus disetujui 2026-09-24: jatah tersisa (per ruang, sebelum pengeluaran hari ini) dibagi sisa hari bulan
 * itu termasuk hari ini, dikurangi pengeluaran hari ini. Ruang Menunaikan dan Menumbuhkan tidak ikut karena
 * itu tabungan atau zakat, bukan jatah belanja.
 *
 * Tenang, bukan panik: tidak pernah negatif. Ruang yang sudah melewati jatah dihitung 0 (tidak memakan jatah
 * ruang lain), dan bila jatah hari ini terlampaui [remaining] menjadi 0 dan [isOver] bernilai true.
 */
data class SafeToSpend(
    /** Jatah harian: jatah tersisa sebelum hari ini dibagi [daysLeft], dibulatkan ke bawah. */
    val dailyBudget: Money,
    /** Pengeluaran hari ini di ruang Mencukupi. */
    val spentToday: Money,
    /** Sisa hari bulan ini termasuk hari ini (minimal 1). */
    val daysLeft: Int,
) {
    val remaining: Money get() = if (spentToday >= dailyBudget) Money.zero() else dailyBudget - spentToday
    val isOver: Boolean get() = spentToday > dailyBudget

    companion object {
        /**
         * Null bila tidak ada ruang Mencukupi yang punya jatah bulan ini (tidak ada yang bisa dihitung).
         * [spentTodayByRoom] adalah pengeluaran hari [today] per ruang; [cards] berisi terpakai sebulan penuh
         * sampai hari ini.
         */
        fun compute(cards: List<RoomCard>, spentTodayByRoom: Map<RoomId, Money>, today: LocalDate): SafeToSpend? {
            val everyday = cards.filter { it.room.kind == RoomKind.MENCUKUPI && it.allocated.isPositive }
            if (everyday.isEmpty()) return null
            val spentToday = everyday.map { spentTodayByRoom[it.room.id] ?: Money.zero() }.sum()
            val pool = everyday.map { card ->
                val today0 = spentTodayByRoom[card.room.id] ?: Money.zero()
                val left = card.allocated - (card.spent - today0)
                if (left.isPositive) left else Money.zero()
            }.sum()
            val daysLeft = YearMonth.from(today).lengthOfMonth() - today.dayOfMonth + 1
            return SafeToSpend(Money(pool.minor / daysLeft), spentToday, daysLeft)
        }
    }
}

/** Butir di bagian "Perlu perhatian" S05. */
sealed interface AttentionItem {
    /** Terpakai sudah melewati jatah. */
    data class RoomOverLimit(val room: Room, val allocated: Money, val spent: Money) : AttentionItem

    /** Terpakai mendekati jatah (85% ke atas, belum lewat). */
    data class RoomNearLimit(val room: Room, val progressBp: Int) : AttentionItem

    /** Ada rezeki bulan ini yang belum dialirkan ke ruang mana pun. */
    data class Unallocated(val amount: Money) : AttentionItem

    /** Saldo akun sudah pernah dicocokkan (S25), tapi sudah lebih dari [DenahLoader.STALE_RECONCILE_DAYS] hari. */
    data class AccountNotReconciled(val account: Account, val daysSince: Int) : AttentionItem
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
    /** Hanya untuk bulan berjalan; null di bulan lain atau bila tidak ada jatah ruang Mencukupi. */
    val safeToSpend: SafeToSpend? = null,
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
    private val accounts: AccountRepository,
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
        if (month != current) return DenahOverview(month, income, unallocated, cards, emptyList())
        val safe = SafeToSpend.compute(cards, transactions.roomTotals(today, today).spent, today)
        return DenahOverview(month, income, unallocated, cards, attentionFor(cards, unallocated, today), safe)
    }

    /** Urutan: ruang yang sudah lewat jatah, lalu yang mendekati, rezeki belum dialirkan, lalu saldo akun lama tidak dicocokkan. */
    private suspend fun attentionFor(cards: List<RoomCard>, unallocated: Money, today: LocalDate): List<AttentionItem> {
        val over = cards.filter { it.isOver && it.room.kind == RoomKind.MENCUKUPI }
            .sortedByDescending { it.progressBp }
            .map { AttentionItem.RoomOverLimit(it.room, it.allocated, it.spent) }
        val near = cards.filter { it.status == RoomStatus.PERLU_PERHATIAN && !it.isOver }
            .sortedByDescending { it.progressBp }
            .map { AttentionItem.RoomNearLimit(it.room, it.progressBp ?: 0) }
        val loose = if (unallocated.isPositive) listOf(AttentionItem.Unallocated(unallocated)) else emptyList()
        // Akun yang belum pernah dicocokkan tidak ditandai: baru mulai "berumur" sejak koreksi pertamanya.
        val stale = accounts.activeAccounts()
            .mapNotNull { account ->
                val last = account.lastReconciledOn ?: return@mapNotNull null
                val days = ChronoUnit.DAYS.between(last, today).toInt()
                if (days > STALE_RECONCILE_DAYS) AttentionItem.AccountNotReconciled(account, days) else null
            }
            .sortedByDescending { it.daysSince }
        return (over + near + loose + stale).take(DenahOverview.MAX_ATTENTION)
    }

    companion object {
        /** Ambang "lama tidak dikoreksi" (docs/ui-flow.md): lebih dari 7 hari sejak S25 terakhir. */
        const val STALE_RECONCILE_DAYS = 7
    }
}
