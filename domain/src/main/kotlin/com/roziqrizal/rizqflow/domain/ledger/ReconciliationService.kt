package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.RoomKind
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/** Isi S25: akun, saldo menurut catatan, dan ruang yang diusulkan untuk selisihnya. */
data class ReconciliationOverview(val account: Account, val recorded: Money, val suggestedRoomId: RoomId?)

/**
 * Koreksi saldo (S25, flow "Kasus tunai, QRIS, dan e-wallet ditangani sama": semuanya berujung pada
 * saldo sebuah akun). Selisih dicatat sebagai transaksi biasa kategori sistem `Tak terlacak`
 * ([TransactionOrigin.CORRECTION]), jadi ikut dihitung dalam jatah ruang seperti pengeluaran lain.
 * Tidak punya notifikasi sendiri; nada netral, tanpa kata yang menghakimi.
 */
class ReconciliationService(
    private val accounts: AccountRepository,
    private val rooms: RoomRepository,
    private val transactions: TransactionRepository,
    private val ledger: LedgerService,
) {
    suspend fun overview(accountId: AccountId): ReconciliationOverview? {
        val account = accounts.find(accountId) ?: return null
        return ReconciliationOverview(account, accounts.balance(accountId), suggestedRoomFor(accountId))
    }

    /** Ruang dari pengeluaran terakhir di akun ini; kalau belum ada, ruang bertipe Mencukupi; kalau tidak ada juga, ruang aktif pertama. */
    private suspend fun suggestedRoomFor(accountId: AccountId): RoomId? {
        transactions.latestExpenseRoom(accountId)?.let { return it }
        val active = rooms.activeRooms()
        return active.firstOrNull { it.kind == RoomKind.MENCUKUPI }?.id ?: active.firstOrNull()?.id
    }

    /**
     * [actual] dibandingkan saldo tercatat. Sama: tidak mencatat apa pun ([LedgerResult.Success] berisi
     * null). Lebih kecil: pengeluaran Tak terlacak di [roomId]. Lebih besar: pemasukan (pemasukan yang
     * belum tercatat), tidak dialirkan ke ruang mana pun seperti pemasukan biasa lain. Selalu memperbarui
     * [Account.lastReconciledOn] ke [date], termasuk saat selisihnya nol.
     */
    suspend fun correct(accountId: AccountId, actual: Money, roomId: RoomId, date: LocalDate): LedgerResult<TransactionId?> {
        val account = accounts.find(accountId) ?: return failure(LedgerError.ACCOUNT_NOT_FOUND)
        if (actual.isNegative) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        val diff = actual - accounts.balance(accountId)

        val id: TransactionId? = when {
            diff.isZero -> null

            diff.isNegative -> {
                val room = rooms.find(roomId) ?: return failure(LedgerError.ROOM_NOT_FOUND)
                if (room.archived) return failure(LedgerError.ROOM_ARCHIVED)
                val category = rooms.categories(roomId).firstOrNull { it.name == RoomTemplates.UNTRACKED }
                    ?: return failure(LedgerError.UNTRACKED_CATEGORY_MISSING)
                when (val result = ledger.recordExpense(NewExpense(diff.abs(), accountId, roomId, category.id, date, origin = TransactionOrigin.CORRECTION))) {
                    is LedgerResult.Success -> result.value.id
                    is LedgerResult.Failure -> return result
                }
            }

            else -> when (val result = ledger.recordIncome(NewIncome(diff, accountId, CORRECTION_SOURCE, date, origin = TransactionOrigin.CORRECTION))) {
                is LedgerResult.Success -> result.value.transaction.id
                is LedgerResult.Failure -> return result
            }
        }

        accounts.save(account.copy(lastReconciledOn = date))
        return LedgerResult.Success(id)
    }

    companion object {
        const val CORRECTION_SOURCE = "Koreksi saldo"
    }
}
