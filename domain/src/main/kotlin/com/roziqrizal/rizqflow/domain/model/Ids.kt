package com.roziqrizal.rizqflow.domain.model

/**
 * Pengenal entitas. Berupa teks (UUID) supaya backup, restore, dan sync fase 2 tidak bentrok
 * antar-perangkat; value class membuat pengenal jenis berbeda tidak tertukar.
 */
@JvmInline
value class RoomId(val value: String)

@JvmInline
value class AccountId(val value: String)

@JvmInline
value class CategoryId(val value: String)

@JvmInline
value class TransactionId(val value: String)
