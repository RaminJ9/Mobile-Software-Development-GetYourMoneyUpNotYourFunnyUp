package com.example.getyourmoneyupnotyourfunnyup.model

data class ExpenseSplit(
    val id: String,
    val expenseId: String,
    val userId: String,
    val amountOwedCents: Long,
    val shareWeight: Double = 1.0
)

data class Expense(
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
    val splits: List<ExpenseSplit> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String,
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
