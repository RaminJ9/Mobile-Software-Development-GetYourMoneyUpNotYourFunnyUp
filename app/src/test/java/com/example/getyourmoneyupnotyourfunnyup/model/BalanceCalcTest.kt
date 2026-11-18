package com.example.getyourmoneyupnotyourfunnyup.model

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class BalanceCalcTest {
    private fun expense(
        amount: Long = 100L,
        shares: List<Pair<String, Long>> = listOf("a" to 50L, "b" to 50L),
        payer: String = "a",
        id: String = "expense"
    ) = Expense(
        id = id, groupId = "group", title = "Dinner", amountCents = amount, currency = "DKK",
        payerId = payer, createdBy = payer,
        splits = shares.mapIndexed { index, (userId, share) -> ExpenseSplit("$id-$index", id, userId, share) }
    )
    private fun repayment(amount: Long = 30L, id: String = "payment") = Settlement(
        id = id, groupId = "group", debtorId = "b", creditorId = "a", amountCents = amount,
        currency = "DKK", paymentMethod = "Simulated"
    )
    private fun calculate(
        expenses: List<Expense> = emptyList(),
        settlements: List<Settlement> = emptyList(),
        members: List<String> = listOf("a", "b")
    ) = BalanceCalculator.calculate("group", "DKK", members, expenses, settlements)
    private fun assertBalances(expected: Map<String, Long>, actual: List<MemberBalance>) {
        assertEquals(expected, actual.associate { it.userId to it.netBalanceCents })
        assertTrue(actual.all { it.currency == "DKK" })
        assertEquals(BigInteger.ZERO, actual.fold(BigInteger.ZERO) { sum, row -> sum + BigInteger.valueOf(row.netBalanceCents) })
    }

    @Test fun emptyLedgerKeepsEveryCurrentMemberAtZeroInStableOrder() {
        val rows = calculate(members = listOf("c", "a", "b", "a"))
        assertBalances(mapOf("a" to 0L, "b" to 0L, "c" to 0L), rows)
        assertEquals(listOf("a", "b", "c"), rows.map { it.userId })
        assertTrue(calculate(members = emptyList()).isEmpty())
    }

    @Test fun payerReceivesOnlyTheAmountOthersOwe() {
        assertBalances(mapOf("a" to 50L, "b" to -50L), calculate(listOf(expense())))
    }

    @Test fun finalizedUnevenOreSharesArePreservedWithoutRoundingAgain() {
        assertBalances(mapOf("a" to 66L, "b" to -33L, "c" to -33L),
            calculate(listOf(expense(100L, listOf("a" to 34L, "b" to 33L, "c" to 33L))), members = listOf("a", "b", "c")))
    }

    @Test fun expensesFromMultiplePayersOffsetEachOther() {
        val second = expense(60L, listOf("a" to 30L, "b" to 30L), payer = "b", id = "second")
        assertBalances(mapOf("a" to 20L, "b" to -20L), calculate(listOf(expense(), second)))
    }

    @Test fun selectedSubsetAndZeroSharesDoNotChargeExcludedMembers() {
        assertBalances(mapOf("a" to 100L, "b" to -100L, "c" to 0L),
            calculate(listOf(expense(shares = listOf("a" to 0L, "b" to 100L))), members = listOf("a", "b", "c")))
    }

    @Test fun formerMembersRemainInBalancesWhenReferencedByLedger() {
        assertBalances(mapOf("former" to 100L, "a" to -100L, "b" to 0L),
            calculate(listOf(expense(shares = listOf("a" to 100L), payer = "former"))))
    }

    @Test fun deletedExpensesAreIgnoredIncludingTheirObsoleteSplits() {
        val deleted = expense(payer = "former").copy(isDeleted = true, splits = emptyList())
        assertBalances(mapOf("a" to 0L, "b" to 0L), calculate(listOf(deleted)))
    }

    @Test fun completedRepaymentMovesBothBalancesTowardsZero() {
        assertBalances(mapOf("a" to 20L, "b" to -20L), calculate(listOf(expense()), listOf(repayment())))
        assertBalances(mapOf("a" to 0L, "b" to 0L), calculate(listOf(expense()), listOf(repayment(50L))))
    }

    @Test fun pendingAndFailedRepaymentsDoNotChangeBalances() {
        val ignored = listOf(SettlementStatus.PENDING, SettlementStatus.FAILED).map { repayment().copy(status = it) }
        assertBalances(mapOf("a" to 50L, "b" to -50L), calculate(listOf(expense()), ignored))
    }

    @Test fun offlineSyncStatusDoesNotDiscardActiveExpensesOrCompletedRepayments() {
        for (status in SyncStatus.entries) {
            assertBalances(mapOf("a" to 20L, "b" to -20L), calculate(
                listOf(expense().copy(syncStatus = status)), listOf(repayment().copy(syncStatus = status))))
        }
    }

    @Test fun actualOverpaymentBecomesACreditRatherThanBeingSilentlyClamped() {
        assertBalances(mapOf("a" to -10L, "b" to 10L), calculate(listOf(expense()), listOf(repayment(60L))))
    }

    @Test fun mismatchedOrMissingSharesAreRejected() {
        for (bad in listOf(expense(shares = listOf("a" to 99L)), expense().copy(splits = emptyList()))) {
            assertThrows(IllegalArgumentException::class.java) { calculate(listOf(bad)) }
        }
    }

    @Test fun wrongExpenseReferencesAndDuplicateSplitsAreRejected() {
        val valid = expense()
        val wrongReference = valid.copy(splits = valid.splits.map { it.copy(expenseId = "other") })
        val duplicateUser = expense(shares = listOf("b" to 50L, "b" to 50L))
        val duplicateId = valid.copy(splits = valid.splits.map { it.copy(id = "same") })
        for (bad in listOf(wrongReference, duplicateUser, duplicateId)) {
            assertThrows(IllegalArgumentException::class.java) { calculate(listOf(bad)) }
        }
    }

    @Test fun negativeExpensesOrSharesAndInvalidSettlementAmountsAreRejected() {
        val negativeTotal = expense(-100L, listOf("a" to -50L, "b" to -50L))
        val negativeShare = expense(shares = listOf("a" to -1L, "b" to 101L))
        for (bad in listOf(negativeTotal, negativeShare)) {
            assertThrows(IllegalArgumentException::class.java) { calculate(listOf(bad)) }
        }
        for (amount in listOf(0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) { calculate(settlements = listOf(repayment(amount))) }
        }
        assertThrows(IllegalArgumentException::class.java) { calculate(settlements = listOf(repayment().copy(creditorId = "b"))) }
    }

    @Test fun recordsFromAnotherGroupAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { calculate(listOf(expense().copy(groupId = "other"))) }
        assertThrows(IllegalArgumentException::class.java) { calculate(settlements = listOf(repayment().copy(groupId = "other"))) }
    }

    @Test fun currenciesAreValidatedAndNeverRelabelled() {
        assertThrows(IllegalArgumentException::class.java) { calculate(listOf(expense().copy(currency = "EUR"))) }
        assertThrows(IllegalArgumentException::class.java) { calculate(settlements = listOf(repayment().copy(currency = "EUR"))) }
        val euros = BalanceCalculator.calculate("group", "EUR", listOf("a", "b"), listOf(expense().copy(currency = "EUR")))
        assertEquals(listOf("EUR", "EUR"), euros.map { it.currency })
        assertEquals(listOf(50L, -50L), euros.map { it.netBalanceCents })
    }

    @Test fun duplicateLedgerRecordsAreRejectedInsteadOfBeingCountedTwice() {
        assertThrows(IllegalArgumentException::class.java) { calculate(listOf(expense(), expense())) }
        assertThrows(IllegalArgumentException::class.java) { calculate(settlements = listOf(repayment(), repayment())) }
    }

    @Test fun blankGroupCurrencyOrFinancialMemberIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BalanceCalculator.calculate("", "DKK", listOf("a"), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { BalanceCalculator.calculate("group", "", listOf("a"), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { calculate(members = listOf("")) }
        assertThrows(IllegalArgumentException::class.java) { calculate(listOf(expense(payer = ""))) }
        assertThrows(IllegalArgumentException::class.java) { calculate(listOf(expense(shares = listOf("" to 100L)))) }
        assertThrows(IllegalArgumentException::class.java) { calculate(settlements = listOf(repayment().copy(debtorId = ""))) }
    }

    @Test fun zeroCostExpenseWithZeroSharesDoesNotCreateDebt() {
        assertBalances(mapOf("a" to 0L, "b" to 0L), calculate(listOf(expense(0L, listOf("a" to 0L, "b" to 0L)))))
    }

    @Test fun inputOrderDoesNotChangeResults() {
        val expenses = listOf(expense(), expense(60L, listOf("a" to 30L, "b" to 30L), "b", "second"))
        val payments = listOf(repayment(10L), repayment(5L, "second-payment"))
        assertEquals(calculate(expenses, payments), calculate(expenses.reversed().map { it.copy(splits = it.splits.reversed()) }, payments.reversed(), listOf("b", "a")))
    }

    @Test fun exactIntermediateSumsAllowLargeOffsettingLedgerEntries() {
        val expenses = listOf(
            expense(Long.MAX_VALUE, listOf("b" to Long.MAX_VALUE), id = "first"),
            expense(Long.MAX_VALUE, listOf("b" to Long.MAX_VALUE), id = "second")
        )
        val payments = listOf(repayment(Long.MAX_VALUE, "first-payment"), repayment(Long.MAX_VALUE, "second-payment"))
        assertBalances(mapOf("a" to 0L, "b" to 0L), calculate(expenses, payments))
    }

    @Test fun unrepresentableFinalBalancesFailInsteadOfWrappingAround() {
        val first = expense(Long.MAX_VALUE, listOf("b" to Long.MAX_VALUE), id = "first")
        val second = expense(1L, listOf("b" to 1L), id = "second")
        assertThrows(ArithmeticException::class.java) { calculate(listOf(first, second)) }
    }
}
