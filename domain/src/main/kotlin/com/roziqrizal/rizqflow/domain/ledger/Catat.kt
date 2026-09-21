package com.roziqrizal.rizqflow.domain.ledger

import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/** Tiga tab layar Catat (S06). Pemasukan lebih dulu karena rezeki adalah inti produk (prototipe yang disetujui). */
enum class CatatMode { INCOME, EXPENSE, TRANSFER }

/** Masalah isian Catat yang membuat tombol simpan belum aktif. Layar menjelaskannya dengan satu kalimat. */
enum class CatatIssue {
    NO_AMOUNT,
    NO_ACCOUNT,

    /** Pengeluaran butuh ruang; akun yang memilih Mulai kosong belum punya. */
    NO_ROOM,
    NO_CATEGORY,
    TRANSFER_NEEDS_TWO_ACCOUNTS,
    SAME_ACCOUNT,
}

/** Bahan pilihan layar Catat, dibaca sekali saat layar dibuka. */
data class CatatContext(
    val accounts: List<Account>,
    val rooms: List<Room>,
    val categories: Map<RoomId, List<Category>>,
    /** Aturan alokasi sekarang, berurutan menurut prioritas ruang. */
    val rules: List<AllocationRule>,
    val lastAccountId: AccountId? = null,
    val lastRoomId: RoomId? = null,
    val lastCategoryId: CategoryId? = null,
) {
    fun categoriesOf(roomId: RoomId): List<Category> = categories[roomId].orEmpty()

    /** Kategori bawaan sebuah ruang: yang terakhir dipakai bila ada di ruang itu, kalau tidak yang pertama bukan kategori sistem. */
    fun defaultCategory(roomId: RoomId): CategoryId? {
        val own = categoriesOf(roomId)
        return own.firstOrNull { it.id == lastCategoryId }?.id ?: own.firstOrNull { !it.isSystem }?.id ?: own.firstOrNull()?.id
    }

    val canTransfer: Boolean get() = accounts.size >= 2
}

/** Sumber pemasukan yang ditawarkan (S06 tab Pemasukan). */
val INCOME_SOURCES = listOf("Gaji", "Usaha", "Lainnya")

/**
 * Isian layar Catat yang belum disimpan. Murni dan tak berubah, seperti [OnboardingDraft]: mudah
 * diuji, dan aman disimpan saat layar diputar. Semua pilihan divalidasi terhadap [CatatContext]
 * di dalam fungsi ini sehingga layar tidak perlu memeriksa apa pun sendiri.
 */
data class CatatDraft(
    val mode: CatatMode = CatatMode.INCOME,
    val digits: String = "",
    val source: String? = INCOME_SOURCES.first(),
    val accountId: AccountId? = null,
    val toAccountId: AccountId? = null,
    val roomId: RoomId? = null,
    val categoryId: CategoryId? = null,
    val date: LocalDate,
    val note: String = "",
) {
    val amount: Money get() = AmountPad.toRupiah(digits)

    fun pressKey(key: String): CatatDraft = copy(digits = AmountPad.apply(digits, key))

    fun withNote(text: String): CatatDraft = copy(note = text.take(LedgerService.NOTE_MAX))

    fun withDate(value: LocalDate): CatatDraft = copy(date = value)

    fun withSource(value: String?): CatatDraft = copy(source = value)

    fun withMode(value: CatatMode, context: CatatContext): CatatDraft = copy(mode = value).normalized(context)

    fun withAccount(id: AccountId, context: CatatContext): CatatDraft = copy(accountId = id).normalized(context)

    fun withToAccount(id: AccountId, context: CatatContext): CatatDraft = copy(toAccountId = id).normalized(context)

    /** Berganti ruang mengganti kategori ke bawaan ruang itu. */
    fun withRoom(id: RoomId, context: CatatContext): CatatDraft =
        if (id == roomId) this else copy(roomId = id, categoryId = context.defaultCategory(id))

    fun withCategory(id: CategoryId): CatatDraft = copy(categoryId = id)

    /**
     * Menjaga isian tetap sah: akun harus ada, ruang dan kategori harus cocok, dan tujuan transfer
     * tidak boleh sama dengan asal. Dipanggil setiap ada perubahan yang bisa merusaknya.
     */
    private fun normalized(context: CatatContext): CatatDraft {
        val ids = context.accounts.map { it.id }
        val from = accountId?.takeIf { it in ids } ?: ids.firstOrNull()
        var to = toAccountId?.takeIf { it in ids && it != from }
        if (mode == CatatMode.TRANSFER && to == null) to = ids.firstOrNull { it != from }
        val room = roomId?.takeIf { id -> context.rooms.any { it.id == id } } ?: context.rooms.firstOrNull()?.id
        val category = categoryId?.takeIf { id -> room != null && context.categoriesOf(room).any { it.id == id } }
            ?: room?.let(context::defaultCategory)
        return copy(accountId = from, toAccountId = to, roomId = room, categoryId = category)
    }

    /** Masalah pertama yang menghalangi simpan, atau null bila sudah bisa disimpan. */
    fun issue(context: CatatContext): CatatIssue? {
        if (!amount.isPositive) return CatatIssue.NO_AMOUNT
        if (accountId == null) return CatatIssue.NO_ACCOUNT
        return when (mode) {
            CatatMode.INCOME -> null
            CatatMode.EXPENSE -> when {
                roomId == null -> CatatIssue.NO_ROOM
                categoryId == null -> CatatIssue.NO_CATEGORY
                else -> null
            }

            CatatMode.TRANSFER -> when {
                !context.canTransfer -> CatatIssue.TRANSFER_NEEDS_TWO_ACCOUNTS
                toAccountId == null || toAccountId == accountId -> CatatIssue.SAME_ACCOUNT
                else -> null
            }
        }
    }

    fun toIncome(overrideRules: List<AllocationRule>? = null): NewIncome =
        NewIncome(amount, requireNotNull(accountId), source, date, note, overrideRules = overrideRules)

    fun toExpense(): NewExpense =
        NewExpense(amount, requireNotNull(accountId), requireNotNull(roomId), requireNotNull(categoryId), date, note)

    fun toTransfer(): NewTransfer = NewTransfer(amount, requireNotNull(accountId), requireNotNull(toAccountId), date, note)

    companion object {
        /** Isian layar Detail (S09): semua kolom diisi dari [transaction] apa adanya, tanpa dinormalkan. */
        fun from(transaction: MoneyTransaction): CatatDraft = CatatDraft(
            mode = when (transaction.kind) {
                TransactionKind.INCOME -> CatatMode.INCOME
                TransactionKind.EXPENSE -> CatatMode.EXPENSE
                TransactionKind.TRANSFER -> CatatMode.TRANSFER
            },
            digits = transaction.amount.minor.toString(),
            source = transaction.incomeSource,
            accountId = transaction.accountId,
            toAccountId = transaction.toAccountId,
            roomId = transaction.roomId,
            categoryId = transaction.categoryId,
            date = transaction.occurredOn,
            note = transaction.note.orEmpty(),
        )

        /** Isian awal: akun terakhir dipakai, ruang dan kategori dari pengeluaran terakhir, tanggal hari ini. */
        fun start(context: CatatContext, today: LocalDate, mode: CatatMode = CatatMode.INCOME): CatatDraft {
            val account = context.lastAccountId?.takeIf { id -> context.accounts.any { it.id == id } }
            val room = context.lastRoomId?.takeIf { id -> context.rooms.any { it.id == id } }
            return CatatDraft(
                mode = mode,
                accountId = account,
                roomId = room,
                categoryId = context.lastCategoryId?.takeIf { room != null && context.categoriesOf(room).any { c -> c.id == it } },
                date = today,
            ).normalized(context)
        }
    }
}

