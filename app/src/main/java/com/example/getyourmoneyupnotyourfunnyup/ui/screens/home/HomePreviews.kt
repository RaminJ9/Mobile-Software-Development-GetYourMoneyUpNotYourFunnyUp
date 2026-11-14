package com.example.getyourmoneyupnotyourfunnyup.ui.screens.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState

private const val PreviewNow = 1793703600000L

private fun previewHomeData(): HomeData {
    val user = User("you", "preview@example.com", "Victor")
    val definitions = listOf(
        Triple("ski", "Ski trip Åre", 1), Triple("friday", "Friday Bar crew", 2),
        Triple("padel", "Padel Tuesdays", 5), Triple("flat", "Kollegiet 4. sal", 0)
    )
    val groups = definitions.mapIndexed { index, (id, name, colour) ->
        DashboardGroup(
            Group(id, name, currency = "DKK", createdBy = "you", members = listOf(GroupMember(id, "you"))),
            colour,
            index != 2,
            PreviewNow - index * 86400000L,
            if (id == "flat") 1 else 0
        )
    }
    val payments = listOf(
        OutstandingPayment("ski-mads", "ski", "Mads", PreviewNow - 2 * 86400000L, DebtTransfer("you", "mads", 28600, "DKK")),
        OutstandingPayment("friday-sofie", "friday", "Sofie", PreviewNow, DebtTransfer("you", "sofie", 9450, "DKK")),
        OutstandingPayment("padel-jonas", "padel", "Jonas", PreviewNow - 86400000L, DebtTransfer("you", "jonas", 3200, "DKK"))
    )
    val balances = mapOf(
        "ski" to -28600L,
        "friday" to -9450L,
        "padel" to -3200L,
        "flat" to 14500L
    ).mapValues { MemberBalance("you", it.value, "DKK") }
    val spend = listOf("ski" to 120000L, "flat" to 62000L, "friday" to 38000L, "padel" to 16000L).map { (id, amount) ->
        PaidOutPayment(
            Settlement("paid-$id", id, "you", "mads", amount, "DKK", "Cash", createdAt = PreviewNow - 86400000L),
            "Mads"
        )
    }
    return HomeData(user, groups, FinancialSummary(balances, payments), spend, true, nowMillis = PreviewNow)
}

@Composable
private fun HomeExample(state: HomeUiState) {
    GetYourMoneyUpNotYourFunnyUpTheme { HomeScreen(state, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Home · light", widthDp = 412, heightDp = 835)
@Preview(name = "Home · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePreview() = HomeExample(HomeUiState.Success(previewHomeData(), canSettle = true))

@Preview(name = "Payments due · light", widthDp = 412, heightDp = 835)
@Preview(name = "Payments due · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaymentsPreview() = HomeExample(HomeUiState.Success(
        previewHomeData(),
        paymentsExpanded = true,
        paymentStates = mapOf("ski-mads" to InlineActionState.Confirming),
        canSettle = true
))

@Preview(name = "Slice · light", widthDp = 412, heightDp = 835)
@Preview(name = "Slice · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SlicePreview() = HomeExample(HomeUiState.Success(previewHomeData(), selectedGroupId = "ski"))

@Preview(name = "Empty · light", widthDp = 412, heightDp = 835)
@Preview(name = "Empty · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyPreview() = HomeExample(HomeUiState.Success(previewHomeData().copy(groups = emptyList())))

@Preview(name = "Offline · light", widthDp = 412, heightDp = 835)
@Preview(name = "Offline · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflinePreview() = HomeExample(HomeUiState.Success(previewHomeData().copy(offlineSavedAt = PreviewNow)))

@Preview(name = "Paid up · light", widthDp = 412, heightDp = 835)
@Preview(name = "Paid up · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaidUpPreview() {
    val data = previewHomeData()
    HomeExample(HomeUiState.Success(data.copy(finances = FinancialSummary(
                    data.finances!!.balancesByGroup.mapValues { it.value.copy(netBalanceCents = maxOf(0, it.value.netBalanceCents)) },
                    emptyList()
    ))))
}
