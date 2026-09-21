package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationEngine
import com.roziqrizal.rizqflow.domain.allocation.AllocationResult
import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/** Alasan sebuah perintah ditolak. Layar memetakannya ke pesan; tidak ada yang berupa galat program. */
enum class LedgerError {
    AMOUNT_NOT_POSITIVE,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_ARCHIVED,
    CURRENCY_MISMATCH,
    ROOM_NOT_FOUND,
    ROOM_ARCHIVED,
    CATEGORY_NOT_IN_ROOM,
    SAME_ACCOUNT,
    TRANSACTION_NOT_FOUND,
    NOT_AN_INCOME,
    NOTE_TOO_LONG,
    SOURCE_TOO_LONG,
    INVALID_NAME,
    NAME_TAKEN,
    ROOM_LIMIT_REACHED,
    RULES_NOT_100_PERCENT,
    RULES_INVALID,
    WORKSPACE_NOT_EMPTY,
}

sealed interface LedgerResult<out T> {
    data class Success<out T>(val value: T) : LedgerResult<T>

    data class Failure(val error: LedgerError) : LedgerResult<Nothing>
}

internal fun failure(error: LedgerError): LedgerResult.Failure = LedgerResult.Failure(error)

data class NewIncome(
    val amount: Money,
    val accountId: AccountId,
    val source: String?,
    val occurredOn: LocalDate,
    val note: String? = null,
    val origin: TransactionOrigin = TransactionOrigin.MANUAL,
)

data class NewExpense(
    val amount: Money,
    val accountId: AccountId,
    val roomId: RoomId,
    val categoryId: CategoryId,
    val occurredOn: LocalDate,
    val note: String? = null,
    val origin: TransactionOrigin = TransactionOrigin.MANUAL,
)

data class NewTransfer(
    val amount: Money,
    val from: AccountId,
    val to: AccountId,
    val occurredOn: LocalDate,
    val note: String? = null,
)

/** Pemasukan yang tersimpan beserta hasil alokasinya, termasuk bagian yang belum dialirkan. */
data class IncomeReceipt(val transaction: MoneyTransaction, val allocation: AllocationResult)

/**
 * Mencatat, mengubah, dan menghapus transaksi (S06 sampai S09).
 *
 * Pemasukan dialirkan sesuai aturan alokasi saat itu dan **potretnya disimpan** (keputusan
 * 2026-09-21): mengubah aturan sesudahnya tidak mengubah riwayat, dan mengubah nominal
 * menghitung ulang dengan persentase potret itu. Tanpa aturan (mulai kosong), pemasukan tetap
 * satu transaksi dan seluruhnya menjadi "belum dialirkan".
 */
