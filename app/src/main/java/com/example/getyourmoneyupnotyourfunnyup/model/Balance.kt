package com.example.getyourmoneyupnotyourfunnyup.model

/**
 * Net balance for a member in a group (positive = owed money, negative = owes money).
 */
data class MemberBalance(
    val userId: String,
    val netBalanceCents: Long,
    val currency: String = "EUR"
)

/**
 * Simplified debt transfer instruction (who pays whom and how much).
 */
data class DebtTransfer(
    val debtorId: String,
    val creditorId: String,
    val amountCents: Long,
    val currency: String = "EUR"
)
