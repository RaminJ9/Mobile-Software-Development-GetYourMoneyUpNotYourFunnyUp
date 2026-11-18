package com.example.getyourmoneyupnotyourfunnyup.model

import java.math.BigInteger

/** S8b Settle up: exact minimum-payment suggestions for one group's balances. No writes. */
object SettlementCalculator {
    /**
     * Input must contain unique members, one currency and a zero-sum balance ledger.
     * Positive balances receive money; negative balances pay. Zero rows are ignored.
     * Results are deterministic regardless of input order, and use integer minor units.
     *
     * Exact minimization is combinatorial: intended for the project's small groups, not
     * large networks. Run off the UI thread when integrating into a ViewModel.
     */
    fun suggest(balances: List<MemberBalance>): List<DebtTransfer> {
        require(balances.all { it.userId.isNotBlank() && it.currency.isNotBlank() }) {
            "Member IDs and currency must not be blank."
        }
        require(balances.map { it.userId }.distinct().size == balances.size) { "Member IDs must be unique." }
        require(balances.map { it.currency }.distinct().size <= 1) { "Balances must use one currency." }
        require(balances.fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.netBalanceCents) } == BigInteger.ZERO) {
            "Balances must sum to zero."
        }
        val debtors = balances.filter { it.netBalanceCents < 0 }.sortedBy { it.userId }
        val creditors = balances.filter { it.netBalanceCents > 0 }.sortedBy { it.userId }
        if (debtors.isEmpty()) return emptyList()
        val currency = balances.first().currency
        val initial = debtors.map { BigInteger.valueOf(it.netBalanceCents).negate() } +
            creditors.map { BigInteger.valueOf(it.netBalanceCents) }
        val memo = mutableMapOf<List<BigInteger>, List<DebtTransfer>>()
        fun search(remaining: List<BigInteger>): List<DebtTransfer> {
            memo[remaining]?.let { return it }
            val owing = debtors.indices.filter { remaining[it].signum() > 0 }
            val owed = creditors.indices.filter { remaining[debtors.size + it].signum() > 0 }
            if (owing.isEmpty()) return emptyList()
            val lowerBound = maxOf(owing.size, owed.size)
            var best: List<DebtTransfer>? = null
            // Some optimal plan has a leaf transfer that clears at least one endpoint.
            // Exploring every debtor/creditor pair includes those plans; a greedy single
            // choice does not guarantee the minimum payment count.
            for (debtor in owing) {
                for (creditor in owed) {
                    val creditIndex = debtors.size + creditor
                    val amount = remaining[debtor].min(remaining[creditIndex])
                    // Range-check before converting; longValueExact requires Android API 31.
                    check(amount.signum() > 0 && amount.bitLength() <= 63) { "Transfer exceeds the supported Long range." }
                    val next = remaining.toMutableList()
                    next[debtor] = next[debtor] - amount
                    next[creditIndex] = next[creditIndex] - amount
                    val candidate = listOf(DebtTransfer(debtors[debtor].userId,
                        creditors[creditor].userId, amount.toLong(), currency)) + search(next)
                    if (best == null || candidate.size < best.size) best = candidate
                    if (best.size == lowerBound) return best.also { memo[remaining] = it }
                }
            }
            return checkNotNull(best).also { memo[remaining] = it }
        }
        return search(initial)
    }
}
