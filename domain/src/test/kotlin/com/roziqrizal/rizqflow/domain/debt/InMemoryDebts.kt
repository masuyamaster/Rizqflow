package com.roziqrizal.rizqflow.domain.debt

/** Penyimpanan tiruan di memori untuk menguji [DebtService] tanpa database. */
class InMemoryDebts : DebtRepository {
    val debtRows = linkedMapOf<String, Debt>()
    val paymentRows = linkedMapOf<String, DebtPayment>()

    override suspend fun all(): List<Debt> = debtRows.values.toList()

    override suspend fun find(id: String): Debt? = debtRows[id]

    override suspend fun save(debt: Debt) {
        debtRows[debt.id] = debt
    }

    override suspend fun delete(id: String) {
        debtRows.remove(id)
        paymentRows.values.removeAll { it.debtId == id }
    }

    override suspend fun allPayments(): List<DebtPayment> = paymentRows.values.toList()

    override suspend fun findPayment(id: String): DebtPayment? = paymentRows[id]

    override suspend fun savePayment(payment: DebtPayment) {
        paymentRows[payment.id] = payment
    }

    override suspend fun deletePayment(id: String) {
        paymentRows.remove(id)
    }
}
