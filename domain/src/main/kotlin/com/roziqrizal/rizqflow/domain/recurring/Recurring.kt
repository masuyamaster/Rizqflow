package com.roziqrizal.rizqflow.domain.recurring

import com.roziqrizal.rizqflow.domain.ledger.AccountRepository
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.NewExpense
import com.roziqrizal.rizqflow.domain.ledger.NewIncome
import com.roziqrizal.rizqflow.domain.ledger.NewTransfer
import com.roziqrizal.rizqflow.domain.ledger.RoomRepository
import com.roziqrizal.rizqflow.domain.ledger.TransactionRepository
import com.roziqrizal.rizqflow.domain.ledger.failure
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Transaksi berulang (Tahap 10, Gratis): aturan yang mencatat pemasukan, pengeluaran, atau
 * transfer secara otomatis pada jadwal harian, mingguan, atau bulanan. Tidak ada penjadwal di
 * latar belakang: setiap kali aplikasi dibuka, [RecurringService.runDue] mencatat semua kemunculan
 * yang sudah lewat sejak terakhir kali. Aplikasi yang lama tidak dibuka tidak kehilangan
 * kemunculan, dan menjalankannya dua kali tidak menggandakan apa pun.
 */

enum class Frequency { DAILY, WEEKLY, MONTHLY }

/**
 * Menghitung kemunculan dari tanggal awal ([start]), tidak pernah dari kemunculan sebelumnya, supaya
 * tanggal tidak bergeser: aturan bulanan yang mulai tanggal 31 jatuh pada 28 (29) Februari lalu
 * kembali ke 31 Maret, bukan tertinggal di 28.
 */
object RecurrenceSchedule {

    /** Kemunculan pertama yang jatuh pada [date] atau sesudahnya. */
    fun firstOnOrAfter(start: LocalDate, frequency: Frequency, date: LocalDate): LocalDate =
        if (date <= start) start else next(start, frequency, date.minusDays(1))

    /** Kemunculan pertama yang jatuh **sesudah** [after]. */
    fun next(start: LocalDate, frequency: Frequency, after: LocalDate): LocalDate {
        if (after < start) return start
        return when (frequency) {
            Frequency.DAILY -> after.plusDays(1)

            Frequency.WEEKLY -> {
                val weeks = ChronoUnit.DAYS.between(start, after) / DAYS_PER_WEEK
                start.plusDays((weeks + 1) * DAYS_PER_WEEK)
            }

            Frequency.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), after.withDayOfMonth(1))
                val candidate = start.plusMonths(months)
                if (candidate > after) candidate else start.plusMonths(months + 1)
            }
        }
    }

    private const val DAYS_PER_WEEK = 7L
}

/**
 * Satu aturan berulang. [nextDue] adalah kemunculan berikutnya yang belum dicatat; maju setiap
 * satu kemunculan berhasil dicatat. [active] false berarti dijeda oleh pengguna. Selesai
 * ([isFinished]) bila ada [endDate] dan [nextDue] sudah melewatinya.
 */
data class RecurringRule(
    val id: String,
    val kind: TransactionKind,
    val amount: Money,
    val accountId: AccountId,
    val toAccountId: AccountId? = null,
    val roomId: RoomId? = null,
    val categoryId: CategoryId? = null,
    val incomeSource: String? = null,
    val note: String? = null,
    val frequency: Frequency,
    /** Tanggal awal; kemunculan berikutnya selalu dihitung darinya, lihat [RecurrenceSchedule]. */
    val startDate: LocalDate,
    val nextDue: LocalDate,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
) {
    init {
        require(amount.isPositive) { "Nominal transaksi berulang harus lebih dari nol" }
        require(endDate == null || endDate >= startDate) { "Tanggal akhir tidak boleh sebelum tanggal awal" }
    }

    val isFinished: Boolean get() = endDate != null && nextDue > endDate
}

