package com.example.getyourmoneyupnotyourfunnyup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtCalculatorTest {

    private fun expense(id: String, payer: String, amount: Long, splits: Map<String, Long>, deleted: Boolean = false) = Expense(
        id = id, groupId = "g", title = id, amountCents = amount, payerId = payer, createdBy = payer, isDeleted = deleted,
        splits = splits.map { (user, owed) -> ExpenseSplit("$id-$user", id, user, owed) },
    )

    private fun settlement(from: String, to: String, amount: Long, status: SettlementStatus = SettlementStatus.COMPLETED) =
        Settlement(id = "$from-$to-$amount", groupId = "g", debtorId = from, creditorId = to, amountCents = amount,
            paymentMethod = "test", status = status)

    @Test
    fun netBalances_creditsPayerAndDebitsEachSplit() {
        val net = DebtCalculator.netBalances(
            listOf("a", "b", "c"),
            listOf(expense("dinner", "a", 30_000, mapOf("a" to 10_000, "b" to 10_000, "c" to 10_000))),
            emptyList(),
        )
        assertEquals(mapOf("a" to 20_000L, "b" to -10_000L, "c" to -10_000L), net)
    }

    @Test
    fun netBalances_ignoresDeletedExpensesAndUnfinishedPayments() {
        val net = DebtCalculator.netBalances(
            listOf("a", "b"),
            listOf(
                expense("kept", "a", 10_000, mapOf("a" to 5_000, "b" to 5_000)),
                expense("removed", "b", 90_000, mapOf("a" to 45_000, "b" to 45_000), deleted = true),
            ),
            listOf(settlement("b", "a", 5_000, SettlementStatus.PENDING)),
        )
        assertEquals(5_000L, net["a"])
        assertEquals(-5_000L, net["b"])
    }

    @Test
    fun completedSettlement_clearsTheDebt() {
        val net = DebtCalculator.netBalances(
            listOf("a", "b"),
            listOf(expense("taxi", "a", 20_000, mapOf("a" to 10_000, "b" to 10_000))),
            listOf(settlement("b", "a", 10_000)),
        )
        assertEquals(0L, net["a"])
        assertEquals(0L, net["b"])
        assertTrue(DebtCalculator.simplify(net).isEmpty())
    }

    @Test
    fun simplify_clearsEveryBalanceWithAtMostNMinusOneTransfers() {
        val net = mapOf("me" to -28_600L, "mads" to 51_200L, "ida" to 12_000L, "jonas" to -14_000L, "sofie" to -6_450L, "emil" to -14_150L)
        val transfers = DebtCalculator.simplify(net)

        assertTrue(transfers.size <= net.size - 1)
        val after = net.toMutableMap()
        transfers.forEach { t ->
            assertTrue(t.amountCents > 0)
            after[t.debtorId] = after.getValue(t.debtorId) + t.amountCents
            after[t.creditorId] = after.getValue(t.creditorId) - t.amountCents
        }
        assertTrue("every balance is settled: $after", after.values.all { it == 0L })
    }

    @Test
    fun simplify_largestDebtorPaysLargestCreditorFirst() {
        val transfers = DebtCalculator.simplify(mapOf("a" to -300L, "b" to -100L, "c" to 400L))
        assertEquals(DebtTransfer("a", "c", 300L, "DKK"), transfers.first())
        assertEquals(DebtTransfer("b", "c", 100L, "DKK"), transfers.last())
    }

    @Test
    fun simplify_isDeterministicForTies() {
        val net = mapOf("x" to -100L, "y" to -100L, "p" to 100L, "q" to 100L)
        assertEquals(DebtCalculator.simplify(net), DebtCalculator.simplify(net.entries.reversed().associate { it.toPair() }))
    }

    @Test
    fun splitEqually_givesLeftoverCentsToTheFirstPeople() {
        val parts = DebtCalculator.splitEqually(29_900, listOf("a", "b", "c", "d", "e", "f", "g"))
        assertEquals(29_900L, parts.values.sum())
        assertEquals(listOf(4_272L, 4_272L, 4_272L, 4_271L, 4_271L, 4_271L, 4_271L), parts.values.toList())
    }
}