/**
 * Membaca [CatatContext] dari penyimpanan: akun dan ruang aktif, kategori, aturan sekarang, dan
 * pilihan terakhir (akun dari transaksi terakhir, ruang dan kategori dari pengeluaran terakhir).
 */
class CatatContextLoader(
    private val accounts: AccountRepository,
    private val rooms: RoomRepository,
    private val transactions: TransactionRepository,
) {
    /**
     * [editing]: transaksi yang sedang diubah. Akun, ruang, dan kategori yang dipakainya ikut dimuat walau
     * sudah terarsip, supaya tetap muncul sebagai pilihan dan tidak diganti diam-diam.
     */
    suspend fun load(editing: MoneyTransaction? = null): CatatContext {
        val activeAccounts = accounts.activeAccounts().withUsed(listOfNotNull(editing?.accountId, editing?.toAccountId)) { accounts.find(it) }
        val activeRooms = rooms.activeRooms().withUsed(listOfNotNull(editing?.roomId)) { rooms.find(it) }
        val lastAny = transactions.latest(null)
        val lastExpense = transactions.latest(TransactionKind.EXPENSE)
        return CatatContext(
            accounts = activeAccounts,
            rooms = activeRooms,
            categories = activeRooms.associate { room ->
                val own = rooms.categories(room.id)
                val used = editing?.takeIf { it.roomId == room.id }?.categoryId?.takeIf { id -> own.none { c -> c.id == id } }?.let { rooms.findCategory(it) }
                room.id to (own + listOfNotNull(used))
            },
            rules = rooms.rules(),
            lastAccountId = lastAny?.accountId,
            lastRoomId = lastExpense?.roomId,
            lastCategoryId = lastExpense?.categoryId,
        )
    }
}

/** Menambahkan yang dipakai transaksi tetapi tidak ada di daftar aktif (mis. terarsip), di belakang yang aktif. */
private suspend fun <T : Any, I> List<T>.withUsed(ids: List<I>, find: suspend (I) -> T?): List<T> {
    val extras = ids.distinct().mapNotNull { find(it) }.filter { extra -> this.none { it == extra } }
    return this + extras
}

/**
 * Pembagian "Ubah sekali ini" di S07: persentase bulat per ruang khusus untuk satu pemasukan.
 * Total selalu 100% (ruang terakhir menerima sisa) seperti di onboarding. Aturan alokasi
 * tersimpan tidak berubah. Persentase pecahan dibulatkan ke persen terdekat saat dibuka.
 */
data class OneTimeSplit(val roomIds: List<RoomId>, val percents: List<Int>) {
    init {
        require(roomIds.size == percents.size) { "Jumlah ruang dan persen harus sama" }
    }

    fun set(index: Int, value: Int): OneTimeSplit = copy(percents = ShareEditor.set(percents, index, value))

    fun step(index: Int, delta: Int): OneTimeSplit = copy(percents = ShareEditor.step(percents, index, delta))

    fun toRules(): List<AllocationRule> = roomIds.zip(percents) { room, p -> AllocationRule(room, BasisPoints.percent(p)) }

    companion object {
        /** Kosong bila belum ada aturan (akun tanpa ruang). */
        fun from(rules: List<AllocationRule>): OneTimeSplit {
            if (rules.isEmpty()) return OneTimeSplit(emptyList(), emptyList())
            val rounded = rules.map { (it.share.value + 50) / 100 }
            // Memaksa jumlah tepat 100: ruang terakhir menerima sisa, dengan mengulang nilai ruang pertama.
            val normalized = if (rounded.size == 1) listOf(100) else ShareEditor.set(rounded, 0, rounded[0])
            return OneTimeSplit(rules.map { it.roomId }, normalized)
        }
    }
}
