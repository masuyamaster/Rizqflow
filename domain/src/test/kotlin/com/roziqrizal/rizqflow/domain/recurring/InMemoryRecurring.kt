package com.roziqrizal.rizqflow.domain.recurring

/** Penyimpanan tiruan di memori untuk menguji [RecurringService] tanpa database. */
class InMemoryRecurring : RecurringRepository {
    val rows = linkedMapOf<String, RecurringRule>()

    override suspend fun all(): List<RecurringRule> = rows.values.toList()

    override suspend fun find(id: String): RecurringRule? = rows[id]

    override suspend fun save(rule: RecurringRule) {
        rows[rule.id] = rule
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
    }
}
