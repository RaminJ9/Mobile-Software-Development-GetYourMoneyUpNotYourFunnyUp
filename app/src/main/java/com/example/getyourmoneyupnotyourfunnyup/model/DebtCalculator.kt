package com.example.getyourmoneyupnotyourfunnyup.model

/**
 * Splitting maths. All amounts are integer minor units (øre/cents). Balances themselves come from
 * [BalanceCalculator] and the exact settle-up plan from [SettlementCalculator].
 */
object DebtCalculator {

    /**
     * Greedy debt simplification: repeatedly lets the person who owes the most pay the person who is
     * owed the most. Produces at most `n - 1` transfers and clears every balance, but not always the
     * fewest; it is the fallback for groups too big for [SettlementCalculator]'s exact search. Ties are
     * broken by id so the result is stable between runs and devices.
     */
    fun simplify(netBalances: Map<String, Long>, currency: String = "DKK"): List<DebtTransfer> {
        val creditors = netBalances.filterValues { it > 0 }.map { Party(it.key, it.value) }.toMutableList()
        val debtors = netBalances.filterValues { it < 0 }.map { Party(it.key, -it.value) }.toMutableList()
        val order = compareByDescending<Party> { it.remaining }.thenBy { it.id }
        val transfers = mutableListOf<DebtTransfer>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            creditors.sortWith(order)
            debtors.sortWith(order)
            val creditor = creditors.first()
            val debtor = debtors.first()
            val amount = minOf(creditor.remaining, debtor.remaining)
            transfers += DebtTransfer(
                debtorId = debtor.id,
                creditorId = creditor.id,
                amountCents = amount,
                currency = currency
            )
            creditor.remaining -= amount
            debtor.remaining -= amount
            creditors.removeAll { it.remaining == 0L }
            debtors.removeAll { it.remaining == 0L }
        }
        return transfers
    }

    /**
     * Splits [totalCents] equally between [userIds]. Leftover cents go to the first people in the
     * list so the parts always add up to the total.
     */
    fun splitEqually(totalCents: Long, userIds: List<String>): Map<String, Long> {
        if (userIds.isEmpty()) return emptyMap()
        val base = Math.floorDiv(totalCents, userIds.size.toLong())
        val remainder = totalCents - base * userIds.size
        return userIds.mapIndexed { index, id ->
            id to base + if (index < remainder) 1L else 0L
        }.toMap(LinkedHashMap())
    }

    private class Party(val id: String, var remaining: Long)
}
