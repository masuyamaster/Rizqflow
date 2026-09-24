package com.roziqrizal.rizqflow.domain.bill

import com.roziqrizal.rizqflow.domain.ledger.AccountRepository
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.NewExpense
import com.roziqrizal.rizqflow.domain.ledger.RoomRepository
import com.roziqrizal.rizqflow.domain.ledger.SettingsRepository
import com.roziqrizal.rizqflow.domain.ledger.TransactionRepository
import com.roziqrizal.rizqflow.domain.ledger.failure
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionOrigin
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.recurring.Frequency
import com.roziqrizal.rizqflow.domain.recurring.RecurrenceSchedule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Tagihan dan cicilan (Tahap 10, Gratis): kewajiban bayar yang punya tanggal jatuh tempo, sekali
 * bayar atau berulang, dengan jumlah cicilan yang boleh dibatasi. Beda dari transaksi berulang:
 * pembayarannya terjadi di luar aplikasi, jadi tagihan tidak pernah dicatat otomatis. Pengguna
 * mengetuk Bayar; barulah pengeluaran tercatat dan jatuh tempo maju. Pengingatnya menumpang
 * jadwal harian pengingat malam (lihat [BillReminderService]).
 */

/**
 * Satu tagihan. [nextDue] adalah jatuh tempo yang belum dibayar; maju satu kemunculan setiap
 * [BillService.pay]. [frequency] null berarti sekali bayar. [totalInstallments] null berarti tanpa
 * batas (langganan, listrik); bila terisi, tagihan lunas setelah [paidCount] mencapainya.
 * [startDate] adalah jangkar penghitungan jatuh tempo, supaya tagihan tanggal 31 tidak bergeser
 * setelah melewati bulan pendek (lihat [RecurrenceSchedule]).
 */
data class Bill(
    val id: String,
    val name: String,
    val amount: Money,
    val accountId: AccountId,
    val roomId: RoomId,
    val categoryId: CategoryId,
    val note: String? = null,
    val frequency: Frequency? = null,
    val startDate: LocalDate,
    val nextDue: LocalDate,
    val totalInstallments: Int? = null,
    val paidCount: Int = 0,
    /** false berarti dijeda pengguna: tidak diingatkan dan tidak ditandai terlambat. */
    val active: Boolean = true,
) {
    init {
        require(name.isNotBlank() && name.length <= NAME_MAX) { "Nama tagihan 1 sampai $NAME_MAX karakter" }
        require(amount.isPositive) { "Nominal tagihan harus lebih dari nol" }
        require(paidCount >= 0) { "Jumlah cicilan terbayar tidak boleh negatif" }
        require(totalInstallments == null || (frequency != null && totalInstallments >= 1)) { "Jumlah cicilan minimal 1 dan hanya untuk tagihan berulang" }
    }

    val isFinished: Boolean
        get() = when {
            frequency == null -> paidCount >= 1
            totalInstallments != null -> paidCount >= totalInstallments
            else -> false
        }

    /** Cicilan yang belum dibayar; null bila tanpa batas. */
    val remainingInstallments: Int? get() = totalInstallments?.let { (it - paidCount).coerceAtLeast(0) }

    fun statusOn(today: LocalDate): BillStatus = when {
        isFinished -> BillStatus.FINISHED
        !active -> BillStatus.PAUSED
        nextDue < today -> BillStatus.OVERDUE
        nextDue == today -> BillStatus.DUE_TODAY
        nextDue <= today.plusDays(SOON_DAYS) -> BillStatus.DUE_SOON
        else -> BillStatus.UPCOMING
    }

    companion object {
        const val NAME_MAX = 40

        /** Jatuh tempo dalam [SOON_DAYS] hari ke depan sudah dianggap dekat. */
        const val SOON_DAYS = 3L
    }
}

enum class BillStatus { OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING, PAUSED, FINISHED }

interface BillRepository {
    /** Semua tagihan, termasuk yang dijeda dan lunas. */
    suspend fun all(): List<Bill>

