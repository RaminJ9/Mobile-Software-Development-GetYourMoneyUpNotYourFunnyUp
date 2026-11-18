package com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SyncPresentation
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*

private const val PreviewNow = 1793703600000L
private const val Day = 86_400_000L

private fun previewGroupsData(): GroupsListData {
    val rows = listOf(
        listOf("kol", "Kollegiet 4. sal", 0, 7, 0L, true, 14500L),
        listOf("fri", "Friday Bar crew", 2, 9, 0L, true, -9450L),
        listOf("pad", "Padel Tuesdays", 5, 4, 1L, false, -3200L),
        listOf("ski", "Ski trip Åre", 1, 6, 3L, true, -28600L),
        listOf("mob", "Mobile Software team", 3, 6, 3L, false, 6000L),
        listOf("fam", "Family dinners", 4, 4, 3L, false, 0L),
        listOf("cph", "Copenhagen weekend", 6, 5, 20L, false, 0L)
    )
    val groups = rows.map { row ->
        val id = row[0] as String
        val members = (1..(row[3] as Int)).map { GroupMember(id, "m$it") }
        DashboardGroup(
            Group(id, row[1] as String, currency = "DKK", createdBy = "you", members = members),
            colourIndex = row[2] as Int,
            isFavourite = row[5] as Boolean,
            lastActivityAt = PreviewNow - (row[4] as Long) * Day,
            pendingExpenseCount = if (id == "kol") 1 else 0
        )
    }
    return GroupsListData("you", groups, rows.associate { it[0] as String to it[6] as Long }, PreviewNow)
}

private val previewContacts = listOf(
    Contact("mads", "Mads", "mads.h@mail.dk", colourIndex = 1),
    Contact("ida", "Ida", "ida.lund@mail.dk", colourIndex = 2),
    Contact("jonas", "Jonas", "", "+45 22 81 40 17", 0),
    Contact("sofie", "Sofie", "sofie@kollegiet.dk", colourIndex = 3)
)

private fun previewMembersData(): GroupMembersData = GroupMembersData(
    groupId = "ski",
    groupName = "Ski trip Åre",
    currentUserId = "you",
    members = listOf(
        GroupMemberItem("you", "You"),
        GroupMemberItem("mads", "Mads", GroupRole.ADMIN, 1, setOf(DietaryRule.Alcohol)),
        GroupMemberItem("ida", "Ida", colourIndex = 2),
        GroupMemberItem("jonas", "Jonas", colourIndex = 0),
        GroupMemberItem("sofie", "Sofie", colourIndex = 3, dietaryRules = setOf(DietaryRule.Meat)),
        GroupMemberItem("emil", "Emil", colourIndex = 4),
        GroupMemberItem("invite:anna.b@mail.dk", "anna.b", invited = true)
    ),
    balances = mapOf(
        "you" to -28600L, "mads" to 51200L, "ida" to 12000L, "jonas" to -14000L,
        "sofie" to -6450L, "emil" to -14150L, "invite:anna.b@mail.dk" to 0L
    ),
    suggestions = previewContacts.drop(3) + Contact("freja", "Freja", "freja@mail.dk", colourIndex = 5)
)

@Composable
private fun Themed(content: @Composable () -> Unit) = GetYourMoneyUpNotYourFunnyUpTheme(content = content)

/* ----- S4 & S-filter ----- */

@Preview(name = "Groups · light", widthDp = 412, heightDp = 915)
@Preview(name = "Groups · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupsPreview() = Themed {
    GroupsScreen(GroupsUiState.Success(previewGroupsData(), canFavourite = true), {}, {}, {})
}

@Preview(name = "Groups · you owe · light", widthDp = 412, heightDp = 915)
@Preview(name = "Groups · you owe · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupsYouOwePreview() = Themed {
    GroupsScreen(GroupsUiState.Success(previewGroupsData(), filter = GroupListFilter.YouOwe, canFavourite = true), {}, {}, {})
}

@Preview(name = "Groups · no match · light", widthDp = 412, heightDp = 915)
@Preview(name = "Groups · no match · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupsNoMatchPreview() = Themed {
    GroupsScreen(GroupsUiState.Success(previewGroupsData(), query = "Roskilde", canFavourite = true), {}, {}, {})
}

@Preview(name = "Groups · offline · light", widthDp = 412, heightDp = 915)
@Preview(name = "Groups · offline · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupsOfflinePreview() = Themed {
    val data = previewGroupsData().copy(sync = SyncPresentation(ConnectionState.Offline, savedAt = PreviewNow, pendingCount = 1))
    GroupsScreen(GroupsUiState.Success(data, filter = GroupListFilter.Favourites, canFavourite = true), {}, {}, {})
}

/* ----- S5 & S-Error ----- */

@Preview(name = "Create group · light", widthDp = 412, heightDp = 915)
@Preview(name = "Create group · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateGroupPreview() = Themed {
    CreateGroupScreen(CreateGroupUiState(User("you", "victor@mail.dk", "Victor"), knownContacts = previewContacts), {}, {})
}

@Preview(name = "Create group · user not found · light", widthDp = 412, heightDp = 915)
@Preview(name = "Create group · user not found · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateGroupNotFoundPreview() = Themed {
    CreateGroupScreen(
        CreateGroupUiState(
            User("you", "victor@mail.dk", "Victor"),
            name = "Roskilde 2027",
            description = "Tickets, camp gear and food",
            colour = GroupColourOption.Peach,
            query = "anna.b@mail.dk",
            members = listOf(MemberDraft.Existing(previewContacts[0]), MemberDraft.Existing(previewContacts[1]), MemberDraft.Invited("lars@mail.dk")),
            lookup = MemberLookupState.NotFound("anna.b@mail.dk"),
            knownContacts = previewContacts
        ),
        {}, {}
    )
}

/* ----- S8c, S8c-add & S-Warn ----- */

@Preview(name = "Members · light", widthDp = 412, heightDp = 915)
@Preview(name = "Members · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MembersPreview() = Themed {
    GroupMembersScreen(GroupMembersUiState.Success(previewMembersData(), canEdit = true), {})
}

@Preview(name = "Members · removal warning · light", widthDp = 412, heightDp = 915)
@Preview(name = "Members · removal warning · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MembersWarningPreview() = Themed {
    GroupMembersScreen(GroupMembersUiState.Success(previewMembersData(), warningFor = "jonas", canEdit = true), {})
}

@Preview(name = "Members · add member · light", widthDp = 412, heightDp = 915)
@Preview(name = "Members · add member · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MembersAddPreview() = Themed {
    GroupMembersScreen(
        GroupMembersUiState.Success(
            previewMembersData(),
            addPanel = AddMemberPanel("karl@mail.dk", MemberLookupState.NotFound("karl@mail.dk")),
            lastWrite = SyncStatus.PENDING_UPDATE,
            canEdit = true
        ),
        {}
    )
}
