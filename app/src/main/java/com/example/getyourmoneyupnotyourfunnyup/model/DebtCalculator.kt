package com.example.getyourmoneyupnotyourfunnyup.model

/**
 * Deterministic balance and debt-settlement maths. All amounts are integer minor units (øre/cents).
 *
 * Balances are computed from the ledger instead of being stored, so concurrent offline edits never
 * conflict on a mutable "balance" field.
 */
object DebtCalculator {

    /**
     * Net balance per person: positive = is owed money, negative = owes money.
     *
     * - An active expense credits the payer with the full amount and debits each split.
     * - A completed settlement credits the debtor (they paid) and debits the creditor.
     * - Deleted expenses and settlements that are not [SettlementStatus.COMPLETED] are ignored.
     *
     * Every id in [memberIds] is present in the result (with 0 if untouched).
     */
    fun netBalances(
        memberIds: Collection<String>,
        expenses: List<Expense>,
        settlements: List<Settlement>
    ): Map<String, Long> {
        val net = LinkedHashMap<String, Long>()
        memberIds.forEach { net[it] = 0L }
        for (expense in expenses) {
            if (expense.isDeleted) continue
            net[expense.payerId] = (net[expense.payerId] ?: 0L) + expense.amountCents
            for (split in expense.splits) {
                net[split.userId] = (net[split.userId] ?: 0L) - split.amountOwedCents
            }
        }
        for (settlement in settlements) {
            if (settlement.status != SettlementStatus.COMPLETED) continue
            net[settlement.debtorId] = (net[settlement.debtorId] ?: 0L) + settlement.amountCents
            net[settlement.creditorId] = (net[settlement.creditorId] ?: 0L) - settlement.amountCents
        }
        return net
    }

    /**
     * Greedy debt simplification: repeatedly lets the person who owes the most pay the person who is
     * owed the most. Produces at most `n - 1` transfers and clears every balance. Ties are broken by
     * id so the result is stable between runs and devices.
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
