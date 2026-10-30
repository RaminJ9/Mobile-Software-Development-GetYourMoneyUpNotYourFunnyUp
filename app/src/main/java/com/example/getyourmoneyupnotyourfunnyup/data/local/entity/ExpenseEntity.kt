package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

data class ExpenseEntity(
    val id: String,
    val groupId: String,
    val title: String,
    val amountCents: Long,
    val currency: String = "EUR",
    val payerId: String,
    val category: String = "General",
    val notes: String? = null,
    val receiptLocalUri: String? = null,
    val receiptRemotePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String,
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
) {
    fun toDomain(splits: List<ExpenseSplit> = emptyList()): Expense = Expense(
        id = id,
        groupId = groupId,
        title = title,
        amountCents = amountCents,
        currency = currency,
        payerId = payerId,
        category = category,
        notes = notes,
        receiptLocalUri = receiptLocalUri,
        receiptRemotePath = receiptRemotePath,
        splits = splits,
        createdAt = createdAt,
        createdBy = createdBy,
        isDeleted = isDeleted,
        syncStatus = syncStatus
    )

    companion object {
        fun fromDomain(expense: Expense): ExpenseEntity = ExpenseEntity(
            id = expense.id,
            groupId = expense.groupId,
            title = expense.title,
            amountCents = expense.amountCents,
            currency = expense.currency,
            payerId = expense.payerId,
            category = expense.category,
            notes = expense.notes,
            receiptLocalUri = expense.receiptLocalUri,
            receiptRemotePath = expense.receiptRemotePath,
            createdAt = expense.createdAt,
            createdBy = expense.createdBy,
            isDeleted = expense.isDeleted,
            syncStatus = expense.syncStatus
        )
    }
}
