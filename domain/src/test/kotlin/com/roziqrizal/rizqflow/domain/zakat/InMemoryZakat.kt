package com.roziqrizal.rizqflow.domain.zakat

/** Penyimpanan tiruan di memori untuk menguji [ZakatService] tanpa database; lihat InMemoryLedger di :domain ledger. */
class InMemoryZakat : ZakatRepository {
    var profile: ZakatProfile? = null
    val itemRows = linkedMapOf<String, WealthItem>()
    val goldPrices = mutableListOf<GoldPrice>()
    val checkRows = mutableListOf<WealthCheck>()
    val paymentRows = mutableListOf<ZakatPayment>()

    override suspend fun activeProfile(): ZakatProfile? = profile

    override suspend fun saveProfile(profile: ZakatProfile) {
        this.profile = profile
    }

    override suspend fun items(profileId: String): List<WealthItem> = itemRows.values.filter { it.profileId == profileId }

    override suspend fun replaceItems(profileId: String, items: List<WealthItem>) {
        itemRows.keys.filter { itemRows.getValue(it).profileId == profileId }.forEach { itemRows.remove(it) }
        items.forEach { itemRows[it.id] = it }
    }

    override suspend fun latestGoldPrice(): GoldPrice? = goldPrices.maxByOrNull { it.day }

    override suspend fun saveGoldPrice(price: GoldPrice) {
        goldPrices.removeAll { it.day == price.day }
        goldPrices.add(price)
    }

    override suspend fun checks(profileId: String): List<WealthCheck> = checkRows.filter { it.profileId == profileId }

    override suspend fun saveCheck(check: WealthCheck) {
        checkRows.removeAll { it.profileId == check.profileId && it.day == check.day }
        checkRows.add(check)
    }

    override suspend fun payments(profileId: String): List<ZakatPayment> = paymentRows.filter { it.profileId == profileId }

    override suspend fun savePayment(payment: ZakatPayment) {
        paymentRows.add(payment)
    }
}
