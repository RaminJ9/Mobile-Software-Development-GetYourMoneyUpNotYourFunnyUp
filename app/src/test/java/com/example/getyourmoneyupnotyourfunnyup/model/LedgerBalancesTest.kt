package com.example.getyourmoneyupnotyourfunnyup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Balances and settle-up plans as the screens read them: from the ledger, through Ramin's calculators. */
class LedgerBalancesTest {

    private fun expense(id: String, payer: String, splits: Map<String, Long>, deleted: Boolean = false) = Expense(
        id = id, groupId = "g", title = id, amountCents = splits.values.sum(), currency = "DKK", payerId = payer,
        createdBy = payer, isDeleted = deleted, splits = splits.map { (user, owed) -> ExpenseSplit("$id-$user", id, user, owed) },
    )

    private fun payment(from: String, to: String, amount: Long, status: SettlementStatus = SettlementStatus.COMPLETED) =
        Settlement(id = "$from-$to-$amount", groupId = "g", debtorId = from, creditorId = to, amountCents = amount,
            currency = "DKK", paymentMethod = "test", status = status)

    private fun ledger(members: List<String>, expenses: List<Expense>, settlements: List<Settlement> = emptyList()) = LedgerSnapshot(
        currentUserId = members.first(),
        people = members.associateWith { Person(it, it, "$it@example.dk") },
        groups = listOf(Group("g", "Trip", currency = "DKK", createdBy = members.first(), members = members.map { GroupMember("g", it) })),
        expenses = expenses,
        settlements = settlements,
    )

    /** Applies a plan to the balances; every balance must end at zero. */
    private fun assertClears(net: Map<String, Long>, plan: List<DebtTransfer>) {
        val after = net.toMutableMap()
        plan.forEach {
            assertTrue(it.amountCents > 0)
            after[it.debtorId] = after.getValue(it.debtorId) + it.amountCents
            after[it.creditorId] = after.getValue(it.creditorId) - it.amountCents
        }
        assertTrue("every balance is settled: $after", after.values.all { it == 0L })
    }

    @Test
    fun balances_ignoreRemovedExpensesAndUnfinishedPayments_andCompletedPaymentsClearThem() {
        val expenses = listOf(
            expense("kept", "a", mapOf("a" to 5_000, "b" to 5_000)),
            expense("removed", "b", mapOf("a" to 45_000, "b" to 45_000), deleted = true),
        )
        val open = ledger(listOf("a", "b"), expenses, listOf(payment("b", "a", 5_000, SettlementStatus.PENDING)))
        assertEquals(mapOf("a" to 5_000L, "b" to -5_000L), open.netBalances("g"))
        assertEquals(listOf(DebtTransfer("b", "a", 5_000, "DKK")), open.settlePlan("g"))

        val paid = ledger(listOf("a", "b"), expenses, listOf(payment("b", "a", 5_000)))
        assertEquals(mapOf("a" to 0L, "b" to 0L), paid.netBalances("g"))
        assertTrue(paid.settlePlan("g").isEmpty())
    }

    @Test
    fun settlePlan_findsTheFewestPayments_biggestFirst() {
        // a owes 800, c 200, f 300; b is owed 600, d 300, e 400. Greedy needs 5 payments, 4 are enough.
        val snapshot = ledger(
            listOf("a", "b", "c", "d", "e", "f"),
            listOf(
                expense("hotel", "b", mapOf("a" to 60_000)),
                expense("dinner", "e", mapOf("a" to 20_000, "c" to 20_000)),
                expense("taxi", "d", mapOf("f" to 30_000)),
            ),
        )
        val net = snapshot.netBalances("g")
        assertEquals(5, DebtCalculator.simplify(net).size)

        val plan = snapshot.settlePlan("g")
        assertEquals(4, plan.size)
        assertClears(net, plan)
        assertEquals(plan.sortedByDescending { it.amountCents }, plan)
    }

    @Test(timeout = 5_000)
    fun settlePlan_bigGroups_useTheQuickPlan() {
        // 16 people with a balance would take the exact search minutes or run out of memory.
        val people = (1..16).map { "p$it" }
        val snapshot = ledger(
            people,
            people.drop(1).mapIndexed { i, who -> expense("e$i", who, mapOf(people[(i + 3) % 16] to (i + 1) * 1_337L)) },
        )
        val net = snapshot.netBalances("g")
        assertTrue(net.values.count { it != 0L } > 8)

        val plan = snapshot.settlePlan("g")
        assertTrue(plan.size < net.size)
        assertClears(net, plan)
    }
}
