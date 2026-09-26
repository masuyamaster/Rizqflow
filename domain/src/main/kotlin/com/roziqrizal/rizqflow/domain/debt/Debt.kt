package com.roziqrizal.rizqflow.domain.debt

import com.roziqrizal.rizqflow.domain.ledger.AccountRepository
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.NewLoanMovement
import com.roziqrizal.rizqflow.domain.ledger.SettingsRepository
import com.roziqrizal.rizqflow.domain.ledger.TransactionRepository
import com.roziqrizal.rizqflow.domain.ledger.failure
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.reminder.DueReminders
import com.roziqrizal.rizqflow.domain.reminder.DueStage
import java.time.LocalDate

/*
 * Utang-piutang (Tahap 10, Gratis): pinjam ke atau dari teman dan keluarga, dengan pelunasan
 * sebagian atau penuh. Perpindahan uangnya adalah transaksi `LOAN_OUT` atau `LOAN_IN` yang hanya
 * menggeser saldo akun: meminjamkan bukan pengeluaran (tidak memakan jatah ruang) dan meminjam
 * bukan pemasukan (tidak dialirkan ke ruang). Sisa piutang lancar dan utang jangka pendek menjadi
 * saran untuk profil harta zakat (lihat [DebtService.zakatSuggestion]); tidak pernah masuk otomatis.
 */

/** Arah pinjaman: [LENT] piutang (saya meminjamkan, orang lain berutang), [BORROWED] utang (saya meminjam). */
enum class DebtDirection { LENT, BORROWED }

/**
 * Satu utang atau piutang. [principal] adalah pokoknya; sisanya dihitung dari [DebtPayment] yang
 * tersimpan, tidak disimpan sendiri. [accountId] dan [initialTransactionId] terisi bila perpindahan
 * uang awalnya dicatat ke saldo; keduanya kosong untuk pinjaman lama yang uangnya bergerak sebelum
 * dicatat di aplikasi. [collectible] hanya bermakna untuk piutang: diperkirakan kembali (lancar)
 * atau tidak (macet); hanya yang lancar menjadi saran harta zakat.
 */
data class Debt(
    val id: String,
    val direction: DebtDirection,
    val party: String,
    val principal: Money,
    val accountId: AccountId? = null,
    val initialTransactionId: TransactionId? = null,
    val startDate: LocalDate,
    val dueDate: LocalDate? = null,
    val note: String? = null,
    val collectible: Boolean = true,
) {
    init {
        require(party.isNotBlank() && party.length <= PARTY_MAX) { "Nama pihak 1 sampai $PARTY_MAX karakter" }
        require(principal.isPositive) { "Pokok pinjaman harus lebih dari nol" }
        require(dueDate == null || dueDate >= startDate) { "Jatuh tempo tidak boleh sebelum tanggal pinjam" }
    }

    companion object {
        const val PARTY_MAX = 40
    }
}

/** Satu pelunasan (sebagian atau penuh). [transactionId] kosong bila tidak dicatat ke saldo akun. */
data class DebtPayment(
    val id: String,
    val debtId: String,
    val amount: Money,
    val paidOn: LocalDate,
    val transactionId: TransactionId? = null,
)

interface DebtRepository {
    suspend fun all(): List<Debt>

    suspend fun find(id: String): Debt?

    suspend fun save(debt: Debt)

    /** Menghapus utang beserta baris pelunasannya; transaksinya dihapus pemanggil ([DebtService.delete]). */
    suspend fun delete(id: String)

    /** Semua pelunasan, terlama dulu. */
    suspend fun allPayments(): List<DebtPayment>

    suspend fun findPayment(id: String): DebtPayment?

    suspend fun savePayment(payment: DebtPayment)

    suspend fun deletePayment(id: String)
}

enum class DebtStatus { OVERDUE, DUE_TODAY, DUE_SOON, OPEN, SETTLED }

/** Isian layar untuk mencatat utang atau piutang baru. */
data class DebtDraft(
    val direction: DebtDirection,
    val party: String,
    val amount: Money,
    val startDate: LocalDate,
    val dueDate: LocalDate? = null,
    val note: String? = null,
    val collectible: Boolean = true,
    /** Akun yang uangnya bergerak; hanya dipakai bila [recordMovement]. */
    val accountId: AccountId? = null,
    /** false untuk pinjaman lama yang uangnya bergerak sebelum dicatat: saldo tidak disentuh. */
    val recordMovement: Boolean = true,
)

