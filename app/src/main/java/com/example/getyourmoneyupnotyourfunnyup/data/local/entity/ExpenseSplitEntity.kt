package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit

data class ExpenseSplitEntity(
    val id: String,
    val expenseId: String,
    val userId: String,
    val amountOwedCents: Long,
    val shareWeight: Double = 1.0
) {
    fun toDomain(): ExpenseSplit = ExpenseSplit(
        id = id,
        expenseId = expenseId,
        userId = userId,
        amountOwedCents = amountOwedCents,
        shareWeight = shareWeight
    )

    companion object {
        fun fromDomain(split: ExpenseSplit): ExpenseSplitEntity = ExpenseSplitEntity(
            id = split.id,
            expenseId = split.expenseId,
            userId = split.userId,
            amountOwedCents = split.amountOwedCents,
            shareWeight = split.shareWeight
        )
    }
}
