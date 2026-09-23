package com.roziqrizal.rizqflow.data.repo

import androidx.room.withTransaction
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.zakat.GoldPrice
import com.roziqrizal.rizqflow.domain.zakat.WealthCheck
import com.roziqrizal.rizqflow.domain.zakat.WealthItem
import com.roziqrizal.rizqflow.domain.zakat.ZakatPayment
import com.roziqrizal.rizqflow.domain.zakat.ZakatProfile
import com.roziqrizal.rizqflow.domain.zakat.ZakatRepository

class LocalZakatRepository(private val db: RizqflowDatabase) : ZakatRepository {
    override suspend fun profiles(): List<ZakatProfile> = db.zakat().activeProfiles().map { it.toDomain() }

    override suspend fun findProfile(id: String): ZakatProfile? = db.zakat().profile(id)?.toDomain()

    override suspend fun saveProfile(profile: ZakatProfile) = db.zakat().upsertProfile(profile.toEntity())

    override suspend fun items(profileId: String): List<WealthItem> = db.zakat().items(profileId).map { it.toDomain() }

    override suspend fun replaceItems(profileId: String, items: List<WealthItem>) {
        db.withTransaction {
            db.zakat().clearItems(profileId)
            if (items.isNotEmpty()) db.zakat().insertItems(items.map { it.toEntity() })
        }
    }

    override suspend fun latestGoldPrice(): GoldPrice? = db.zakat().latestGoldPrice()?.toDomain()

    override suspend fun saveGoldPrice(price: GoldPrice) = db.zakat().upsertGoldPrice(price.toEntity())

    override suspend fun checks(profileId: String): List<WealthCheck> = db.zakat().checks(profileId).map { it.toDomain() }

    override suspend fun saveCheck(check: WealthCheck) {
        db.withTransaction {
            db.zakat().clearCheckOfDay(check.profileId, check.day.toEpochDay())
            db.zakat().insertCheck(check.toEntity())
        }
    }

    override suspend fun payments(profileId: String): List<ZakatPayment> = db.zakat().payments(profileId).map { it.toDomain() }

    override suspend fun savePayment(payment: ZakatPayment) = db.zakat().insertPayment(payment.toEntity())
}
