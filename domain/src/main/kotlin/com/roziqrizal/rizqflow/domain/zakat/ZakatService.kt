package com.roziqrizal.rizqflow.domain.zakat

import com.roziqrizal.rizqflow.domain.allocation.BasisPoints
import com.roziqrizal.rizqflow.domain.calendar.HijriCalendar
import com.roziqrizal.rizqflow.domain.calendar.UmmAlQuraCalendar
import com.roziqrizal.rizqflow.domain.entitlement.Entitlements
import com.roziqrizal.rizqflow.domain.entitlement.Feature
import com.roziqrizal.rizqflow.domain.entitlement.PlanEntitlements
import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.NewExpense
import com.roziqrizal.rizqflow.domain.ledger.Room
import com.roziqrizal.rizqflow.domain.ledger.RoomRepository
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplates
import com.roziqrizal.rizqflow.domain.ledger.TransactionRepository
import com.roziqrizal.rizqflow.domain.ledger.failure
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.GoldPriceSource
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.model.WealthKind
import com.roziqrizal.rizqflow.domain.money.Currency
import com.roziqrizal.rizqflow.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth

/** Satu baris harta atau pengurang yang belum disimpan (S15). [id] null berarti baris baru. */
data class NewWealthItem(val id: String? = null, val kind: WealthKind, val label: String, val value: Money, val goldMilligrams: Long? = null)

/** Satu baris riwayat zakat (S14) beserta nominal yang tercatat di transaksinya. */
data class ZakatPaymentRow(val payment: ZakatPayment, val amount: Money)

/**
 * Isi S14 Beranda Zakat untuk satu ruang Memberi, untuk satu profil harta ([profile]; null bila belum
 * ada profil). [profiles] adalah semua profil aktif untuk pemilih profil, [canAddProfile] apakah paket
 * membolehkan profil tambahan.
 */
data class GivingOverview(
    val room: Room,
    val status: GivingStatus,
    val goldPrice: GoldPrice?,
    val items: List<WealthItem>,
    val payments: List<ZakatPaymentRow>,
    val profile: ZakatProfile? = null,
    val profiles: List<ZakatProfile> = emptyList(),
    val canAddProfile: Boolean = false,
) {
    /** Modul zakat belum pernah diisi sama sekali: S14 menampilkan kartu "Mulai dengan mengisi harta". */
    val neverSetUp: Boolean get() = room.givingMode == ZakatHaulHijriStrategy.ID && items.isEmpty()
}

/**
 * Modul Memberi (S14 sampai S17): menilai ruang Memberi lewat [GivingStrategy] yang sedang aktif,
 * menyimpan profil harta, dan menunaikan zakat sebagai pengeluaran biasa di ruang itu (F5). Status
 * haul tidak pernah disimpan; selalu dihitung ulang dari riwayat [WealthCheck] dan [ZakatPayment]
 * lewat [HaulTracker], persis seperti saldo akun dihitung ulang dari transaksi.
 */
