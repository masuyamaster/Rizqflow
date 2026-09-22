package com.roziqrizal.rizqflow.domain.ledger

import java.time.LocalDate

/** Penyimpanan pengaturan tiruan di memori untuk menguji [ReminderService] tanpa database. */
class InMemorySettings : SettingsRepository {
    private val values = mutableMapOf<String, String>()
    private val checkedDays = mutableSetOf<LocalDate>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun isDayChecked(day: LocalDate): Boolean = day in checkedDays

    override suspend fun markDayChecked(day: LocalDate) {
        checkedDays += day
    }
}
