package com.example.getyourmoneyupnotyourfunnyup.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.balances.BalanceScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BalanceUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun chartShowsNamedDebtsCreditsAndZeroWithAccessibleDirections() {
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme {
            BalanceScreen(BalanceUiState.Success("Trip", listOf(
                BalanceMember("you", "Ramin", -12550, isYou = true),
                BalanceMember("ida", "Ida", 12550), BalanceMember("zero", "Mads", 0)
            ), true, 0, false), {}, {})
        } }
        compose.onNodeWithText("You").assertIsDisplayed()
        compose.onNodeWithText("−125,50 kr").assertIsDisplayed()
        compose.onNodeWithText("+125,50 kr").assertIsDisplayed()
        compose.onNodeWithTag("balance-bar-you").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Owes 125,50 kr"))
        compose.onNodeWithTag("balance-bar-ida").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Is owed 125,50 kr"))
        compose.onNodeWithTag("balance-bar-zero").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Settled, 0,00 kr"))
    }
    @Test fun failureOffersRetryAndBack() {
        var retried = false; var returned = false
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme {
            BalanceScreen(BalanceUiState.Error("Unavailable"), { returned = true }, { retried = true })
        } }
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(retried); assertTrue(returned) }
    }
    @Test fun emptyLedgerDoesNotInventPayments() {
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme {
            BalanceScreen(BalanceUiState.Success("Trip", listOf(BalanceMember("you", "Ramin", 0)), false, 0, false), {}, {})
        } }
        compose.onNodeWithText("No expenses or completed repayments yet. Balances are zero.").assertIsDisplayed()
        compose.onNodeWithText("Pay").assertDoesNotExist()
        compose.onNodeWithText("0,00 kr").assertIsDisplayed()
    }
}
