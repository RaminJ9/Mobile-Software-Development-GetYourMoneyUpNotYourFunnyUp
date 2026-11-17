package com.example.getyourmoneyupnotyourfunnyup.model

import java.math.BigInteger

/** FE8 / US14, S8b: derive balances from one group's ledger without storing mutable totals. */
object BalanceCalculator {
    /**
     * Positive means the member is owed money; negative means they owe money.
     * Uses finalized minor-unit shares, not shareWeight or item-allocation rules.
     *
     * Deleted expenses and non-completed settlements are ignored. Local active expenses and
     * completed simulated repayments count even while their cloud synchronization is pending.
     * Current members receive a row at zero; former members referenced by the active ledger
     * remain included. Results are sorted by user ID, independent of input ordering.
     *
     * Throws IllegalArgumentException for inconsistent ledger inputs. ArithmeticException means
     * a final member balance cannot fit in Long. Exact intermediate sums avoid rounding/overflow
     * and allow large payments that offset each other to produce representable final balances.
     */
    fun calculate(
        groupId: String,
        currency: String,
        memberIds: Collection<String>,
        expenses: List<Expense>,
        settlements: List<Settlement> = emptyList()
    ): List<MemberBalance> {
        require(groupId.isNotBlank()) { "A group ID is required." }
        require(currency.isNotBlank()) { "A currency is required." }
        val balances = sortedMapOf<String, BigInteger>()
        fun add(userId: String, amount: BigInteger) {
            require(userId.isNotBlank()) { "Ledger member IDs must not be blank." }
            balances[userId] = (balances[userId] ?: BigInteger.ZERO) + amount
        }
        memberIds.forEach { add(it, BigInteger.ZERO) }

        val expenseIds = mutableSetOf<String>()
        for (expense in expenses) {
            if (expense.isDeleted) continue
            require(expense.id.isNotBlank() && expenseIds.add(expense.id)) { "Expense IDs must be non-blank and unique." }
            require(expense.groupId == groupId) { "Expense ${expense.id} belongs to another group." }
            require(expense.currency == currency) { "Expense ${expense.id} has a different currency." }
            require(expense.amountCents >= 0) { "Expense ${expense.id} has a negative total." }
            val splitIds = mutableSetOf<String>()
            val splitUsers = mutableSetOf<String>()
            val splitTotal = expense.splits.fold(BigInteger.ZERO) { total, split ->
                require(split.expenseId == expense.id) { "A split belongs to another expense." }
                require(split.id.isNotBlank() && splitIds.add(split.id)) { "Split IDs must be non-blank and unique within an expense." }
                require(split.userId.isNotBlank() && splitUsers.add(split.userId)) { "Each member must have one share per expense." }
                require(split.amountOwedCents >= 0) { "Expense shares must not be negative." }
                total + BigInteger.valueOf(split.amountOwedCents)
            }
            val amount = BigInteger.valueOf(expense.amountCents)
            require(splitTotal == amount) { "Shares for expense ${expense.id} do not match its total." }
            add(expense.payerId, amount)
            expense.splits.forEach { add(it.userId, BigInteger.valueOf(it.amountOwedCents).negate()) }
        }

        val settlementIds = mutableSetOf<String>()
        for (settlement in settlements) {
            if (settlement.status != SettlementStatus.COMPLETED) continue
            require(settlement.id.isNotBlank() && settlementIds.add(settlement.id)) { "Settlement IDs must be non-blank and unique." }
            require(settlement.groupId == groupId) { "Settlement ${settlement.id} belongs to another group." }
            require(settlement.currency == currency) { "Settlement ${settlement.id} has a different currency." }
            require(settlement.amountCents > 0) { "Settlement amounts must be positive." }
            require(settlement.debtorId != settlement.creditorId) { "A member cannot repay themselves." }
            val amount = BigInteger.valueOf(settlement.amountCents)
            add(settlement.debtorId, amount)
            add(settlement.creditorId, amount.negate())
        }

        check(balances.values.fold(BigInteger.ZERO, BigInteger::add) == BigInteger.ZERO) { "Group balances must sum to zero." }
        return balances.map { (userId, amount) ->
            val cents = try {
                amount.longValueExact()
            } catch (_: ArithmeticException) {
                throw ArithmeticException("Balance for $userId exceeds the supported Long range.")
            }
            MemberBalance(userId = userId, netBalanceCents = cents, currency = currency)
        }
    }
}
