package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/*
 * Antarmuka penyimpanan. Implementasinya di :data (Room, satu database per akun). Semua fungsi
 * yang mengubah lebih dari satu baris harus atomik: gagal di tengah tidak boleh meninggalkan
 * sebagian data.
 */

/** Isi awal ruang kerja satu akun: hasil onboarding. */
data class WorkspaceSnapshot(
    val rooms: List<Room>,
    val categories: List<Category>,
    val rules: List<AllocationRule>,
    val accounts: List<Account>,
)

interface WorkspaceRepository {
    /** Benar bila belum ada ruang maupun akun (akun baru yang belum onboarding). */
    suspend fun isEmpty(): Boolean

    /** Menyimpan seluruh isi awal sekaligus (atomik). */
    suspend fun initialize(snapshot: WorkspaceSnapshot)
}

interface AccountRepository {
    suspend fun find(id: AccountId): Account?

    /** Akun yang belum diarsipkan, berurutan. */
    suspend fun activeAccounts(): List<Account>

    suspend fun save(account: Account)

    /** Saldo menurut catatan: saldo awal + pemasukan - pengeluaran +/- transfer. */
    suspend fun balance(id: AccountId): Money
}

interface RoomRepository {
    /** Ruang yang belum diarsipkan, berurutan menurut prioritas. */
    suspend fun activeRooms(): List<Room>

    suspend fun find(id: RoomId): Room?

    /** Aturan alokasi ruang aktif, berurutan menurut prioritas ruang. Ruang tanpa aturan dianggap 0%. */
    suspend fun rules(): List<AllocationRule>

    suspend fun categories(roomId: RoomId): List<Category>

    suspend fun findCategory(id: CategoryId): Category?

    /** Menambah ruang beserta kategori awalnya (atomik). */
    suspend fun addRoom(room: Room, categories: List<Category>)

    /** Mengganti seluruh aturan alokasi (atomik). */
    suspend fun replaceRules(rules: List<AllocationRule>)
}

interface TransactionRepository {
    suspend fun find(id: TransactionId): MoneyTransaction?

    /** Alokasi satu pemasukan, berurutan menurut prioritas ruang. */
    suspend fun entriesOf(incomeId: TransactionId): List<AllocationEntry>

    /** Menyimpan pemasukan beserta potret alokasinya (atomik). */
    suspend fun saveIncome(transaction: MoneyTransaction, entries: List<AllocationEntry>)

    /** Menyimpan pengeluaran atau transfer. */
    suspend fun save(transaction: MoneyTransaction)

    /** Mengganti pemasukan dan seluruh potret alokasinya (atomik). */
    suspend fun replaceIncome(transaction: MoneyTransaction, entries: List<AllocationEntry>)

    /** Hapus fisik; potret alokasi ikut terhapus. */
    suspend fun delete(id: TransactionId)

    /** Transaksi antara dua hari (inklusif), terbaru dulu. */
    suspend fun between(from: LocalDate, to: LocalDate): List<MoneyTransaction>
}
