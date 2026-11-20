package com.example.getyourmoneyupnotyourfunnyup.model

import org.junit.Assert.*
import org.junit.Test

class SettleRefTest {
    private val transfer = DebtTransfer("a", "b", 5000, "DKK")
    private val expense = Expense("e", "g", "Dinner", 5000, currency = "DKK", payerId = "b", createdBy = "b",
        splits = listOf(ExpenseSplit("s", "e", "a", 5000)))
    @Test fun referenceIgnoresSyncFlagsPhotoAndDisplayText() {
        val id = SettlementReference.id("g", transfer, listOf(expense), emptyList())
        assertEquals(id, SettlementReference.id("g", transfer, listOf(expense.copy(title = "New title",
            receiptLocalUri = "file:///photo", syncStatus = SyncStatus.FAILED)), emptyList()))
    }
    @Test fun laterDebtForSameAmountGetsNewReference() {
        val id = SettlementReference.id("g", transfer, listOf(expense), emptyList())
        assertNotEquals(id, SettlementReference.id("g", transfer, listOf(expense.copy(id = "new", splits = listOf(ExpenseSplit("s2", "new", "a", 5000)))), emptyList()))
        assertNotEquals(id, SettlementReference.id("other-group", transfer, listOf(expense), emptyList()))
    }
}
