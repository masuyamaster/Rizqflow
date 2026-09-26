package com.roziqrizal.rizqflow.domain.role

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.entitlement.Entitlements
import com.roziqrizal.rizqflow.domain.entitlement.Feature
import com.roziqrizal.rizqflow.domain.ledger.AccountRepository
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.NewExpense
import com.roziqrizal.rizqflow.domain.ledger.Room
import com.roziqrizal.rizqflow.domain.ledger.RoomRepository
import com.roziqrizal.rizqflow.domain.ledger.TransactionRepository
import com.roziqrizal.rizqflow.domain.ledger.failure
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.money.sum
import java.time.LocalDate
import java.time.YearMonth

/** Keadaan jadwal DCA bulan ini. Dihitung dari transaksi, tidak pernah disimpan. */
sealed interface DcaStatus {
    /** Jumlah yang sudah dicatat bulan ini di kategori DCA mencapai nominal jadwal. */
    data class Done(val invested: Money) : DcaStatus

    /** Tanggal jadwal sudah tiba (atau lewat) tapi nominalnya belum tercatat bulan ini. */
    data class Due(val dueOn: LocalDate, val invested: Money) : DcaStatus

    /** Tanggal jadwal bulan ini belum tiba. */
    data class Upcoming(val dueOn: LocalDate) : DcaStatus
}

data class DcaView(val plan: DcaPlan, val status: DcaStatus, val accountName: String, val categoryName: String)

/**
 * Isi layar Peran untuk satu ruang. [entitled] false berarti paket sekarang tidak membuka sistem
 * peran (belum Pro, atau Pro berakhir): pengaturan yang sudah tersimpan tetap terlihat dan bisa
 * dilepas, tapi peringatan dan pengingatnya diam.
 */
data class RoleOverview(
    val room: Room,
    val entitled: Boolean,
    val trader: TraderProfile?,
    val dca: DcaView?,
) {
    val kind: RoleKind? get() = when {
        trader != null -> RoleKind.TRADER
        dca != null -> RoleKind.INVESTOR
        else -> null
    }
}

/** Pengeluaran melewati batas risiko per trade: banner lembut di Catat. */
data class RiskWarning(val room: Room, val maxRisk: Money, val amount: Money)

/**
 * Sistem per peran (Pro): Trader dengan batas risiko per trade, Investor dengan jadwal DCA. Semua
 * penguncian lewat [Entitlements] ([Feature.ROLE_SYSTEMS]). Memasang atau mengubah butuh Pro;
 * melepas selalu boleh, dan turun paket tidak menghapus apa pun, hanya membuat modul diam.
 */
