package com.example.getyourmoneyupnotyourfunnyup.data.remote

import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SettlementEntity
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

/**
 * Pure mapper converting [Settlement] and [SettlementEntity] to and from
 * Cloud Firestore `/groups/{groupId}/settlements/{settlementId}` document maps.
 */
internal object SettlementDocumentMapper {

    fun toMap(
        settlement: Settlement,
        recordedBy: String = settlement.debtorId
    ): Map<String, Any?> = mapOf(
        "id" to settlement.id,
        "groupId" to settlement.groupId,
        "debtorId" to settlement.debtorId,
        "creditorId" to settlement.creditorId,
        "amountCents" to settlement.amountCents,
        "currency" to settlement.currency,
        "paymentMethod" to settlement.paymentMethod,
        "status" to settlement.status.name,
        "createdAt" to settlement.createdAt,
        "recordedBy" to recordedBy.ifBlank { settlement.debtorId }
    )

    fun toMap(
        settlement: SettlementEntity,
        recordedBy: String = settlement.debtorId
    ): Map<String, Any?> = toMap(settlement.toDomain(), recordedBy)

    fun fromMap(
        data: Map<String, Any?>,
        settlementId: String,
        fallbackGroupId: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): Settlement = entityFromMap(
        data = data,
        settlementId = settlementId,
        fallbackGroupId = fallbackGroupId,
        nowMillis = nowMillis
    ).toDomain()

    fun entityFromMap(
        data: Map<String, Any?>,
        settlementId: String,
        fallbackGroupId: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): SettlementEntity {
        val resolvedId = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: settlementId
        val groupId = (data["groupId"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackGroupId
        return SettlementEntity(
            id = resolvedId,
            groupId = groupId,
            debtorId = (data["debtorId"] as? String).orEmpty(),
            creditorId = (data["creditorId"] as? String).orEmpty(),
            amountCents = (data["amountCents"] as? Number)?.toLong() ?: 0L,
            currency = (data["currency"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_CURRENCY,
            paymentMethod = (data["paymentMethod"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_PAYMENT_METHOD,
            status = SettlementStatus.fromValue(data["status"] as? String),
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: nowMillis,
            syncStatus = SyncStatus.SYNCED
        )
    }

    private const val DEFAULT_CURRENCY = "DKK"
    private const val DEFAULT_PAYMENT_METHOD = "MobilePay"
}
