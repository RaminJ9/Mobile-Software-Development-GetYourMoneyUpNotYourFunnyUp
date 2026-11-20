package com.example.getyourmoneyupnotyourfunnyup.model

import java.security.MessageDigest

/** Same financial obligation gets the same reference across screens/devices; sync flags do not change it. */
object SettlementReference {
    fun id(groupId: String, transfer: DebtTransfer, expenses: List<Expense>, settlements: List<Settlement>): String {
        val parts = mutableListOf(groupId, transfer.debtorId, transfer.creditorId, transfer.currency, transfer.amountCents.toString())
        expenses.filterNot { it.isDeleted }.sortedBy { it.id }.forEach { e ->
            parts += listOf(e.id, e.payerId, e.currency, e.amountCents.toString())
            e.splits.sortedBy { it.userId }.forEach { parts += listOf(it.userId, it.amountOwedCents.toString()) }
        }
        settlements.filter { it.status == SettlementStatus.COMPLETED && it.debtorId == transfer.debtorId && it.creditorId == transfer.creditorId }.sortedBy { it.id }.forEach {
            parts += listOf(it.id, it.debtorId, it.creditorId, it.currency, it.amountCents.toString())
        }
        val canonical = parts.joinToString("") { "${it.length}:$it" }
        return "pay-" + MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