interface RecurringRepository {
    /** Semua aturan, termasuk yang dijeda dan selesai. */
    suspend fun all(): List<RecurringRule>

    suspend fun find(id: String): RecurringRule?

    suspend fun save(rule: RecurringRule)

    suspend fun delete(id: String)
}

/** Isian layar untuk membuat atau mengubah aturan. Isian yang tidak berlaku bagi [kind] diabaikan. */
data class RecurringDraft(
    val kind: TransactionKind,
    val amount: Money,
    val accountId: AccountId,
    val toAccountId: AccountId? = null,
    val roomId: RoomId? = null,
    val categoryId: CategoryId? = null,
    val incomeSource: String? = null,
    val note: String? = null,
    val frequency: Frequency,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
)

/** Satu baris daftar dengan nama tujuannya. [usable]: akun, ruang, dan kategorinya masih aktif, jadi kemunculan berikutnya bisa dicatat. */
data class RecurringRow(
    val rule: RecurringRule,
    val accountName: String,
    val toAccountName: String?,
    val roomName: String?,
    val categoryName: String?,
    val usable: Boolean,
)

/** Kemunculan yang tidak bisa dicatat (mis. akunnya sudah diarsipkan); aturan tetap di tempatnya sampai diperbaiki atau dijeda. */
data class BlockedOccurrence(val rule: RecurringRule, val date: LocalDate, val error: LedgerError)

/** Hasil satu kali [RecurringService.runDue]: banyaknya transaksi yang baru dicatat dan kemunculan yang tertahan. */
data class RecurringRun(val recorded: Int, val blocked: List<BlockedOccurrence>)

