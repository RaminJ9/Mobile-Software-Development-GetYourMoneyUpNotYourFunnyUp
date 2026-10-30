package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

data class SettlementEntity(
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
) {
    fun toDomain(): Settlement = Settlement(
        id = id,
        groupId = groupId,
        debtorId = debtorId,
        creditorId = creditorId,
        amountCents = amountCents,
        currency = currency,
        paymentMethod = paymentMethod,
        status = status,
        createdAt = createdAt,
        syncStatus = syncStatus
    )

    companion object {
        fun fromDomain(settlement: Settlement): SettlementEntity = SettlementEntity(
            id = settlement.id,
            groupId = settlement.groupId,
            debtorId = settlement.debtorId,
            creditorId = settlement.creditorId,
            amountCents = settlement.amountCents,
            currency = settlement.currency,
            paymentMethod = settlement.paymentMethod,
            status = settlement.status,
            createdAt = settlement.createdAt,
            syncStatus = settlement.syncStatus
        )
    }
}
