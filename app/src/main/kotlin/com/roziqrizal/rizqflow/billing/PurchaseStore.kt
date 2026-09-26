package com.roziqrizal.rizqflow.billing

import android.content.Context
import com.roziqrizal.rizqflow.domain.entitlement.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Paket yang dimiliki perangkat ini, disimpan di SharedPreferences privat aplikasi: pembelian
 * Google Play terikat ke akun Play Store perangkat, bukan ke satu akun ledger lokal (satu perangkat
 * bisa punya banyak akun ledger, S30/S31), jadi statusnya app-wide seperti `ThemePreference`, bukan
 * per akun. `allowBackup` mati, jadi berkas ini tidak ikut cadangan otomatis Android.
 *
 * [grant] SEMENTARA menandai paket terbeli secara lokal tanpa transaksi sungguhan, sampai Google
 * Play Billing terpasang (menyusul di Tahap 7 lanjutan setelah listing Play Console ada, Tahap 8).
 * Tidak dipanggil dari mana pun saat ini secara sengaja: bottom sheet S21 belum memanggilnya supaya
 * tombol Beli tidak diam-diam membuka Pro tanpa pembayaran sungguhan.
 */
class PurchaseStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("purchases", Context.MODE_PRIVATE)
    private val _plans = MutableStateFlow(readPlans())
    val plans: StateFlow<Set<Plan>> = _plans.asStateFlow()

    fun grant(plan: Plan) {
        val updated = _plans.value + plan
        prefs.edit().putStringSet(KEY_PLANS, updated.map { it.name }.toSet()).apply()
        _plans.value = updated
    }

    private fun readPlans(): Set<Plan> =
        prefs.getStringSet(KEY_PLANS, emptySet())
            .orEmpty()
            .mapNotNull { raw -> Plan.entries.firstOrNull { it.name == raw } }
            .toSet()

    private companion object {
        const val KEY_PLANS = "owned_plans"
    }
}
