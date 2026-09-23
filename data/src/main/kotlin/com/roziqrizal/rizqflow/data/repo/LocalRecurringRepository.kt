package com.roziqrizal.rizqflow.data.repo

import com.roziqrizal.rizqflow.data.db.RecurringRuleEntity
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.recurring.RecurringRepository
import com.roziqrizal.rizqflow.domain.recurring.RecurringRule
import java.time.LocalDate

class LocalRecurringRepository(private val db: RizqflowDatabase) : RecurringRepository {
    override suspend fun all(): List<RecurringRule> = db.recurring().all().map { it.toDomain() }

    override suspend fun find(id: String): RecurringRule? = db.recurring().find(id)?.toDomain()

    override suspend fun save(rule: RecurringRule) = db.recurring().upsert(rule.toEntity())

    override suspend fun delete(id: String) = db.recurring().delete(id)
}

internal fun RecurringRuleEntity.toDomain() = RecurringRule(
    id = id,
    kind = kind,
    amount = money(amount, currency),
    accountId = AccountId(accountId),
    toAccountId = toAccountId?.let(::AccountId),
    roomId = roomId?.let(::RoomId),
    categoryId = categoryId?.let(::CategoryId),
    incomeSource = incomeSource,
    note = note,
    frequency = frequency,
    startDate = LocalDate.ofEpochDay(startDate),
    nextDue = LocalDate.ofEpochDay(nextDue),
    endDate = endDate?.let(LocalDate::ofEpochDay),
    active = active,
)

internal fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id,
    kind = kind,
    amount = amount.minor,
    currency = amount.currency.code,
    accountId = accountId.value,
    toAccountId = toAccountId?.value,
    roomId = roomId?.value,
    categoryId = categoryId?.value,
    incomeSource = incomeSource,
    note = note,
    frequency = frequency,
    startDate = startDate.toEpochDay(),
    nextDue = nextDue.toEpochDay(),
    endDate = endDate?.toEpochDay(),
    active = active,
)
