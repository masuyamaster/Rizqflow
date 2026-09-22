package com.roziqrizal.rizqflow.data.repo

import com.roziqrizal.rizqflow.data.db.GoldPriceEntity
import com.roziqrizal.rizqflow.data.db.WealthCheckEntity
import com.roziqrizal.rizqflow.data.db.WealthItemEntity
import com.roziqrizal.rizqflow.data.db.ZakatPaymentEntity
import com.roziqrizal.rizqflow.data.db.ZakatProfileEntity
import com.roziqrizal.rizqflow.domain.model.TransactionId
import com.roziqrizal.rizqflow.domain.money.Money
import com.roziqrizal.rizqflow.domain.zakat.GoldPrice
import com.roziqrizal.rizqflow.domain.zakat.HaulBreakPolicy
import com.roziqrizal.rizqflow.domain.zakat.WealthCheck
import com.roziqrizal.rizqflow.domain.zakat.WealthItem
import com.roziqrizal.rizqflow.domain.zakat.ZakatPayment
import com.roziqrizal.rizqflow.domain.zakat.ZakatProfile
import java.time.LocalDate

internal fun ZakatProfileEntity.toDomain() = ZakatProfile(
    id = id,
    name = name,
    haulBreakPolicy = HaulBreakPolicy.valueOf(haulBreakPolicy),
    archived = archived,
)

internal fun ZakatProfile.toEntity() = ZakatProfileEntity(
    id = id,
    name = name,
    haulBreakPolicy = haulBreakPolicy.name,
    archived = archived,
)

internal fun WealthItemEntity.toDomain() = WealthItem(
    id = id,
    profileId = profileId,
    kind = kind,
    label = label,
    value = Money.rupiah(value),
    goldMilligrams = goldMilligrams,
    updatedAtMillis = updatedAt,
)

internal fun WealthItem.toEntity() = WealthItemEntity(
    id = id,
    profileId = profileId,
    kind = kind,
    label = label,
    value = value.minor,
    goldMilligrams = goldMilligrams,
    updatedAt = updatedAtMillis,
)

internal fun GoldPriceEntity.toDomain() = GoldPrice(day = LocalDate.ofEpochDay(day), perGram = Money.rupiah(perGram), source = source)

internal fun GoldPrice.toEntity() = GoldPriceEntity(day = day.toEpochDay(), perGram = perGram.minor, source = source)

internal fun WealthCheckEntity.toDomain() = WealthCheck(
    id = id,
    profileId = profileId,
    day = LocalDate.ofEpochDay(day),
    netWealth = Money.rupiah(netWealth),
    nisab = Money.rupiah(nisab),
)

internal fun WealthCheck.toEntity() = WealthCheckEntity(
    id = id,
    profileId = profileId,
    day = day.toEpochDay(),
    netWealth = netWealth.minor,
    nisab = nisab.minor,
)

internal fun ZakatPaymentEntity.toDomain() = ZakatPayment(id = id, profileId = profileId, day = LocalDate.ofEpochDay(day), transactionId = TransactionId(transactionId))

internal fun ZakatPayment.toEntity() = ZakatPaymentEntity(id = id, profileId = profileId, day = day.toEpochDay(), transactionId = transactionId.value)
