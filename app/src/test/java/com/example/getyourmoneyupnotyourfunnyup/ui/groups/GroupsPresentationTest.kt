package com.example.getyourmoneyupnotyourfunnyup.ui.groups

import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import org.junit.Assert.*
import org.junit.Test

class GroupsPresentationTest {
    private fun group(id: String, name: String, fav: Boolean = false, at: Long = 0) =
        DashboardGroup(Group(id, name, createdBy = "u"), 0, fav, at)

    private val groups = listOf(
        group("kol", "Kollegiet 4. sal", fav = true, at = 5),
        group("fri", "Friday Bar crew", fav = true, at = 4),
        group("ski", "Ski trip Åre", at = 3),
        group("fam", "Family dinners", at = 1)
    )
    private val balances = mapOf("kol" to 14500L, "fri" to -9450L, "ski" to -28600L, "fam" to 0L)

    @Test fun filterCountsMatchEachChip() {
        val counts = filterCounts(groups, balances)
        assertEquals(4, counts[GroupListFilter.All])
        assertEquals(2, counts[GroupListFilter.Favourites])
        assertEquals(2, counts[GroupListFilter.YouOwe])
        assertEquals(1, counts[GroupListFilter.OwedToYou])
        assertEquals(1, counts[GroupListFilter.Settled])
    }

    @Test fun unknownBalanceIsNeverCountedAsSettledOrOwing() {
        val counts = filterCounts(groups, null)
        assertEquals(0, counts[GroupListFilter.Settled])
        assertEquals(0, counts[GroupListFilter.YouOwe])
        assertEquals(4, counts[GroupListFilter.All])
    }

    @Test fun searchIsCaseAndAccentInsensitiveAndCombinesWithFilter() {
        assertEquals(listOf("ski"), visibleGroups(groups, balances, GroupListFilter.All, "are").map { it.group.id })
        assertEquals(listOf("fri"), visibleGroups(groups, balances, GroupListFilter.YouOwe, "FRIDAY").map { it.group.id })
        assertTrue(visibleGroups(groups, balances, GroupListFilter.Favourites, "ski").isEmpty())
    }

    @Test fun listIsMostRecentlyActiveFirst() {
        assertEquals(listOf("kol", "fri", "ski", "fam"), visibleGroups(groups, balances, GroupListFilter.All, "").map { it.group.id })
    }

    @Test fun summaryUsesDanishFormatAndHidesWhileSearching() {
        val owe = visibleGroups(groups, balances, GroupListFilter.YouOwe, "")
        assertEquals("You owe 380,50 kr across 2 groups", groupsSummary(GroupListFilter.YouOwe, owe, balances, "")?.text)
        val owed = visibleGroups(groups, balances, GroupListFilter.OwedToYou, "")
        assertEquals("You’re owed 145,00 kr across 1 group", groupsSummary(GroupListFilter.OwedToYou, owed, balances, "")?.text)
        assertNull(groupsSummary(GroupListFilter.YouOwe, owe, balances, "fri"))
        assertNull(groupsSummary(GroupListFilter.All, groups, balances, ""))
    }

    @Test fun emptyStatesExplainWhy() {
        assertEquals("No groups match “Roskilde”", emptyGroupsMessage(GroupListFilter.All, " Roskilde ").title)
        assertEquals("No favourites yet", emptyGroupsMessage(GroupListFilter.Favourites, "").title)
        assertEquals("You don’t owe anyone", emptyGroupsMessage(GroupListFilter.YouOwe, "").title)
    }

    @Test fun contactsAreRecognisedAndNormalised() {
        assertEquals(ContactKind.Email, contactKind(" Anna.B@Mail.dk "))
        assertEquals(ContactKind.Phone, contactKind("+45 22 81 40 17"))
        assertEquals(ContactKind.Invalid, contactKind("anna"))
        assertEquals(ContactKind.Invalid, contactKind("1234"))
        assertEquals("anna.b@mail.dk", normaliseContact(" Anna.B@Mail.dk "))
        assertEquals("+4522814017", normaliseContact("+45 22 81 40 17"))
        assertEquals("anna.b", inviteDisplayName("anna.b@mail.dk"))
        assertTrue(Contact("j", "Jonas", "", "+45 22 81 40 17").matchesContact("+4522814017"))
        assertEquals(MemberDraft.Invited("Anna@Mail.dk").key, MemberDraft.Invited("anna@mail.dk ").key)
    }

    @Test fun memberSubtitleShowsRoleAndDietaryNotes() {
        val mads = GroupMemberItem("mads", "Mads", GroupRole.ADMIN, dietaryRules = setOf(DietaryRule.Alcohol))
        assertEquals("Organiser · no alcohol", memberSubtitle(mads, "you"))
        assertEquals("That’s you", memberSubtitle(GroupMemberItem("you", "You"), "you"))
        assertEquals("Member", memberSubtitle(GroupMemberItem("ida", "Ida"), "you"))
        assertEquals("No alcohol · vegetarian", memberSubtitle(GroupMemberItem("s", "S", dietaryRules = setOf(DietaryRule.Meat, DietaryRule.Alcohol)), "you"))
    }

    @Test fun removalWarningOnlyForOpenOrUnknownBalances() {
        assertFalse(requiresRemovalWarning(0))
        assertTrue(requiresRemovalWarning(-14000))
        assertTrue(requiresRemovalWarning(51200))
        assertTrue(requiresRemovalWarning(null))
        assertEquals(
            "Jonas still owes 140,00 kr. Removing them can leave the group unbalanced.",
            removalWarningText("Jonas", -14000)
        )
        assertEquals(
            "Mads is still owed 1.240,50 kr. Removing them can leave the group unbalanced.",
            removalWarningText("Mads", 124050)
        )
    }

    @Test fun memberChipLabels() {
        assertEquals("You owe 286,00", memberBalanceLabel(-28600, isYou = true))
        assertEquals("Owes 140,00", memberBalanceLabel(-14000, isYou = false))
        assertEquals("Owed 512,00", memberBalanceLabel(51200, isYou = false))
        assertEquals("Settled", memberBalanceLabel(0, isYou = false))
    }
}