    suspend fun find(id: String): Bill?

    suspend fun save(bill: Bill)

    suspend fun delete(id: String)
}

/**
 * Isian layar untuk membuat atau mengubah tagihan. [dueDate] adalah jatuh tempo berikutnya yang
 * belum dibayar (boleh sudah lewat: tagihan yang terlambat memang perlu dicatat). [alreadyPaid]
 * adalah cicilan yang sudah dibayar sebelum tagihan ini dicatat di aplikasi.
 */
data class BillDraft(
    val name: String,
    val amount: Money,
    val accountId: AccountId,
    val roomId: RoomId,
    val categoryId: CategoryId,
    val note: String? = null,
    val frequency: Frequency? = null,
    val dueDate: LocalDate,
    val totalInstallments: Int? = null,
    val alreadyPaid: Int = 0,
)

/** Satu baris daftar dengan nama tujuannya. [usable]: akun, ruang, dan kategorinya masih aktif, jadi bisa dibayar. */
data class BillRow(
    val bill: Bill,
    val accountName: String,
    val roomName: String,
    val categoryName: String,
    val usable: Boolean,
)

/** Hasil satu kali bayar; [previous] dan [transactionId] cukup untuk membatalkannya lewat [BillService.undoPay]. */
data class BillPayment(val previous: Bill, val transactionId: TransactionId, val updated: Bill)