class LedgerService(
    private val accounts: AccountRepository,
    private val rooms: RoomRepository,
    private val transactions: TransactionRepository,
    private val newId: () -> String,
    private val nowMillis: () -> Long,
) {

    /** Pratinjau S07: bagaimana [amount] akan dialirkan dengan aturan sekarang. Tidak menyimpan apa pun. */
    suspend fun previewIncome(amount: Money): AllocationResult = AllocationEngine.allocate(amount, rooms.rules())

    suspend fun recordIncome(command: NewIncome): LedgerResult<IncomeReceipt> {
        if (!command.amount.isPositive) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        val note = cleanNote(command.note) ?: return failure(LedgerError.NOTE_TOO_LONG)
        val source = cleanSource(command.source) ?: return failure(LedgerError.SOURCE_TOO_LONG)
        checkAccount(command.accountId, command.amount)?.let { return failure(it) }

        val rules = rooms.rules()
        val allocation = AllocationEngine.allocate(command.amount, rules)
        val now = nowMillis()
        val transaction = MoneyTransaction(
            id = TransactionId(newId()),
            kind = TransactionKind.INCOME,
            amount = command.amount,
            accountId = command.accountId,
            incomeSource = source.value,
            occurredOn = command.occurredOn,
            note = note.value,
            origin = command.origin,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        transactions.saveIncome(transaction, snapshotEntries(transaction.id, rules, allocation, now))
        return LedgerResult.Success(IncomeReceipt(transaction, allocation))
    }

    suspend fun recordExpense(command: NewExpense): LedgerResult<MoneyTransaction> {
        if (!command.amount.isPositive) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        val note = cleanNote(command.note) ?: return failure(LedgerError.NOTE_TOO_LONG)
        checkAccount(command.accountId, command.amount)?.let { return failure(it) }

        val room = rooms.find(command.roomId) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        if (room.archived) return failure(LedgerError.ROOM_ARCHIVED)
        val category = rooms.findCategory(command.categoryId)
        if (category == null || category.roomId != room.id || category.archived) return failure(LedgerError.CATEGORY_NOT_IN_ROOM)

        val now = nowMillis()
        val transaction = MoneyTransaction(
            id = TransactionId(newId()),
            kind = TransactionKind.EXPENSE,
            amount = command.amount,
            accountId = command.accountId,
            roomId = room.id,
            categoryId = category.id,
            occurredOn = command.occurredOn,
            note = note.value,
            origin = command.origin,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        transactions.save(transaction)
        return LedgerResult.Success(transaction)
    }

    suspend fun recordTransfer(command: NewTransfer): LedgerResult<MoneyTransaction> {
        if (!command.amount.isPositive) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        if (command.from == command.to) return failure(LedgerError.SAME_ACCOUNT)
        val note = cleanNote(command.note) ?: return failure(LedgerError.NOTE_TOO_LONG)
        checkAccount(command.from, command.amount)?.let { return failure(it) }
        checkAccount(command.to, command.amount)?.let { return failure(it) }

        val now = nowMillis()
        val transaction = MoneyTransaction(
            id = TransactionId(newId()),
            kind = TransactionKind.TRANSFER,
            amount = command.amount,
            accountId = command.from,
            toAccountId = command.to,
            occurredOn = command.occurredOn,
            note = note.value,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        transactions.save(transaction)
        return LedgerResult.Success(transaction)
    }

    /**
     * Mengubah nominal pemasukan (S09). Alokasi dihitung ulang dengan persentase potret lama;
     * ruang dan persentasenya tidak berubah, dan aturan alokasi saat ini tidak dipakai.
     */
    suspend fun editIncomeAmount(id: TransactionId, newAmount: Money): LedgerResult<IncomeReceipt> {
        val current = transactions.find(id) ?: return failure(LedgerError.TRANSACTION_NOT_FOUND)
        if (current.kind != TransactionKind.INCOME) return failure(LedgerError.NOT_AN_INCOME)
        if (!newAmount.isPositive) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        if (newAmount.currency != current.amount.currency) return failure(LedgerError.CURRENCY_MISMATCH)

        val old = transactions.entriesOf(id)
        val rules = old.map { AllocationRule(it.roomId, it.share) }
        val allocation = AllocationEngine.allocate(newAmount, rules)
        val entries = old.zip(allocation.shares) { entry, share -> entry.copy(amount = share.amount) }
        val updated = current.copy(amount = newAmount, updatedAtMillis = nowMillis())
        transactions.replaceIncome(updated, entries)
        return LedgerResult.Success(IncomeReceipt(updated, allocation))
    }

    suspend fun delete(id: TransactionId): LedgerResult<Unit> {
        transactions.find(id) ?: return failure(LedgerError.TRANSACTION_NOT_FOUND)
        transactions.delete(id)
        return LedgerResult.Success(Unit)
    }

    private suspend fun checkAccount(id: AccountId, amount: Money): LedgerError? {
        val account = accounts.find(id) ?: return LedgerError.ACCOUNT_NOT_FOUND
        if (account.archived) return LedgerError.ACCOUNT_ARCHIVED
        if (account.currency != amount.currency) return LedgerError.CURRENCY_MISMATCH
        return null
    }

    /**
     * Satu baris potret per ruang yang punya bagian (persentase lebih dari nol), termasuk yang
     * jumlahnya 0 karena nominalnya kecil: persentasenya harus ikut tersimpan supaya bisa dihitung
     * ulang saat nominal diubah menjadi lebih besar.
     */
    private fun snapshotEntries(
        incomeId: TransactionId,
        rules: List<AllocationRule>,
        allocation: AllocationResult,
        now: Long,
    ): List<AllocationEntry> = rules.zip(allocation.shares)
        .filter { (rule, _) -> rule.share.value > 0 }
        .map { (rule, share) -> AllocationEntry(newId(), incomeId, rule.roomId, rule.share, share.amount, now) }

    private class Cleaned(val value: String?)

    /** Catatan dirapikan; `null` bila terlalu panjang. Kosong menjadi nilai kosong yang sah. */
    private fun cleanNote(raw: String?): Cleaned? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() }
        return if (text != null && text.length > NOTE_MAX) null else Cleaned(text)
    }

    private fun cleanSource(raw: String?): Cleaned? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() }
        return if (text != null && text.length > SOURCE_MAX) null else Cleaned(text)
    }

    companion object {
        const val NOTE_MAX = 200
        const val SOURCE_MAX = 40
    }
}
