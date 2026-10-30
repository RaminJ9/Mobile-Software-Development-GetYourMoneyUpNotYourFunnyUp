package com.example.getyourmoneyupnotyourfunnyup.model

enum class SettlementStatus {
    PENDING,
    COMPLETED,
    FAILED;

    companion object {
        fun fromValue(value: String?): SettlementStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: COMPLETED
    }
}

data class Settlement(
    val id: String,
    val groupId: String,
    val debtorId: String,
    val creditorId: String,
    val amountCents: Long,
    val currency: String = "EUR",
    val paymentMethod: String,
    val status: SettlementStatus = SettlementStatus.COMPLETED,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
