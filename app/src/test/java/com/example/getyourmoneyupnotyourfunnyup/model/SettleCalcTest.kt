package com.example.getyourmoneyupnotyourfunnyup.model

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class SettleCalcTest {
    private fun rows(vararg amounts: Long) = amounts.mapIndexed { index, amount -> MemberBalance("u$index", amount, "DKK") }
    private fun verify(rows: List<MemberBalance>): List<DebtTransfer> {
        val remaining = rows.associate { it.userId to BigInteger.valueOf(it.netBalanceCents) }.toMutableMap()
        val transfers = SettlementCalculator.suggest(rows)
        for (transfer in transfers) {
            assertTrue(transfer.amountCents > 0)
            assertNotEquals(transfer.debtorId, transfer.creditorId)
            assertEquals(rows.first().currency, transfer.currency)
            assertTrue(rows.single { it.userId == transfer.debtorId }.netBalanceCents < 0)
            assertTrue(rows.single { it.userId == transfer.creditorId }.netBalanceCents > 0)
            val amount = BigInteger.valueOf(transfer.amountCents)
            remaining[transfer.debtorId] = remaining.getValue(transfer.debtorId) + amount
            remaining[transfer.creditorId] = remaining.getValue(transfer.creditorId) - amount
        }
        assertTrue(remaining.values.all { it == BigInteger.ZERO })
        return transfers
    }
    @Test fun emptyAndSettledGroupsHaveNoSuggestions() {
        assertTrue(SettlementCalculator.suggest(emptyList()).isEmpty())
        assertTrue(verify(rows(0, 0)).isEmpty())
    }
    @Test fun oneDebtorPaysActualCreditor() {
        assertEquals(listOf(DebtTransfer("u0", "u2", 12345, "DKK")), verify(rows(-12345, 0, 12345)))
    }
    @Test fun splitsDebtBetweenMultipleCreditors() { assertEquals(2, verify(rows(-150, 50, 100)).size) }
    @Test fun combinesMultipleDebtors() { assertEquals(2, verify(rows(-50, -100, 150)).size) }
    @Test fun findsExactMinimumWhereLargestFirstGreedyNeedsExtraPayment() {
        // With credits 8 and 12, largest-first makes four payments; exact matching needs three.
        assertEquals(4, verify(rows(-8, -7, -5, 10, 10)).size)
        assertEquals(3, verify(rows(-8, -7, -5, 8, 12)).size)
    }
    @Test fun orderingIsStableAcrossInputPermutations() {
        val input = rows(-8, -7, -5, 8, 12)
        assertEquals(SettlementCalculator.suggest(input), SettlementCalculator.suggest(input.reversed()))
    }
    @Test fun handlesLongMinimumWithoutNegationOverflow() {
        assertEquals(2, verify(rows(Long.MIN_VALUE, Long.MAX_VALUE, 1)).size)
    }
    @Test fun totalsCanExceedLongWithoutOverflow() {
        assertEquals(2, verify(rows(-Long.MAX_VALUE, -Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)).size)
    }
    @Test fun preservesCurrencyWithoutConversion() {
        assertEquals("EUR", verify(rows(-10, 10).map { it.copy(currency = "EUR") }).single().currency)
    }
    @Test fun rejectsMixedCurrenciesDuplicateIdsAndUnbalancedInputs() {
        for (invalid in listOf(rows(-1, 2), rows(-1, 1).map { it.copy(userId = "same") },
            listOf(MemberBalance("a", -1, "DKK"), MemberBalance("b", 1, "EUR")),
            listOf(MemberBalance("", 0, "DKK")), listOf(MemberBalance("a", 0, "")))) {
            assertThrows(IllegalArgumentException::class.java) { SettlementCalculator.suggest(invalid) }
        }
    }
    @Test fun exhaustivelyMatchesIndependentZeroSumPartitionMinimumForSmallGroups() {
        // Maximum disjoint zero-sum subsets yield n - subsets minimum transfers.
        fun maxParts(values: List<Long>): Int {
            if (values.isEmpty()) return 0
            var best = 0
            for (mask in 1 until (1 shl values.size)) {
                if (mask and 1 == 0) continue
                if (values.indices.filter { mask and (1 shl it) != 0 }.sumOf { values[it] } != 0L) continue
                best = maxOf(best, 1 + maxParts(values.filterIndexed { index, _ -> mask and (1 shl index) == 0 }))
            }
            return best
        }
        for (a in -3L..3L) for (b in -3L..3L) for (c in -3L..3L) {
            val input = rows(a, b, c, -a-b-c)
            val nonzero = input.map { it.netBalanceCents }.filter { it != 0L }
            assertEquals(nonzero.size - maxParts(nonzero), verify(input).size)
        }
    }
}