/** Perubahan yang boleh setelah dicatat: pokok, arah, dan perpindahan uang awal terikat pada transaksinya. */
data class DebtEdit(val party: String, val dueDate: LocalDate?, val note: String?, val collectible: Boolean)

/** Satu baris daftar: utang dengan sisanya dan riwayat pelunasannya (terbaru dulu). */
data class DebtRow(
    val debt: Debt,
    val payments: List<DebtPayment>,
    val accountName: String?,
) {
    val repaid: Money get() = payments.fold(Money.rupiah(0)) { total, payment -> total + payment.amount }
    val outstanding: Money get() = debt.principal - repaid
    val isSettled: Boolean get() = !outstanding.isPositive

    fun statusOn(today: LocalDate): DebtStatus {
        val due = debt.dueDate
        return when {
            isSettled -> DebtStatus.SETTLED
            due == null -> DebtStatus.OPEN
            due < today -> DebtStatus.OVERDUE
            due == today -> DebtStatus.DUE_TODAY
            due <= today.plusDays(DueReminders.SOON_DAYS) -> DebtStatus.DUE_SOON
            else -> DebtStatus.OPEN
        }
    }
}

/** Daftar dengan total sisa piutang dan sisa utang (hanya yang belum lunas). */
data class DebtOverview(val rows: List<DebtRow>, val receivable: Money, val payable: Money)

/** Hasil satu pelunasan; cukup untuk membatalkannya lewat [DebtService.undoRepay]. */
data class DebtRepayment(val payment: DebtPayment, val settled: Boolean)

/**
 * Saran nilai untuk profil harta zakat (S15), tidak pernah masuk otomatis. [receivable] adalah sisa
 * piutang yang diperkirakan kembali; [shortTermDebt] adalah sisa utang yang jatuh tempo dalam
 * [DebtService.SHORT_TERM_MONTHS] bulan atau tanpa jatuh tempo. Pembatasan ini asumsi kerja yang
 * belum diverifikasi dengan kitab, sama seperti asumsi fikih zakat lainnya.
 */
data class ZakatDebtSuggestion(val receivable: Money, val shortTermDebt: Money)

