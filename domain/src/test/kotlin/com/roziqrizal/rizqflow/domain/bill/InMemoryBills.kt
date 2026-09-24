package com.roziqrizal.rizqflow.domain.bill

/** Penyimpanan tiruan di memori untuk menguji [BillService] tanpa database. */
class InMemoryBills : BillRepository {
    val rows = linkedMapOf<String, Bill>()

    override suspend fun all(): List<Bill> = rows.values.toList()

    override suspend fun find(id: String): Bill? = rows[id]

    override suspend fun save(bill: Bill) {
        rows[bill.id] = bill
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
    }
}