class RecurringService(
    private val rules: RecurringRepository,
    private val accounts: AccountRepository,
    private val rooms: RoomRepository,
    private val transactions: TransactionRepository,
    private val ledger: LedgerService,
    private val newId: () -> String,
) {

    suspend fun list(): List<RecurringRow> {
        val allAccounts = accounts.allAccounts().associateBy { it.id }
        val allRooms = rooms.allRooms().associateBy { it.id }
        val allCategories = rooms.allCategories().associateBy { it.id }
        return rules.all()
            .sortedWith(compareBy<RecurringRule> { !it.active || it.isFinished }.thenBy { it.nextDue }.thenBy { it.id })
            .map { rule ->
                val account = allAccounts[rule.accountId]
                val toAccount = rule.toAccountId?.let { allAccounts[it] }
                val room = rule.roomId?.let { allRooms[it] }
                val category = rule.categoryId?.let { allCategories[it] }
                RecurringRow(
                    rule = rule,
                    accountName = account?.name.orEmpty(),
                    toAccountName = toAccount?.name,
                    roomName = room?.name,
                    categoryName = category?.name,
                    usable = account != null && !account.archived &&
                        (rule.toAccountId == null || (toAccount != null && !toAccount.archived)) &&
                        (rule.roomId == null || (room != null && !room.archived)) &&
                        (rule.categoryId == null || (category != null && !category.archived)),
                )
            }
    }

    /** Membuat aturan baru. Tanggal awal tidak boleh sebelum [today]; yang sudah lewat dicatat sendiri oleh pengguna. */
    suspend fun create(draft: RecurringDraft, today: LocalDate): LedgerResult<RecurringRule> {
        validate(draft)?.let { return failure(it) }
        if (draft.startDate < today) return failure(LedgerError.INVALID_SCHEDULE)
        val rule = draft.toRule(newId(), nextDue = draft.startDate, active = true)
        rules.save(rule)
        return LedgerResult.Success(rule)
    }

    /**
     * Mengubah aturan. Jadwal yang berubah (frekuensi atau tanggal awal) menghitung ulang kemunculan
     * berikutnya mulai [today] dan tidak mencatat ulang yang sudah lewat; jadwal yang sama tetap
     * melanjutkan dari [RecurringRule.nextDue], jadi kemunculan yang tertahan tidak hilang.
     */
    suspend fun update(id: String, draft: RecurringDraft, today: LocalDate): LedgerResult<RecurringRule> {
        val current = rules.find(id) ?: return failure(LedgerError.RECURRING_NOT_FOUND)
        validate(draft)?.let { return failure(it) }
        val scheduleChanged = draft.frequency != current.frequency || draft.startDate != current.startDate
        if (scheduleChanged && draft.startDate < today) return failure(LedgerError.INVALID_SCHEDULE)
        val nextDue = if (scheduleChanged) RecurrenceSchedule.firstOnOrAfter(draft.startDate, draft.frequency, today) else current.nextDue
        val updated = draft.toRule(id, nextDue, current.active)
        rules.save(updated)
        return LedgerResult.Success(updated)
    }

    /** Menjeda atau melanjutkan. Melanjutkan tidak mengejar kemunculan yang terlewat selama dijeda. */
    suspend fun setActive(id: String, active: Boolean, today: LocalDate): LedgerResult<RecurringRule> {
        val current = rules.find(id) ?: return failure(LedgerError.RECURRING_NOT_FOUND)
        val nextDue = if (active && current.nextDue < today) {
            RecurrenceSchedule.firstOnOrAfter(current.startDate, current.frequency, today)
        } else {
            current.nextDue
        }
        val updated = current.copy(active = active, nextDue = nextDue)
        rules.save(updated)
        return LedgerResult.Success(updated)
    }

    /** Menghapus aturan; transaksi yang sudah dicatat darinya tetap ada. */
    suspend fun delete(id: String): LedgerResult<Unit> {
        rules.find(id) ?: return failure(LedgerError.RECURRING_NOT_FOUND)
        rules.delete(id)
        return LedgerResult.Success(Unit)
    }

    /**
     * Mencatat semua kemunculan aturan aktif yang jatuh pada [today] atau sebelumnya. Aman dijalankan
     * berulang kali dan dari mana saja: tiap kemunculan punya pengenal transaksi tetap
     * (`rec-<aturan>-<tanggal>`), jadi kemunculan yang sudah tercatat dilewati walau kemajuan aturan
     * belum sempat tersimpan. Kemunculan yang gagal dicatat menahan aturan itu di tempat (yang
     * sesudahnya menunggu) dan dilaporkan di [RecurringRun.blocked]; aturan lain jalan terus.
     * Paling banyak [MAX_CATCH_UP] kemunculan per aturan per panggilan; sisanya menyusul panggilan berikutnya.
     */
    suspend fun runDue(today: LocalDate): RecurringRun {
        var recorded = 0
        val blocked = mutableListOf<BlockedOccurrence>()
        for (rule in rules.all()) {
            if (!rule.active || rule.isFinished) continue
            var current = rule
            var steps = 0
            while (steps < MAX_CATCH_UP && current.nextDue <= today && !current.isFinished) {
                val date = current.nextDue
                val transactionId = occurrenceId(current.id, date)
                if (transactions.find(TransactionId(transactionId)) == null) {
                    val failed = record(current, date, transactionId)
                    if (failed != null) {
                        blocked += BlockedOccurrence(current, date, failed)
                        break
                    }
                    recorded++
                }
                current = current.copy(nextDue = RecurrenceSchedule.next(current.startDate, current.frequency, date))
                rules.save(current)
                steps++
            }
        }
        return RecurringRun(recorded, blocked)
    }

    private suspend fun record(rule: RecurringRule, date: LocalDate, transactionId: String): LedgerError? {
        val result = when (rule.kind) {
            TransactionKind.INCOME ->
                ledger.recordIncome(NewIncome(rule.amount, rule.accountId, rule.incomeSource, date, rule.note, TransactionOrigin.RECURRING, id = transactionId))

            TransactionKind.EXPENSE -> {
                val roomId = rule.roomId ?: return LedgerError.ROOM_NOT_FOUND
                val categoryId = rule.categoryId ?: return LedgerError.CATEGORY_NOT_IN_ROOM
                ledger.recordExpense(NewExpense(rule.amount, rule.accountId, roomId, categoryId, date, rule.note, TransactionOrigin.RECURRING, id = transactionId))
            }

            TransactionKind.TRANSFER -> {
                val to = rule.toAccountId ?: return LedgerError.ACCOUNT_NOT_FOUND
                ledger.recordTransfer(NewTransfer(rule.amount, rule.accountId, to, date, rule.note, TransactionOrigin.RECURRING, id = transactionId))
            }
        }
        return (result as? LedgerResult.Failure)?.error
    }

    private suspend fun validate(draft: RecurringDraft): LedgerError? {
        if (!draft.amount.isPositive) return LedgerError.AMOUNT_NOT_POSITIVE
        if ((draft.note?.trim()?.length ?: 0) > LedgerService.NOTE_MAX) return LedgerError.NOTE_TOO_LONG
        if ((draft.incomeSource?.trim()?.length ?: 0) > LedgerService.SOURCE_MAX) return LedgerError.SOURCE_TOO_LONG
        if (draft.endDate != null && draft.endDate < draft.startDate) return LedgerError.INVALID_SCHEDULE
        checkAccount(draft.accountId, draft.amount)?.let { return it }
        when (draft.kind) {
            TransactionKind.INCOME -> Unit

            TransactionKind.EXPENSE -> {
                val room = rooms.find(draft.roomId ?: return LedgerError.ROOM_NOT_FOUND) ?: return LedgerError.ROOM_NOT_FOUND
                if (room.archived) return LedgerError.ROOM_ARCHIVED
                val category = rooms.findCategory(draft.categoryId ?: return LedgerError.CATEGORY_NOT_IN_ROOM)
                if (category == null || category.roomId != room.id || category.archived) return LedgerError.CATEGORY_NOT_IN_ROOM
            }

            TransactionKind.TRANSFER -> {
                val to = draft.toAccountId ?: return LedgerError.ACCOUNT_NOT_FOUND
                if (to == draft.accountId) return LedgerError.SAME_ACCOUNT
                checkAccount(to, draft.amount)?.let { return it }
            }
        }
        return null
    }

    private suspend fun checkAccount(id: AccountId, amount: Money): LedgerError? {
        val account = accounts.find(id) ?: return LedgerError.ACCOUNT_NOT_FOUND
        if (account.archived) return LedgerError.ACCOUNT_ARCHIVED
        if (account.currency != amount.currency) return LedgerError.CURRENCY_MISMATCH
        return null
    }

    /** Hanya isian yang berlaku bagi jenisnya yang dibawa; sisanya dibuang supaya aturan tidak menyimpan sampah. */
    private fun RecurringDraft.toRule(id: String, nextDue: LocalDate, active: Boolean) = RecurringRule(
        id = id,
        kind = kind,
        amount = amount,
        accountId = accountId,
        toAccountId = if (kind == TransactionKind.TRANSFER) toAccountId else null,
        roomId = if (kind == TransactionKind.EXPENSE) roomId else null,
        categoryId = if (kind == TransactionKind.EXPENSE) categoryId else null,
        incomeSource = if (kind == TransactionKind.INCOME) incomeSource?.trim()?.takeIf { it.isNotEmpty() } else null,
        note = note?.trim()?.takeIf { it.isNotEmpty() },
        frequency = frequency,
        startDate = startDate,
        nextDue = nextDue,
        endDate = endDate,
        active = active,
    )

    companion object {
        /** Batas kemunculan per aturan per panggilan [runDue]; aturan harian yang lama tak dibuka setahun lebih menyusul bertahap. */
        const val MAX_CATCH_UP = 400

        fun occurrenceId(ruleId: String, date: LocalDate) = "rec-$ruleId-$date"
    }
}
