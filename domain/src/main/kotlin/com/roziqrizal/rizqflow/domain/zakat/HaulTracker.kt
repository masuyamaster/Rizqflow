package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.calendar.HijriCalendar
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Kejadian yang memengaruhi haul, diurutkan menurut tanggal. */
sealed interface HaulEvent {
    val date: LocalDate

    /**
     * Nilai harta diperiksa pada [date]: [netWealth] dibandingkan dengan [nisab] pada hari itu.
     * Nisab ikut dicatat karena harga emas berubah dari waktu ke waktu.
     */
    data class WealthChecked(override val date: LocalDate, val netWealth: Money, val nisab: Money) : HaulEvent {
        val reachesNisab: Boolean get() = netWealth >= nisab
    }

    /** Zakat ditunaikan pada [date]; haul baru dimulai bila harta masih di atas nisab. */
    data class ZakatPaid(override val date: LocalDate) : HaulEvent
}

sealed interface HaulStatus {
    /** Belum mencapai nisab (atau haul terputus): dipantau, belum ada haul. */
    data object BelowNisab : HaulStatus

    /** Haul berjalan sejak [start], genap pada [due]. */
    data class Running(val start: LocalDate, val due: LocalDate, val elapsedDays: Long, val totalDays: Long) : HaulStatus {
        val daysLeft: Long get() = totalDays - elapsedDays
    }

    /** Haul genap: zakat jatuh tempo dan bisa ditunaikan. */
    data class Completed(val start: LocalDate, val due: LocalDate) : HaulStatus
}

/**
 * Menentukan status haul dari riwayat pemeriksaan harta. Satu haul adalah satu tahun Hijriyah
 * sejak harta pertama kali mencapai nisab, dihitung memakai [calendar]. Cara menyikapi harta
 * yang turun di bawah nisab mengikuti [ZakatAssumptions.haulBreakPolicy].
 */
class HaulTracker(
    private val calendar: HijriCalendar,
    private val assumptions: ZakatAssumptions = ZakatAssumptions(),
) {

    fun status(events: List<HaulEvent>, today: LocalDate): HaulStatus {
        var start: LocalDate? = null
        var aboveNisab = false

        for (event in events.filter { !it.date.isAfter(today) }.sortedBy { it.date }) {
            when (event) {
                is HaulEvent.WealthChecked -> {
                    aboveNisab = event.reachesNisab
                    start = when (assumptions.haulBreakPolicy) {
                        HaulBreakPolicy.RESET_WHEN_BELOW_NISAB ->
                            if (aboveNisab) start ?: event.date else null

                        HaulBreakPolicy.CHECK_ONLY_AT_START_AND_END -> when {
                            start == null -> if (aboveNisab) event.date else null
                            !aboveNisab && !event.date.isBefore(calendar.plusYears(start)) -> null
                            else -> start
                        }
                    }
                }

                is HaulEvent.ZakatPaid -> start = if (aboveNisab) event.date else null
            }
        }

        val begin = start ?: return HaulStatus.BelowNisab
        val due = calendar.plusYears(begin)
        if (today.isBefore(due)) {
            return HaulStatus.Running(
                start = begin,
                due = due,
                elapsedDays = ChronoUnit.DAYS.between(begin, today),
                totalDays = ChronoUnit.DAYS.between(begin, due),
            )
        }
        val stillAbove = assumptions.haulBreakPolicy == HaulBreakPolicy.RESET_WHEN_BELOW_NISAB || aboveNisab
        return if (stillAbove) HaulStatus.Completed(begin, due) else HaulStatus.BelowNisab
    }
}