class ZakatService(
    private val rooms: RoomRepository,
    private val transactions: TransactionRepository,
    private val zakat: ZakatRepository,
    private val ledger: LedgerService,
    private val newId: () -> String,
    private val calendar: HijriCalendar = UmmAlQuraCalendar(),
    private val assumptions: ZakatAssumptions = ZakatAssumptions(),
    private val entitlements: Entitlements = PlanEntitlements(),
) {
    /**
     * Null bila [roomId] tidak ada. [profileId] memilih profil harta yang dinilai; kosong atau tidak
     * dikenal memakai profil pertama. Profil tanpa pemeriksaan harta sama sekali (baru ditambah) belum
     * bisa dinilai, jadi statusnya meminta harta diisi, bukan "belum mencapai nisab".
     */
    suspend fun overview(roomId: RoomId, today: LocalDate, profileId: String? = null): GivingOverview? {
        val room = rooms.find(roomId) ?: return null
        val profiles = zakat.profiles()
        val profile = profiles.firstOrNull { it.id == profileId } ?: profiles.firstOrNull()
        val items = profile?.let { zakat.items(it.id) }.orEmpty()
        val checks = profile?.let { zakat.checks(it.id) }.orEmpty()
        val price = zakat.latestGoldPrice()
        val netWealth = if (profile == null || checks.isEmpty()) null else netWealthOf(items, price?.perGram?.currency ?: Money.zero().currency)

        val month = YearMonth.from(today)
        val income = transactions.between(month.atDay(1), month.atEndOfMonth())
            .filter { it.kind == TransactionKind.INCOME }
            .fold(Money.zero()) { total, tx -> total + tx.amount }

        val events = buildEvents(profile, checks)
        val strategy = strategyFor(room.givingMode, profile?.haulBreakPolicy ?: HaulBreakPolicy.RESET_WHEN_BELOW_NISAB)
        val status = strategy.evaluate(GivingContext(today, income, price?.perGram, netWealth, events))

        val paymentRows = profile?.let { zakat.payments(it.id) }.orEmpty()
            .sortedByDescending { it.day }
            .map { payment -> ZakatPaymentRow(payment, transactions.find(payment.transactionId)?.amount ?: Money.zero()) }

        return GivingOverview(room, status, price, items, paymentRows, profile, profiles, canAddProfile(profiles))
    }

    /** Paket ini membuka profil harta tambahan dan pengingat haul untuk semuanya (bukan hanya profil pertama). */
    fun multiProfileEnabled(): Boolean = entitlements.isEnabled(Feature.MULTI_ZAKAT_PROFILE)

    /** Profil pertama gratis; setiap profil tambahan butuh Pro. Profil yang sudah ada tidak pernah terkunci. */
    private fun canAddProfile(profiles: List<ZakatProfile>): Boolean =
        profiles.isEmpty() || entitlements.isEnabled(Feature.MULTI_ZAKAT_PROFILE)

    /** Menambah profil harta (mis. istri atau usaha). Profil tambahan butuh Pro; nama tidak boleh kembar. */
    suspend fun addProfile(name: String): LedgerResult<ZakatProfile> {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > ZakatProfile.NAME_MAX) return failure(LedgerError.INVALID_NAME)
        val existing = zakat.profiles()
        if (!canAddProfile(existing)) return failure(LedgerError.FEATURE_LOCKED)
        if (existing.any { it.name.equals(clean, ignoreCase = true) }) return failure(LedgerError.NAME_TAKEN)
        val profile = ZakatProfile(newId(), clean)
        zakat.saveProfile(profile)
        return LedgerResult.Success(profile)
    }

    /** Mengganti nama profil. Selalu diizinkan, juga saat paket tidak membuka profil tambahan. */
    suspend fun renameProfile(profileId: String, name: String): LedgerResult<Unit> {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > ZakatProfile.NAME_MAX) return failure(LedgerError.INVALID_NAME)
        val profile = zakat.findProfile(profileId) ?: return failure(LedgerError.PROFILE_NOT_FOUND)
        if (zakat.profiles().any { it.id != profileId && it.name.equals(clean, ignoreCase = true) }) return failure(LedgerError.NAME_TAKEN)
        zakat.saveProfile(profile.copy(name = clean))
        return LedgerResult.Success(Unit)
    }

    /**
     * Mengarsipkan profil: hilang dari pemilih dan pengingat, riwayatnya tetap tersimpan. Profil aktif
     * terakhir tidak bisa diarsipkan. Selalu diizinkan, juga saat paket tidak membuka profil tambahan.
     */
    suspend fun archiveProfile(profileId: String): LedgerResult<Unit> {
        val profiles = zakat.profiles()
        val profile = profiles.firstOrNull { it.id == profileId } ?: return failure(LedgerError.PROFILE_NOT_FOUND)
        if (profiles.size <= 1) return failure(LedgerError.LAST_PROFILE)
        zakat.saveProfile(profile.copy(archived = true))
        return LedgerResult.Success(Unit)
    }

    /** Ruang lain (persentase donasi) atau zakat mal (nisab dan haul); disimpan di ruang itu sendiri. */
    suspend fun setGivingMode(roomId: RoomId, mode: String): LedgerResult<Unit> {
        require(mode == PercentageGivingStrategy.ID || mode == ZakatHaulHijriStrategy.ID) { "Mode tidak dikenal: $mode" }
        val room = rooms.find(roomId) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        rooms.saveRooms(listOf(room.copy(givingMode = mode)))
        return LedgerResult.Success(Unit)
    }

    /**
     * Menyimpan seluruh daftar harta sekaligus (S15). [goldPricePerGram] wajib karena nisab selalu
     * dinilai dalam emas, dipakai juga untuk menghitung ulang nilai baris `GOLD` dari gramnya.
     * Menyimpan otomatis mencatat pemeriksaan harta hari ini, dasar penghitungan haul.
     */
    suspend fun saveWealth(items: List<NewWealthItem>, goldPricePerGram: Money, today: LocalDate, profileId: String? = null): LedgerResult<Unit> {
        if (!goldPricePerGram.isPositive) return failure(LedgerError.GOLD_PRICE_REQUIRED)
        if (items.any { it.label.isBlank() || it.label.length > WealthItem.LABEL_MAX || it.value.isNegative }) {
            return failure(LedgerError.INVALID_WEALTH_ITEM)
        }

        val profile = resolveProfile(profileId) ?: return failure(LedgerError.PROFILE_NOT_FOUND)
        zakat.saveGoldPrice(GoldPrice(today, goldPricePerGram, GoldPriceSource.MANUAL))

        val now = System.currentTimeMillis()
        val resolved = items.map { draft ->
            val value = if (draft.kind == WealthKind.GOLD) ZakatCalculator.goldValue(goldPricePerGram, draft.goldMilligrams ?: 0) else draft.value
            WealthItem(draft.id ?: newId(), profile.id, draft.kind, draft.label.trim(), value, draft.goldMilligrams, now)
        }
        zakat.replaceItems(profile.id, resolved)

        val netWealth = netWealthOf(resolved, goldPricePerGram.currency)
        zakat.saveCheck(WealthCheck(newId(), profile.id, today, netWealth, ZakatCalculator.nisab(goldPricePerGram, assumptions)))
        return LedgerResult.Success(Unit)
    }

    /**
     * Menunaikan zakat: pengeluaran kategori sistem `Zakat mal` di ruang Memberi, lalu haul baru
     * mulai dihitung dari hari ini (F5). Boleh dipanggil sebelum haul genap (S17: "tunaikan lebih awal").
     */
    suspend fun payZakat(
        roomId: RoomId,
        accountId: AccountId,
        amount: Money,
        date: LocalDate,
        note: String? = null,
        profileId: String? = null,
    ): LedgerResult<TransactionId> {
        val room = rooms.find(roomId) ?: return failure(LedgerError.ROOM_NOT_FOUND)
        val category = rooms.categories(roomId).firstOrNull { it.name == RoomTemplates.ZAKAT }
            ?: return failure(LedgerError.ZAKAT_CATEGORY_MISSING)
        val profile = resolveProfile(profileId) ?: return failure(LedgerError.PROFILE_NOT_FOUND)

        val recorded = ledger.recordExpense(NewExpense(amount, accountId, room.id, category.id, date, note))
        val transaction = when (recorded) {
            is LedgerResult.Success -> recorded.value
            is LedgerResult.Failure -> return recorded
        }
        zakat.savePayment(ZakatPayment(newId(), profile.id, date, transaction.id))
        return LedgerResult.Success(transaction.id)
    }

    private fun strategyFor(mode: String?, haulBreakPolicy: HaulBreakPolicy): GivingStrategy = when (mode) {
        PercentageGivingStrategy.ID -> PercentageGivingStrategy(PERCENTAGE_MODE_RATE)
        else -> ZakatHaulHijriStrategy(HaulTracker(calendar, assumptions.copy(haulBreakPolicy = haulBreakPolicy)))
    }

    private fun netWealthOf(items: List<WealthItem>, currency: Currency): Money {
        val assets = items.filter { it.kind != WealthKind.DEDUCTION }.map { it.value }
        val deductions = items.filter { it.kind == WealthKind.DEDUCTION }.map { it.value }
        return ZakatCalculator.netWealth(assets, deductions, currency)
    }

    /**
     * Profil yang dituju: [profileId] bila diberikan (null bila tidak ada), kalau tidak profil pertama,
     * dan bila belum ada profil sama sekali dibuat profil bawaan "Utama".
     */
    private suspend fun resolveProfile(profileId: String?): ZakatProfile? {
        if (profileId != null) return zakat.profiles().firstOrNull { it.id == profileId }
        return zakat.profiles().firstOrNull() ?: ZakatProfile(newId(), DEFAULT_PROFILE_NAME).also { zakat.saveProfile(it) }
    }

    private suspend fun buildEvents(profile: ZakatProfile?, savedChecks: List<WealthCheck>): List<HaulEvent> {
        if (profile == null) return emptyList()
        val checks = savedChecks.map { HaulEvent.WealthChecked(it.day, it.netWealth, it.nisab) }
        val payments = zakat.payments(profile.id).map { HaulEvent.ZakatPaid(it.day) }
        // Pada hari yang sama, pemeriksaan mendahului pembayaran: haul baru dievaluasi dari nilai
        // harta hari itu dulu, baru direset oleh pembayaran (urutan stabil menurut hari).
        return (checks + payments).sortedBy { it.date }
    }

    companion object {
        private const val DEFAULT_PROFILE_NAME = "Utama"

        /** Bawaan mode persentase donasi (S14): 10% dari rezeki bulan itu, sama seperti pola Tiga hak. */
        private val PERCENTAGE_MODE_RATE = BasisPoints.percent(10)
    }
}