class DebtService(
    private val debts: DebtRepository,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val ledger: LedgerService,
    private val newId: () -> String,
) {

    /** Yang belum lunas di atas (terlambat dan terdekat dulu, tanpa jatuh tempo di bawahnya), lalu yang lunas. */
    suspend fun overview(today: LocalDate): DebtOverview {
        val allAccounts = accounts.allAccounts().associateBy { it.id }
        val paymentsByDebt = debts.allPayments().groupBy { it.debtId }
        val rows = debts.all()
            .map { debt ->
                DebtRow(
                    debt = debt,
                    payments = paymentsByDebt[debt.id].orEmpty().sortedWith(compareByDescending<DebtPayment> { it.paidOn }.thenByDescending { it.id }),
                    accountName = debt.accountId?.let { allAccounts[it]?.name },
                )
            }
            .sortedWith(
                compareBy<DebtRow> { it.isSettled }
                    .thenBy { it.debt.dueDate ?: LocalDate.MAX }
                    .thenByDescending { it.debt.startDate }
                    .thenBy { it.debt.party.lowercase() }
                    .thenBy { it.debt.id },
            )
        val open = rows.filter { !it.isSettled }
        return DebtOverview(
            rows = rows,
            receivable = open.filter { it.debt.direction == DebtDirection.LENT }.fold(Money.rupiah(0)) { total, row -> total + row.outstanding },
            payable = open.filter { it.debt.direction == DebtDirection.BORROWED }.fold(Money.rupiah(0)) { total, row -> total + row.outstanding },
        )
    }

    /**
     * Mencatat utang atau piutang baru. Dengan [DebtDraft.recordMovement], uangnya langsung
     * bergerak di akun (piutang: keluar; utang: masuk) lewat transaksi pinjaman pada
     * [DebtDraft.startDate]; bila gagal, tidak ada yang tersimpan.
     */
    suspend fun create(draft: DebtDraft): LedgerResult<Debt> {
        validate(draft.party, draft.startDate, draft.dueDate, draft.note)?.let { return failure(it) }
        if (!draft.amount.isPositive) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        val id = newId()
        val party = draft.party.trim()
        var accountId: AccountId? = null
        var initial: TransactionId? = null
        if (draft.recordMovement) {
            accountId = draft.accountId ?: return failure(LedgerError.ACCOUNT_NOT_FOUND)
            val kind = if (draft.direction == DebtDirection.LENT) TransactionKind.LOAN_OUT else TransactionKind.LOAN_IN
            val note = if (draft.direction == DebtDirection.LENT) "Pinjaman ke $party" else "Pinjaman dari $party"
            when (val result = ledger.recordLoanMovement(NewLoanMovement(kind, draft.amount, accountId, draft.startDate, note, id = "debt-$id-open"))) {
                is LedgerResult.Success -> initial = result.value.id
                is LedgerResult.Failure -> return failure(result.error)
            }
        }
        val debt = Debt(
            id = id, direction = draft.direction, party = party, principal = draft.amount,
            accountId = accountId, initialTransactionId = initial, startDate = draft.startDate, dueDate = draft.dueDate,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() }, collectible = draft.collectible,
        )
        debts.save(debt)
        return LedgerResult.Success(debt)
    }

    suspend fun update(id: String, edit: DebtEdit): LedgerResult<Debt> {
        val current = debts.find(id) ?: return failure(LedgerError.DEBT_NOT_FOUND)
        validate(edit.party, current.startDate, edit.dueDate, edit.note)?.let { return failure(it) }
        val updated = current.copy(
            party = edit.party.trim(), dueDate = edit.dueDate,
            note = edit.note?.trim()?.takeIf { it.isNotEmpty() }, collectible = edit.collectible,
        )
        debts.save(updated)
        return LedgerResult.Success(updated)
    }

    /**
     * Mencatat pelunasan sebagian atau penuh pada [paidOn]. Dengan [accountId], uangnya bergerak di
     * akun itu (piutang dilunasi: masuk; utang dibayar: keluar); tanpa akun, hanya sisa yang
     * berkurang dan saldo tidak disentuh. Tidak boleh melebihi sisa.
     */
    suspend fun repay(id: String, amount: Money, paidOn: LocalDate, accountId: AccountId?): LedgerResult<DebtRepayment> {
        val debt = debts.find(id) ?: return failure(LedgerError.DEBT_NOT_FOUND)
        if (!amount.isPositive) return failure(LedgerError.AMOUNT_NOT_POSITIVE)
        val outstanding = outstandingOf(debt)
        if (amount > outstanding) return failure(LedgerError.DEBT_OVERPAID)

        val paymentId = newId()
        var transactionId: TransactionId? = null
        if (accountId != null) {
            val kind = if (debt.direction == DebtDirection.LENT) TransactionKind.LOAN_IN else TransactionKind.LOAN_OUT
            val note = if (debt.direction == DebtDirection.LENT) "Pelunasan dari ${debt.party}" else "Bayar utang ke ${debt.party}"
            when (val result = ledger.recordLoanMovement(NewLoanMovement(kind, amount, accountId, paidOn, note, id = "debt-$id-pay-$paymentId"))) {
                is LedgerResult.Success -> transactionId = result.value.id
                is LedgerResult.Failure -> return failure(result.error)
            }
        }
        val payment = DebtPayment(paymentId, id, amount, paidOn, transactionId)
        debts.savePayment(payment)
        return LedgerResult.Success(DebtRepayment(payment, settled = amount == outstanding))
    }

    /** Membatalkan [repay] (Urungkan): menghapus pelunasan beserta transaksinya. Aman dipanggil bila sudah terhapus. */
    suspend fun undoRepay(payment: DebtPayment) {
        removePayment(payment.id)
    }

    /** Menghapus satu pelunasan yang salah catat, beserta transaksinya; sisa utang bertambah kembali. */
    suspend fun removePayment(paymentId: String): LedgerResult<Unit> {
        val payment = debts.findPayment(paymentId) ?: return failure(LedgerError.DEBT_NOT_FOUND)
        payment.transactionId?.let { if (transactions.find(it) != null) transactions.delete(it) }
        debts.deletePayment(paymentId)
        return LedgerResult.Success(Unit)
    }

    /**
     * Menghapus utang beserta semua pelunasan dan transaksi saldonya (transaksi pinjaman tidak bisa
     * dihapus dari daftar Transaksi, jadi tidak dibiarkan menggantung). Saldo akun kembali seperti
     * sebelum utang ini dicatat.
     */
    suspend fun delete(id: String): LedgerResult<Unit> {
        val debt = debts.find(id) ?: return failure(LedgerError.DEBT_NOT_FOUND)
        debts.allPayments().filter { it.debtId == id }.forEach { removePayment(it.id) }
        debts.delete(id)
        debt.initialTransactionId?.let { if (transactions.find(it) != null) transactions.delete(it) }
        return LedgerResult.Success(Unit)
    }

    /** Saran untuk profil harta zakat: sisa piutang lancar dan sisa utang jangka pendek per [today]. */
    suspend fun zakatSuggestion(today: LocalDate): ZakatDebtSuggestion {
        val open = overview(today).rows.filter { !it.isSettled }
        val horizon = today.plusMonths(SHORT_TERM_MONTHS)
        return ZakatDebtSuggestion(
            receivable = open.filter { it.debt.direction == DebtDirection.LENT && it.debt.collectible }.fold(Money.rupiah(0)) { total, row -> total + row.outstanding },
            shortTermDebt = open
                .filter { it.debt.direction == DebtDirection.BORROWED && (it.debt.dueDate == null || it.debt.dueDate <= horizon) }
                .fold(Money.rupiah(0)) { total, row -> total + row.outstanding },
        )
    }

    private suspend fun outstandingOf(debt: Debt): Money =
        debt.principal - debts.allPayments().filter { it.debtId == debt.id }.fold(Money.rupiah(0)) { total, payment -> total + payment.amount }

    private fun validate(party: String, startDate: LocalDate, dueDate: LocalDate?, note: String?): LedgerError? {
        val name = party.trim()
        if (name.isEmpty() || name.length > Debt.PARTY_MAX) return LedgerError.INVALID_PARTY
        if (dueDate != null && dueDate < startDate) return LedgerError.INVALID_DUE_DATE
        if ((note?.trim()?.length ?: 0) > LedgerService.NOTE_MAX) return LedgerError.NOTE_TOO_LONG
        return null
    }

    companion object {
        /** Utang yang jatuh tempo dalam sekian bulan (atau tanpa jatuh tempo) dianggap jangka pendek. */
        const val SHORT_TERM_MONTHS = 12L
    }
}

