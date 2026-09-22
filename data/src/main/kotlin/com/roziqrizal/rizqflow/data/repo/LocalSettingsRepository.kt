package com.roziqrizal.rizqflow.data.repo

import com.roziqrizal.rizqflow.data.db.AppSettingEntity
import com.roziqrizal.rizqflow.data.db.DayCheckEntity
import com.roziqrizal.rizqflow.data.db.RizqflowDatabase
import com.roziqrizal.rizqflow.domain.ledger.SettingsRepository
import java.time.LocalDate

class LocalSettingsRepository(private val db: RizqflowDatabase) : SettingsRepository {
    override suspend fun get(key: String): String? = db.settings().get(key)

    override suspend fun put(key: String, value: String) = db.settings().put(AppSettingEntity(key, value))

    override suspend fun isDayChecked(day: LocalDate): Boolean = db.settings().isDayChecked(day.toEpochDay()) > 0

    override suspend fun markDayChecked(day: LocalDate) = db.settings().markDayChecked(DayCheckEntity(day.toEpochDay()))
}
