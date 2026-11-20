package com.example.getyourmoneyupnotyourfunnyup.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.expenses.ExpenseScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.expenses.decodeReceipt
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ExpenseUiState
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ExpenseUiTest {
    @get:Rule val compose = createComposeRule()
    private var receiptFile: File? = null
    @After fun cleanup() { receiptFile?.delete() }
    private fun state(expense: Expense = Expense("e", "g", "Dinner", 10000, currency = "DKK", payerId = "ida", createdBy = "ida",
        createdAt = 1700000000000, splits = listOf(ExpenseSplit("s1", "e", "you", 5000), ExpenseSplit("s2", "e", "ida", 5000)))) =
        ExpenseUiState.Success("you", listOf(expense), mapOf("you" to "Ramin", "ida" to "Ida"), selectedId = "e")
    private fun detailsText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("expense-details")))
    private fun waitForSheet() = compose.waitUntil(10000) { compose.onAllNodesWithText("Expense details").fetchSemanticsNodes().isNotEmpty() }

    @Test fun detailsShowsPayerTotalSharesAndOwnershipAndClosesWithoutRemovingExpense() {
        val current = mutableStateOf(state())
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { ExpenseScreen(current.value, {}, { current.value = current.value.copy(selectedId = null) }, {}) } }
        waitForSheet()
        detailsText("Paid by Ida").assertIsDisplayed()
        detailsText("100,00 kr").assertIsDisplayed()
        detailsText("Synced").assertIsDisplayed()
        detailsText("Ramin (You)").performScrollTo().assertIsDisplayed()
        detailsText("Only Ida can edit or remove this expense.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove expense").assertDoesNotExist()
        detailsText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Expense details").assertDoesNotExist()
        compose.onNodeWithTag("expense-e").assertExists()
        compose.runOnIdle { assertEquals(1, current.value.expenses.size) }
    }
    @Test fun missingLocalAndRemoteOnlyReceiptsHaveHonestFallbackAndPendingStatus() {
        val original = state().expenses.single().copy(receiptLocalUri = "file:///no-such-receipt.png", syncStatus = SyncStatus.PENDING_INSERT)
        val current = mutableStateOf(state(original))
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { ExpenseScreen(current.value, {}, {}, {}) } }
        waitForSheet()
        detailsText("Saved on your phone \u00b7 waiting to sync").assertExists()
        detailsText("Receipt photo").performScrollTo()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Receipt isn't available on this phone.").fetchSemanticsNodes().isNotEmpty() }
        detailsText("Receipt isn't available on this phone.").assertIsDisplayed()
        compose.runOnIdle { current.value = state(original.copy(receiptLocalUri = null, receiptRemotePath = "groups/g/receipts/e.jpg")) }
        detailsText("Receipt photo isn't saved on this phone.").assertExists()
    }
    @Test fun localReceiptIsSampledAndCanBeViewedWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "receipt_test_${UUID.randomUUID()}.png"); receiptFile = file
        val bitmap = Bitmap.createBitmap(256, 2048, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val uri = Uri.fromFile(file).toString()
        val decoded = decodeReceipt(context.contentResolver, uri)!!
        assertTrue(maxOf(decoded.width, decoded.height) <= 1600); decoded.recycle()
        val current = state(state().expenses.single().copy(receiptLocalUri = uri))
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { ExpenseScreen(current, {}, {}, {}) } }
        waitForSheet(); detailsText("Receipt photo").performScrollTo()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("receipt-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("View receipt").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Full receipt").assertIsDisplayed()
        compose.onNodeWithText("Close receipt").performClick()
        compose.onNodeWithText("Expense details").assertExists()
    }
    @Test fun ownerConfirmationStaysInSheetAndKeepDoesNotRemove() {
        val original = state().expenses.single().copy(createdBy = "you")
        val current = mutableStateOf(state(original).copy(removalAvailable = true))
        var removes = 0
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme {
            ExpenseScreen(current.value, {}, {}, {},
                onAskRemoval = { current.value = current.value.copy(removalAction = com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState.Confirming) },
                onKeepExpense = { current.value = current.value.copy(removalAction = com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState.Idle) },
                onRemove = { removes++ })
        } }
        waitForSheet(); detailsText("Remove expense").performScrollTo().performClick()
        detailsText("Keep it").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, removes) }
        detailsText("Remove expense").performScrollTo().performClick()
        detailsText("Remove").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, removes) }
        compose.onNodeWithText("Expense details").assertExists()
    }

    @Test fun groupHistoryShowsWhoPaidWhomWithAmountAndActualSyncStatus() {
        val current = state().copy(selectedId = null, expenses = emptyList(), settlements = listOf(
            Settlement("p", "g", "you", "ida", 12345, "DKK", "Simulated", syncStatus = SyncStatus.PENDING_INSERT)))
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { ExpenseScreen(current, {}, {}, {}) } }
        compose.onNodeWithText("Ramin paid Ida").assertIsDisplayed()
        compose.onNodeWithText("123,45 kr").assertIsDisplayed()
        compose.onNodeWithText("Virtual payment recorded").assertIsDisplayed()
        compose.onNodeWithText("Saved on your phone \u00b7 waiting to sync").assertExists()
        compose.onNodeWithText("Synced").assertDoesNotExist()
    }

}