/** Utang-piutang yang perlu disapa hari ini; [outstanding] adalah sisanya saat itu. */
data class DebtReminder(val debt: Debt, val outstanding: Money, val stage: DueStage, val daysLeft: Int)

/**
 * Pengingat jatuh tempo utang-piutang yang menumpang jadwal harian pengingat malam (S26), seperti
 * `BillReminderService`: aturan sapaannya di [DueReminders], penandanya
 * `debt_reminder_notified_<utang>`. Utang tanpa jatuh tempo dan yang sudah lunas tidak disapa. Baik
 * utang saya maupun piutang disapa; kata-katanya beda (menagih dengan lembut) di lapisan notifikasi.
 * Pelunasan sebagian tidak mengubah jatuh tempo, jadi tidak memulai daur baru.
 */
class DebtReminderService(
    private val debts: DebtRepository,
    private val settings: SettingsRepository,
) {
    suspend fun dueReminders(today: LocalDate): List<DebtReminder> {
        val paidByDebt = debts.allPayments().groupBy { it.debtId }
        return debts.all()
            .filter { it.dueDate != null }
            .sortedBy { it.dueDate }
            .mapNotNull { debt ->
                val due = debt.dueDate ?: return@mapNotNull null
                val outstanding = debt.principal - paidByDebt[debt.id].orEmpty().fold(Money.rupiah(0)) { total, payment -> total + payment.amount }
                if (!outstanding.isPositive) return@mapNotNull null
                val (stage, daysLeft) = DueReminders.stageOf(today, due) ?: return@mapNotNull null
                if (DueReminders.alreadyNotified(settings, keyFor(debt.id), due, stage)) null else DebtReminder(debt, outstanding, stage, daysLeft)
            }
    }

    suspend fun markNotified(reminder: DebtReminder) {
        val due = reminder.debt.dueDate ?: return
        DueReminders.markNotified(settings, keyFor(reminder.debt.id), due, reminder.stage)
    }

    private fun keyFor(debtId: String) = "$KEY_PREFIX$debtId"

    private companion object {
        const val KEY_PREFIX = "debt_reminder_notified_"
    }
}