class RoleService(
    private val rooms: RoomRepository,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val roles: RoleRepository,
    private val ledger: LedgerService,
    private val entitlements: Entitlements,
) {
    private val entitled: Boolean get() = entitlements.isEnabled(Feature.ROLE_SYSTEMS)

    /** Null bila ruangnya tidak ada. */
    suspend fun overview(roomId: RoomId, today: LocalDate): RoleOverview? {
        val room = rooms.find(roomId) ?: return null
        val dca = roles.dca(roomId)?.let { plan -> viewOf(plan, today) }
        return RoleOverview(room, entitled, roles.trader(roomId), dca)
    }

    /** Memasang (atau memperbarui) peran Trader. Ruang yang sudah Investor harus dilepas dulu. */
    suspend fun setTrader(roomId: RoomId, capital: Money, riskPerTrade: BasisPoints): LedgerResult<Unit> {
        if (!entitled) return failure(LedgerError.FEATURE_LOCKED)
        val room = rooms.find(roomId) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        if (room.archived) return failure(LedgerError.ROOM_ARCHIVED)
        if (!capital.isPositive || riskPerTrade.value !in 1..TraderProfile.MAX_RISK_BP) return failure(LedgerError.INVALID_ROLE_SETTINGS)
        if (roles.dca(room.id) != null) return failure(LedgerError.ROLE_CONFLICT)
        roles.saveTrader(TraderProfile(room.id, capital, riskPerTrade))
        return LedgerResult.Success(Unit)
    }

    /**
     * Memasang (atau memperbarui) jadwal DCA. [categoryId] harus kategori biasa ruang itu (bukan
     * kategori sistem) dan [accountId] akun aktif. Ruang yang sudah Trader harus dilepas dulu.
     */
    suspend fun setDca(roomId: RoomId, amount: Money, dayOfMonth: Int, accountId: AccountId, categoryId: CategoryId): LedgerResult<Unit> {
        if (!entitled) return failure(LedgerError.FEATURE_LOCKED)
        val room = rooms.find(roomId) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        if (room.archived) return failure(LedgerError.ROOM_ARCHIVED)
        if (!amount.isPositive || dayOfMonth !in 1..DcaPlan.MAX_DAY) return failure(LedgerError.INVALID_ROLE_SETTINGS)
        val account = accounts.find(accountId) ?: return failure(LedgerError.ACCOUNT_NOT_FOUND)
        if (account.archived) return failure(LedgerError.ACCOUNT_ARCHIVED)
        if (account.currency != amount.currency) return failure(LedgerError.CURRENCY_MISMATCH)
        val category = rooms.findCategory(categoryId)
        if (category == null || category.roomId != room.id || category.archived || category.isSystem) {
            return failure(LedgerError.CATEGORY_NOT_IN_ROOM)
        }
        if (roles.trader(room.id) != null) return failure(LedgerError.ROLE_CONFLICT)
        roles.saveDca(DcaPlan(room.id, amount, dayOfMonth, accountId, categoryId))
        return LedgerResult.Success(Unit)
    }

    /** Melepas peran dari ruang. Selalu diizinkan, juga saat paket tidak lagi membukanya. */
    suspend fun remove(roomId: RoomId) {
        roles.remove(roomId)
    }

    /**
     * Peringatan lembut untuk Catat: pengeluaran [amount] di ruang Trader melewati batas risiko per
     * trade. Null bila ruang bukan Trader, paket tidak membukanya, atau nominalnya masih dalam batas.
     */
    suspend fun riskWarning(roomId: RoomId, amount: Money): RiskWarning? {
        if (!entitled) return null
        val trader = roles.trader(roomId) ?: return null
        val room = rooms.find(roomId) ?: return null
        val max = trader.maxRiskPerTrade
        return if (amount.currency == max.currency && amount > max) RiskWarning(room, max, amount) else null
    }

    /**
     * Mencatat DCA bulan ini sesuai jadwal: satu pengeluaran sebesar nominal jadwal di kategori DCA
     * (ketuk "Catat sekarang"). Boleh dipanggil kapan saja, juga sebelum tanggalnya.
     */
    suspend fun recordDca(roomId: RoomId, on: LocalDate): LedgerResult<TransactionId> {
        if (!entitled) return failure(LedgerError.FEATURE_LOCKED)
        val plan = roles.dca(roomId) ?: return failure(LedgerError.ROLE_NOT_SET)
        val recorded = ledger.recordExpense(NewExpense(plan.amount, plan.accountId, plan.roomId, plan.categoryId, on, DCA_NOTE))
        return when (recorded) {
            is LedgerResult.Success -> LedgerResult.Success(recorded.value.id)
            is LedgerResult.Failure -> recorded
        }
    }

    /** Jadwal DCA yang jatuh tempo hari ini dan belum tercatat bulan ini, di ruang aktif. Kosong bila paket tidak membukanya. */
    suspend fun dueDca(today: LocalDate): List<DcaView> {
        if (!entitled) return emptyList()
        return roles.allDca().mapNotNull { plan ->
            val room = rooms.find(plan.roomId)
            if (room == null || room.archived) return@mapNotNull null
            viewOf(plan, today).takeIf { it.status is DcaStatus.Due }
        }
    }

    private suspend fun viewOf(plan: DcaPlan, today: LocalDate): DcaView {
        val month = YearMonth.from(today)
        val invested = transactions.between(month.atDay(1), month.atEndOfMonth())
            .filter { it.kind == TransactionKind.EXPENSE && it.roomId == plan.roomId && it.categoryId == plan.categoryId }
            .map { it.amount }
            .sum(plan.amount.currency)
        val dueOn = month.atDay(plan.dayOfMonth)
        val status = when {
            invested >= plan.amount -> DcaStatus.Done(invested)
            !today.isBefore(dueOn) -> DcaStatus.Due(dueOn, invested)
            else -> DcaStatus.Upcoming(dueOn)
        }
        val accountName = accounts.find(plan.accountId)?.name.orEmpty()
        val categoryName = rooms.findCategory(plan.categoryId)?.name.orEmpty()
        return DcaView(plan, status, accountName, categoryName)
    }

    companion object {
        /** Catatan pengeluaran DCA. Istilahnya sama di semua bahasa, jadi tidak lewat resource. */
        const val DCA_NOTE = "DCA"
    }
}