class BillService(
    private val bills: BillRepository,
    private val accounts: AccountRepository,
    private val rooms: RoomRepository,
    private val transactions: TransactionRepository,
    private val ledger: LedgerService,
    private val newId: () -> String,
) {

    /** Yang perlu dibayar di atas (terlambat dulu), lalu yang dijeda, lalu yang sudah lunas. */
    suspend fun list(today: LocalDate): List<BillRow> {
        val allAccounts = accounts.allAccounts().associateBy { it.id }
        val allRooms = rooms.allRooms().associateBy { it.id }
        val allCategories = rooms.allCategories().associateBy { it.id }
        return bills.all()
            .sortedWith(compareBy<Bill> { rank(it, today) }.thenBy { it.nextDue }.thenBy { it.name.lowercase() }.thenBy { it.id })
            .map { bill ->
                val account = allAccounts[bill.accountId]
                val room = allRooms[bill.roomId]
                val category = allCategories[bill.categoryId]
                BillRow(
                    bill = bill,
                    accountName = account?.name.orEmpty(),
                    roomName = room?.name.orEmpty(),
                    categoryName = category?.name.orEmpty(),
                    usable = account != null && !account.archived && room != null && !room.archived && category != null && !category.archived,
                )
            }
    }

    private fun rank(bill: Bill, today: LocalDate): Int = when (bill.statusOn(today)) {
        BillStatus.FINISHED -> 2
        BillStatus.PAUSED -> 1
        else -> 0
    }

    suspend fun create(draft: BillDraft): LedgerResult<Bill> {
        validate(draft)?.let { return failure(it) }
        val bill = draft.toBill(newId(), startDate = draft.dueDate, active = true)
        bills.save(bill)
        return LedgerResult.Success(bill)
    }

    /**
     * Mengubah tagihan. Tanggal jatuh tempo yang diubah menjadi jangkar jadwal yang baru; bila tidak
     * berubah, jangkar lama dipertahankan. Jumlah cicilan terbayar ikut diisi dari layar, jadi
     * salah hitung bisa dibetulkan.
     */
    suspend fun update(id: String, draft: BillDraft): LedgerResult<Bill> {
        val current = bills.find(id) ?: return failure(LedgerError.BILL_NOT_FOUND)
        validate(draft)?.let { return failure(it) }
        val startDate = if (draft.dueDate == current.nextDue && draft.frequency == current.frequency) current.startDate else draft.dueDate
        val updated = draft.toBill(id, startDate, current.active)
        bills.save(updated)
        return LedgerResult.Success(updated)
    }

    /** Menjeda atau melanjutkan. Tagihan yang dijeda tidak diingatkan; jatuh temponya tidak digeser. */
    suspend fun setActive(id: String, active: Boolean): LedgerResult<Bill> {
        val current = bills.find(id) ?: return failure(LedgerError.BILL_NOT_FOUND)
        val updated = current.copy(active = active)
        bills.save(updated)
        return LedgerResult.Success(updated)
    }

    /** Menghapus tagihan; pengeluaran yang sudah tercatat dari pembayarannya tetap ada. */
    suspend fun delete(id: String): LedgerResult<Unit> {
        bills.find(id) ?: return failure(LedgerError.BILL_NOT_FOUND)
        bills.delete(id)
        return LedgerResult.Success(Unit)
    }

    /**
     * Membayar jatuh tempo berikutnya: mencatat pengeluaran di ruang dan kategori tagihan pada
     * [paidOn], lalu memajukan jatuh tempo (atau melunasi). [amount] boleh berbeda dari nominal
     * tagihan (listrik dan air berubah tiap bulan); kosong berarti nominal tagihan. Pengeluarannya
     * berpengenal tetap (`bill-<tagihan>-<jatuh tempo>`), jadi pembayaran yang sudah tercatat tapi
     * kemajuannya belum sempat tersimpan tidak tercatat dua kali.
     */
    suspend fun pay(id: String, paidOn: LocalDate, amount: Money? = null): LedgerResult<BillPayment> {
        val bill = bills.find(id) ?: return failure(LedgerError.BILL_NOT_FOUND)
        if (bill.isFinished) return failure(LedgerError.BILL_FINISHED)
        val paid = amount ?: bill.amount
        val transactionId = TransactionId(occurrenceId(bill.id, bill.nextDue))
        if (transactions.find(transactionId) == null) {
            val result = ledger.recordExpense(
                NewExpense(paid, bill.accountId, bill.roomId, bill.categoryId, paidOn, paymentNote(bill), TransactionOrigin.BILL, id = transactionId.value),
            )
            if (result is LedgerResult.Failure) return failure(result.error)
        }
        val paidBill = bill.copy(paidCount = bill.paidCount + 1)
        val updated = if (paidBill.isFinished || bill.frequency == null) paidBill else paidBill.copy(nextDue = RecurrenceSchedule.next(bill.startDate, bill.frequency, bill.nextDue))
        bills.save(updated)
        return LedgerResult.Success(BillPayment(bill, transactionId, updated))
    }

    /** Membatalkan [pay]: menghapus pengeluarannya dan mengembalikan tagihan seperti sebelum dibayar. */
    suspend fun undoPay(payment: BillPayment) {
        if (transactions.find(payment.transactionId) != null) transactions.delete(payment.transactionId)
        bills.save(payment.previous)
    }

    private fun paymentNote(bill: Bill): String {
        val installment = bill.totalInstallments?.let { " (${bill.paidCount + 1}/$it)" }.orEmpty()
        return (bill.name + installment).take(LedgerService.NOTE_MAX)
    }

    private suspend fun validate(draft: BillDraft): LedgerError? {
        val name = draft.name.trim()
        if (name.isEmpty() || name.length > Bill.NAME_MAX) return LedgerError.INVALID_NAME
        if (!draft.amount.isPositive) return LedgerError.AMOUNT_NOT_POSITIVE
        if ((draft.note?.trim()?.length ?: 0) > LedgerService.NOTE_MAX) return LedgerError.NOTE_TOO_LONG
        val total = draft.totalInstallments
        if (draft.alreadyPaid < 0) return LedgerError.INVALID_INSTALLMENTS
        if (total != null && (draft.frequency == null || total < 1 || draft.alreadyPaid >= total)) return LedgerError.INVALID_INSTALLMENTS
        if (draft.frequency == null && draft.alreadyPaid > 0) return LedgerError.INVALID_INSTALLMENTS

        val account = accounts.find(draft.accountId) ?: return LedgerError.ACCOUNT_NOT_FOUND
        if (account.archived) return LedgerError.ACCOUNT_ARCHIVED
        if (account.currency != draft.amount.currency) return LedgerError.CURRENCY_MISMATCH
        val room = rooms.find(draft.roomId) ?: return LedgerError.ROOM_NOT_FOUND
        if (room.archived) return LedgerError.ROOM_ARCHIVED
        val category = rooms.findCategory(draft.categoryId)
        if (category == null || category.roomId != room.id || category.archived) return LedgerError.CATEGORY_NOT_IN_ROOM
        return null
    }

    private fun BillDraft.toBill(id: String, startDate: LocalDate, active: Boolean) = Bill(
        id = id,
        name = name.trim(),
        amount = amount,
        accountId = accountId,
        roomId = roomId,
        categoryId = categoryId,
        note = note?.trim()?.takeIf { it.isNotEmpty() },
        frequency = frequency,
        startDate = startDate,
        nextDue = dueDate,
        totalInstallments = if (frequency != null) totalInstallments else null,
        paidCount = alreadyPaid,
        active = active,
    )

    companion object {
        fun occurrenceId(billId: String, dueDate: LocalDate) = "bill-$billId-$dueDate"
    }
}

