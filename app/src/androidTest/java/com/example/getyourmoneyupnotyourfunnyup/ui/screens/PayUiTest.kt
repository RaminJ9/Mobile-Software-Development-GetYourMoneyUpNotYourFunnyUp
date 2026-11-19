package com.example.getyourmoneyupnotyourfunnyup.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.*
import com.example.getyourmoneyupnotyourfunnyup.data.repository.*
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.balances.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PayUiTest {
    @get:Rule val compose = createComposeRule()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private var database: AppDatabase? = null
    @After fun cleanup() {
        compose.runOnIdle { owner.viewModelStore.clear() }
        database?.close()
    }
    private val transfer = DebtTransfer("you", "ida", 5000, "DKK")
    private fun state(action: InlineActionState = InlineActionState.Idle, saving: Boolean = false, ownDebt: Boolean = true) =
        BalanceUiState.Success("Trip", listOf(BalanceMember("you", "Ramin", -5000, isYou = ownDebt),
            BalanceMember("ida", "Ida", 5000, isYou = !ownDebt)), true, 0, false,
            listOf(transfer), listOf(BalancePayment("request", transfer, "Ramin", "Ida", action)), true, saving)

    @Test fun confirmingThenCancellingDoesNotSaveAndConfirmUpdatesRealLocalBalances() = localPaymentFlow(creditor = false)

    @Test fun creditorMarksAsPaidWithInlineConfirmationAndRealBalanceUpdate() = localPaymentFlow(creditor = true)

    private fun localPaymentFlow(creditor: Boolean) {
        val actorId = if (creditor) "ida" else "you"
        val actionLabel = if (creditor) "Mark as paid" else "Pay 50,00 kr"
        val db = AppDatabase.createInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        database = db
        db.userDao.upsertUser(UserEntity("you", "you@test", "Ramin"))
        db.userDao.upsertUser(UserEntity("ida", "ida@test", "Ida"))
        db.groupDao.upsertGroupWithMembers(GroupEntity("g", "Trip", currency = "DKK", createdBy = "ida"),
            listOf(GroupMemberEntity("g", "you"), GroupMemberEntity("g", "ida")))
        db.expenseDao.upsertExpenseWithSplits(ExpenseEntity("e", "g", "Food", 10000,
            currency = "DKK", payerId = "ida", createdBy = "ida"), listOf(
            ExpenseSplitEntity("s1", "e", "you", 5000), ExpenseSplitEntity("s2", "e", "ida", 5000)))
        lateinit var model: BalanceViewModel
        compose.runOnIdle {
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = BalanceViewModel(
                    actorId, "g", LocalBalanceRepository(db) { actorId },
                    paymentRepository = LocalPaymentRepository(db) { actorId }) as T
            }
            model = ViewModelProvider(owner, factory)[BalanceViewModel::class.java]
        }
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { BalanceRoute(model, {}) } }
        compose.waitUntil(10000) { model.state.value is BalanceUiState.Success }
        compose.onNodeWithText(actionLabel).performScrollTo().performClick()
        compose.onNodeWithText(if (creditor) "Mark payment from Ramin as received?" else "Confirm simulated payment to Ida?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(db.settlementDao.getSettlementsForGroup("g").isEmpty())
        compose.onNodeWithText(actionLabel).performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(10000) {
            (model.state.value as? BalanceUiState.Success)?.let { state ->
                state.payments.any { it.action is InlineActionState.Done } && state.members.all { it.amountCents == 0L }
            } == true
        }
        compose.onNodeWithText("Paid \u00b7 settled").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Saved on your phone \u00b7 waiting to sync").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(actionLabel).assertDoesNotExist()
        compose.onNodeWithText("No payments needed. Everyone is settled.").performScrollTo().assertIsDisplayed()
        assertEquals(1, db.settlementDao.getSettlementsForGroup("g").size)
        assertEquals(1, db.syncQueueDao.getAllPending().size)
        assertEquals(SyncStatus.PENDING_INSERT, db.settlementDao.getSettlementsForGroup("g").single().syncStatus)
    }
    @Test fun failedPaymentShowsRetryThatRequestsConfirmationAgain() {
        val ids = mutableListOf<String>()
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme {
            BalanceScreen(state(InlineActionState.Failed("Couldn't save payment. Try again.")), {}, {}, onPay = { ids += it })
        } }
        compose.onNodeWithText("Couldn't save payment. Try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pay 50,00 kr").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("request"), ids) }
        compose.onNodeWithText("Confirm").assertDoesNotExist()
    }
    @Test fun creditorCannotUseDebtorsPaymentButton() {
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { BalanceScreen(state(ownDebt = false), {}, {}) } }
        compose.onNodeWithText("Ramin \u2192 Ida").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pay 50,00 kr").assertDoesNotExist()
        compose.onNodeWithText("Mark as paid").assertIsDisplayed()
    }
    @Test fun savingCardHasNoRepeatedConfirmOrPayControls() {
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { BalanceScreen(state(InlineActionState.Working, saving = true), {}, {}) } }
        compose.onNodeWithText("Saving...").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertDoesNotExist()
        compose.onNodeWithText("Pay 50,00 kr").assertDoesNotExist()
    }
}
