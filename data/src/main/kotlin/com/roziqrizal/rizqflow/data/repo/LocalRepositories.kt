package com.roziqrizal.rizqflow.data.repo

import androidx.room.withTransaction
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.allocation.AllocationRule
import com.roziqrizal.rizqflow.domain.ledger.Account
import com.roziqrizal.rizqflow.domain.ledger.AccountRepository
import com.roziqrizal.rizqflow.domain.ledger.AllocationEntry
import com.roziqrizal.rizqflow.domain.ledger.Category
import com.roziqrizal.rizqflow.domain.ledger.MoneyTransaction
import com.roziqrizal.rizqflow.domain.ledger.Room
import com.roziqrizal.rizqflow.domain.ledger.RoomRepository
import com.roziqrizal.rizqflow.domain.ledger.RoomTotals
import com.roziqrizal.rizqflow.domain.ledger.TransactionRepository
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceRepository
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSnapshot
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate

/*
 * Implementasi repositori di atas Room. Setiap fungsi yang menulis lebih dari satu baris
 * dibungkus satu transaksi database: gagal di tengah membatalkan semuanya.
 */

class LocalWorkspaceRepository(private val db: RizqflowDatabase) : WorkspaceRepository {
    override suspend fun isEmpty(): Boolean = db.rooms().count() == 0 && db.accounts().count() == 0

    override suspend fun initialize(snapshot: WorkspaceSnapshot) {
        db.withTransaction {
            // Urutan mengikuti kunci asing: ruang lebih dulu, baru kategori dan aturan.
            snapshot.rooms.forEach { db.rooms().upsert(it.toEntity()) }
            db.rooms().upsertCategories(snapshot.categories.map(Category::toEntity))
            db.rooms().upsertRules(snapshot.rules.map(AllocationRule::toEntity))
            db.accounts().upsertAll(snapshot.accounts.map(Account::toEntity))
        }
    }
}

class LocalAccountRepository(private val db: RizqflowDatabase) : AccountRepository {
    override suspend fun find(id: AccountId): Account? = db.accounts().find(id.value)?.toDomain()

    override suspend fun activeAccounts(): List<Account> = db.accounts().active().map { it.toDomain() }

    override suspend fun save(account: Account) = db.accounts().upsert(account.toEntity())

    override suspend fun balance(id: AccountId): Money {
        val account = requireNotNull(find(id)) { "Akun tidak ada: ${id.value}" }
        return Money(db.accounts().balance(id.value), account.currency)
    }
}

class LocalRoomRepository(private val db: RizqflowDatabase) : RoomRepository {
    override suspend fun activeRooms(): List<Room> = db.rooms().active().map { it.toDomain() }

    override suspend fun find(id: RoomId): Room? = db.rooms().find(id.value)?.toDomain()

    override suspend fun rules(): List<AllocationRule> = db.rooms().rules().map { it.toDomain() }

    override suspend fun categories(roomId: RoomId): List<Category> = db.rooms().categories(roomId.value).map { it.toDomain() }

    override suspend fun findCategory(id: CategoryId): Category? = db.rooms().findCategory(id.value)?.toDomain()

    override suspend fun addRoom(room: Room, categories: List<Category>) {
        db.withTransaction {
            db.rooms().upsert(room.toEntity())
            db.rooms().upsertCategories(categories.map(Category::toEntity))
        }
    }

    override suspend fun replaceRules(rules: List<AllocationRule>) {
        db.withTransaction {
            db.rooms().clearActiveRules()
            db.rooms().upsertRules(rules.map(AllocationRule::toEntity))
        }
    }
}

class LocalTransactionRepository(private val db: RizqflowDatabase) : TransactionRepository {
    override suspend fun find(id: TransactionId): MoneyTransaction? = db.transactions().find(id.value)?.toDomain()

    override suspend fun entriesOf(incomeId: TransactionId): List<AllocationEntry> {
        val currency = db.transactions().find(incomeId.value)?.toDomain()?.amount?.currency ?: return emptyList()
        return db.transactions().entriesOf(incomeId.value).map { it.toDomain(currency) }
    }

    override suspend fun saveIncome(transaction: MoneyTransaction, entries: List<AllocationEntry>) {
        db.withTransaction {
            db.transactions().insert(transaction.toEntity())
            db.transactions().insertEntries(entries.map(AllocationEntry::toEntity))
        }
    }

    override suspend fun save(transaction: MoneyTransaction) = db.transactions().insert(transaction.toEntity())

    override suspend fun replaceIncome(transaction: MoneyTransaction, entries: List<AllocationEntry>) {
        db.withTransaction {
            db.transactions().update(transaction.toEntity())
            db.transactions().deleteEntries(transaction.id.value)
            db.transactions().insertEntries(entries.map(AllocationEntry::toEntity))
        }
    }

    override suspend fun delete(id: TransactionId) = db.transactions().delete(id.value)

    override suspend fun between(from: LocalDate, to: LocalDate): List<MoneyTransaction> =
        db.transactions().between(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    override suspend fun latest(kind: TransactionKind?): MoneyTransaction? =
        (if (kind == null) db.transactions().latest() else db.transactions().latestOfKind(kind))?.toDomain()

    // Versi 1 hanya rupiah (docs/model-data.md); multi-mata uang (Pro) menambah kolom mata uang di agregat ini.
    override suspend fun roomTotals(from: LocalDate, to: LocalDate): RoomTotals = RoomTotals(
        allocated = db.transactions().allocatedPerRoom(from.toEpochDay(), to.toEpochDay()).associate { RoomId(it.roomId) to Money.rupiah(it.total) },
        spent = db.transactions().spentPerRoom(from.toEpochDay(), to.toEpochDay()).associate { RoomId(it.roomId) to Money.rupiah(it.total) },
    )
}