/** Tahap pengingat satu tagihan; urutannya menentukan apakah sapaan baru perlu (hanya naik, tidak pernah turun). */
enum class BillReminderStage { APPROACHING, DUE_TODAY, OVERDUE }

/** Satu tagihan yang perlu disapa hari ini; [daysLeft] negatif berarti terlambat sekian hari. */
data class BillReminder(val bill: Bill, val stage: BillReminderStage, val daysLeft: Int)

/**
 * Pengingat tagihan yang menumpang jadwal harian pengingat malam (S26), seperti `HaulReminderService`
 * dan `DcaReminderService`: bukan penjadwal baru, jadi hanya jalan bila pengingat malam aktif.
 * Menyapa paling banyak tiga kali per jatuh tempo: tiga hari sebelumnya, pada harinya, dan sekali
 * saat terlambat, lalu diam sampai dibayar. Penanda tersimpan (`bill_reminder_notified_<tagihan>` =
 * `<jatuh tempo>:<tahap>`) terikat pada jatuh temponya, jadi membayar (jatuh tempo maju)
 * dengan sendirinya memulai daur baru tanpa perlu menghapus apa pun. Tagihan yang dijeda atau
 * lunas tidak pernah disapa.
 */
class BillReminderService(
    private val bills: BillRepository,
    private val settings: SettingsRepository,
) {
    suspend fun dueReminders(today: LocalDate): List<BillReminder> = bills.all()
        .filter { it.active && !it.isFinished }
        .sortedBy { it.nextDue }
        .mapNotNull { bill ->
            val daysLeft = ChronoUnit.DAYS.between(today, bill.nextDue).toInt()
            val stage = when {
                daysLeft < 0 -> BillReminderStage.OVERDUE
                daysLeft == 0 -> BillReminderStage.DUE_TODAY
                daysLeft <= Bill.SOON_DAYS -> BillReminderStage.APPROACHING
                else -> return@mapNotNull null
            }
            if (alreadyNotified(bill, stage)) null else BillReminder(bill, stage, daysLeft)
        }

    suspend fun markNotified(reminder: BillReminder) {
        settings.put(keyFor(reminder.bill.id), "${reminder.bill.nextDue.toEpochDay()}:${reminder.stage.ordinal}")
    }

    private suspend fun alreadyNotified(bill: Bill, stage: BillReminderStage): Boolean {
        val (due, notifiedStage) = settings.get(keyFor(bill.id))?.split(':')?.takeIf { it.size == 2 }?.let { it[0].toLongOrNull() to it[1].toIntOrNull() } ?: return false
        return due == bill.nextDue.toEpochDay() && notifiedStage != null && notifiedStage >= stage.ordinal
    }

    private fun keyFor(billId: String) = "$KEY_PREFIX$billId"

    private companion object {
        const val KEY_PREFIX = "bill_reminder_notified_"
    }
}
