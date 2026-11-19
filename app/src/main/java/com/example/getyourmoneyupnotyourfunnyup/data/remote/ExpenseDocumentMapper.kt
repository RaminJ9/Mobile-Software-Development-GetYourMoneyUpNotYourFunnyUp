package com.example.getyourmoneyupnotyourfunnyup.data.remote

import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseSplitEntity
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

/**
 * Pure mapper converting [Expense], [ExpenseEntity] and [ExpenseSplitEntity] to and from
 * Cloud Firestore `/groups/{groupId}/expenses/{expenseId}` document maps.
 */
internal object ExpenseDocumentMapper {

    fun toMap(expense: Expense): Map<String, Any?> = mapOf(
        "id" to expense.id,
        "groupId" to expense.groupId,
        "title" to expense.title,
        "amountCents" to expense.amountCents,
        "currency" to expense.currency,
        "payerId" to expense.payerId,
        "category" to expense.category,
        "notes" to expense.notes,
        "receiptStoragePath" to expense.receiptRemotePath,
        "splits" to expense.splits.map(::splitToMap),
        "createdAt" to expense.createdAt,
        "createdBy" to expense.createdBy,
        "isDeleted" to expense.isDeleted
    )

    fun toMap(
        expense: ExpenseEntity,
        splits: List<ExpenseSplitEntity>
    ): Map<String, Any?> = mapOf(
        "id" to expense.id,
        "groupId" to expense.groupId,
        "title" to expense.title,
        "amountCents" to expense.amountCents,
        "currency" to expense.currency,
        "payerId" to expense.payerId,
        "category" to expense.category,
        "notes" to expense.notes,
        "receiptStoragePath" to expense.receiptRemotePath,
        "splits" to splits.map(::splitToMap),
        "createdAt" to expense.createdAt,
        "createdBy" to expense.createdBy,
        "isDeleted" to expense.isDeleted
    )

    fun splitToMap(split: ExpenseSplit): Map<String, Any?> = mapOf(
        "id" to split.id,
        "expenseId" to split.expenseId,
        "userId" to split.userId,
        "amountOwedCents" to split.amountOwedCents,
        "shareWeight" to split.shareWeight
    )

    fun splitToMap(split: ExpenseSplitEntity): Map<String, Any?> = mapOf(
        "id" to split.id,
        "expenseId" to split.expenseId,
        "userId" to split.userId,
        "amountOwedCents" to split.amountOwedCents,
        "shareWeight" to split.shareWeight
    )

    fun fromMap(
        data: Map<String, Any?>,
        expenseId: String,
        fallbackGroupId: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): Expense {
        val (entity, splitEntities) = entitiesFromMap(
            data = data,
            expenseId = expenseId,
            fallbackGroupId = fallbackGroupId,
            nowMillis = nowMillis
        )
        return entity.toDomain(splitEntities.map { it.toDomain() })
    }

    fun entitiesFromMap(
        data: Map<String, Any?>,
        expenseId: String,
        fallbackGroupId: String = "",
        receiptLocalUri: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<ExpenseEntity, List<ExpenseSplitEntity>> {
        val resolvedId = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: expenseId
        val groupId = (data["groupId"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackGroupId
        val payerId = (data["payerId"] as? String).orEmpty()
        val createdBy = (data["createdBy"] as? String)?.takeIf { it.isNotBlank() } ?: payerId
        val receiptRemotePath = ((data["receiptStoragePath"] as? String)
            ?: (data["receiptRemotePath"] as? String))?.takeIf { it.isNotBlank() }

        val splits = parseSplits(data["splits"] as? List<*>, resolvedId)
        val expenseEntity = ExpenseEntity(
            id = resolvedId,
            groupId = groupId,
            title = (data["title"] as? String).orEmpty(),
            amountCents = (data["amountCents"] as? Number)?.toLong() ?: 0L,
            currency = (data["currency"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_CURRENCY,
            payerId = payerId,
            category = (data["category"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_CATEGORY,
            notes = (data["notes"] as? String)?.takeIf { it.isNotBlank() },
            receiptLocalUri = receiptLocalUri,
            receiptRemotePath = receiptRemotePath,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: nowMillis,
            createdBy = createdBy,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            syncStatus = SyncStatus.SYNCED
        )
        return expenseEntity to splits
    }

    private fun parseSplits(
        rawSplits: List<*>?,
        expenseId: String
    ): List<ExpenseSplitEntity> {
        if (rawSplits.isNullOrEmpty()) return emptyList()
        return rawSplits.mapIndexedNotNull { index, entry ->
            val map = entry as? Map<*, *> ?: return@mapIndexedNotNull null
            val userId = (map["userId"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val splitId = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: "$expenseId:$userId:$index"
            ExpenseSplitEntity(
                id = splitId,
                expenseId = expenseId,
                userId = userId,
                amountOwedCents = (map["amountOwedCents"] as? Number)?.toLong() ?: 0L,
                shareWeight = (map["shareWeight"] as? Number)?.toDouble() ?: 1.0
            )
        }
    }

    private const val DEFAULT_CURRENCY = "DKK"
    private const val DEFAULT_CATEGORY = "General"
}
