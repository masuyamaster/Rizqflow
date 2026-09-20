package com.roziqrizal.rizqflow.domain.model

/** Jenis akun: tempat uang berada. */
enum class AccountKind { CASH, BANK, EWALLET }

/**
 * Tipe ruang; menentukan arti "terpenuhi" (docs/konsep.md). Menunaikan dan Menumbuhkan
 * terpenuhi saat penggunaan mencapai jatah; Mencukupi masuk Perlu perhatian di 85% jatah.
 */
enum class RoomKind { MENUNAIKAN, MENUMBUHKAN, MENCUKUPI }

/** Jenis transaksi. Jumlah selalu positif; jenis menentukan arah uang. */
enum class TransactionKind { INCOME, EXPENSE, TRANSFER }

/** Dari mana transaksi berasal. */
enum class TransactionOrigin { MANUAL, QUICK, REPLY, CORRECTION, DRAFT, IMPORT }

/** Jenis baris di profil harta zakat; [DEDUCTION] adalah pengurang (utang jangka pendek). */
enum class WealthKind { GOLD, CASH_SAVINGS, INVESTMENT, RECEIVABLE, OTHER, DEDUCTION }

/** Asal harga emas. */
enum class GoldPriceSource { MANUAL, AUTO }
