package com.example.getyourmoneyupnotyourfunnyup.ui.groups

import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SyncPresentation
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.formatDanishAmountFromCents
import java.util.Locale

/* ---------------------------------------------------------------------------------------------
 * S4 & S-filter · Groups tab
 * ------------------------------------------------------------------------------------------- */

/**
 * Snapshot for the Groups tab. [balancesByGroup] holds the current user's net balance per group in
 * øre (negative = you owe). `null` means balances are unavailable, which hides the money filters'
 * counts rather than pretending every group is settled.
 */
data class GroupsListData(
    val userId: String,
    val groups: List<DashboardGroup>,
    val balancesByGroup: Map<String, Long>? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val sync: SyncPresentation = SyncPresentation()
)

enum class GroupListFilter(val label: String) {
    All("All"),
    Favourites("Favourites"),
    YouOwe("You owe"),
    OwedToYou("Owed to you"),
    Settled("Settled")
}

fun GroupListFilter.matches(isFavourite: Boolean, balanceCents: Long?): Boolean = when (this) {
    GroupListFilter.All -> true
    GroupListFilter.Favourites -> isFavourite
    GroupListFilter.YouOwe -> balanceCents != null && balanceCents < 0
    GroupListFilter.OwedToYou -> balanceCents != null && balanceCents > 0
    GroupListFilter.Settled -> balanceCents == 0L
}

/** Case- and accent-insensitive "contains" search on the group name (e.g. `are` finds `Åre`). */
fun matchesGroupQuery(name: String, query: String): Boolean {
    val needle = normaliseForSearch(query)
    return needle.isEmpty() || normaliseForSearch(name).contains(needle)
}

private fun normaliseForSearch(value: String): String =
    java.text.Normalizer.normalize(value.trim().lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")

/** Most recently active first, so the directory feels like the Home "Recent" list. */
fun visibleGroups(
    groups: List<DashboardGroup>,
    balances: Map<String, Long>?,
    filter: GroupListFilter,
    query: String
): List<DashboardGroup> = groups
    .distinctBy { it.group.id }
    .filter { filter.matches(it.isFavourite, balances?.get(it.group.id)) && matchesGroupQuery(it.group.name, query) }
    .sortedByDescending { it.lastActivityAt }

fun filterCounts(groups: List<DashboardGroup>, balances: Map<String, Long>?): Map<GroupListFilter, Int> =
    GroupListFilter.entries.associateWith { filter ->
        groups.distinctBy { it.group.id }.count { filter.matches(it.isFavourite, balances?.get(it.group.id)) }
    }

/** Contextual strip shown above the list for the money filters (hidden while searching). */
data class GroupsSummary(val text: String, val owes: Boolean)

fun groupsSummary(
    filter: GroupListFilter,
    shown: List<DashboardGroup>,
    balances: Map<String, Long>?,
    query: String
): GroupsSummary? {
    if (query.isNotBlank() || balances == null || shown.isEmpty()) return null
    val amounts = shown.mapNotNull { balances[it.group.id] }
    val groupsLabel = if (shown.size == 1) "1 group" else "${shown.size} groups"
    return when (filter) {
        GroupListFilter.YouOwe -> GroupsSummary(
            "You owe ${formatDanishAmountFromCents(-amounts.filter { it < 0 }.sum())} across $groupsLabel",
            owes = true
        )
        GroupListFilter.OwedToYou -> GroupsSummary(
            "You’re owed ${formatDanishAmountFromCents(amounts.filter { it > 0 }.sum())} across $groupsLabel",
            owes = false
        )
        else -> null
    }
}

data class EmptyGroupsMessage(val title: String, val text: String)

fun emptyGroupsMessage(filter: GroupListFilter, query: String): EmptyGroupsMessage = when {
    query.isNotBlank() -> EmptyGroupsMessage(
        "No groups match “${query.trim()}”",
        "Check the spelling or try another filter."
    )
    filter == GroupListFilter.Favourites -> EmptyGroupsMessage(
        "No favourites yet",
        "Tap the star on a group to pin it here and on your home screen."
    )
    filter == GroupListFilter.YouOwe -> EmptyGroupsMessage("You don’t owe anyone", "All your groups are square.")
    filter == GroupListFilter.All -> EmptyGroupsMessage(
        "No groups yet",
        "Make a group for your flat, a trip or Friday bar."
    )
    else -> EmptyGroupsMessage("Nothing here", "All your groups are square.")
}

fun interface GroupFavouriteActions {
    /** Persists the per-user favourite flag locally first. Returns the local/sync state. */
    suspend fun setFavourite(groupId: String, favourite: Boolean): SyncStatus
}

/* ---------------------------------------------------------------------------------------------
 * S5 & S-Error · Create group, colour palette and invitations
 * ------------------------------------------------------------------------------------------- */

/** The 6 primary swatches offered in the picker (groups 7–9 can use the extra palette colours). */
enum class GroupColourOption(val label: String, val colourIndex: Int) {
    Blue("Blue", 0),
    Lavender("Lavender", 1),
    Peach("Peach", 2),
    Butter("Butter", 3),
    Teal("Teal", 4),
    Sand("Sand", 5)
}

/** Someone the app can add directly (an existing user found by email/phone or a past split). */
data class Contact(
    val userId: String,
    val displayName: String,
    val email: String,
    val phone: String? = null,
    val colourIndex: Int = 0
) {
    /** Caption under the name: email when available, otherwise the phone number. */
    val contactLine: String get() = email.ifBlank { phone.orEmpty() }
}

sealed interface MemberDraft {
    val key: String
    val displayName: String

    data class Existing(val contact: Contact) : MemberDraft {
        override val key: String get() = contact.userId
        override val displayName: String get() = contact.displayName
    }

    /** Unregistered person: added with an `invited` badge and joins when they sign up. */
    data class Invited(val contactValue: String) : MemberDraft {
        override val key: String get() = "invite:" + normaliseContact(contactValue)
        override val displayName: String get() = inviteDisplayName(contactValue)
    }
}

data class CreateGroupDraft(
    val name: String,
    val description: String?,
    val colourIndex: Int,
    val members: List<MemberDraft>
)

data class CreatedGroup(val groupId: String, val syncStatus: SyncStatus)

fun interface ContactDirectory {
    /** Looks up a registered user by email or phone. `null` = no one uses the app with it yet. */
    suspend fun find(query: String): Contact?
}

fun interface GroupCreationActions {
    suspend fun createGroup(draft: CreateGroupDraft): CreatedGroup
}

enum class ContactKind { Email, Phone, Invalid }

private val EmailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

fun contactKind(value: String): ContactKind {
    val trimmed = value.trim()
    if (EmailPattern.matches(trimmed)) return ContactKind.Email
    val digits = trimmed.removePrefix("+").filter { it.isDigit() }
    val onlyPhoneCharacters = trimmed.all { it.isDigit() || it in " +-()" }
    return if (onlyPhoneCharacters && digits.length in 8..15) ContactKind.Phone else ContactKind.Invalid
}

/** Lower-cased email, or a phone number with spaces/dashes removed (`+45 22 81 40 17` → `+4522814017`). */
fun normaliseContact(value: String): String {
    val trimmed = value.trim()
    return when (contactKind(trimmed)) {
        ContactKind.Phone -> (if (trimmed.startsWith("+")) "+" else "") + trimmed.filter { it.isDigit() }
        else -> trimmed.lowercase(Locale.ROOT)
    }
}

fun inviteDisplayName(value: String): String {
    val trimmed = value.trim()
    return if (contactKind(trimmed) == ContactKind.Email) trimmed.substringBefore('@') else trimmed
}

fun Contact.matchesContact(query: String): Boolean {
    val needle = normaliseContact(query)
    return normaliseContact(email) == needle || (phone != null && phonesMatch(phone, needle))
}

/**
 * Compares two phone strings after stripping spaces/formatting, also treating Danish 8-digit
 * national numbers with or without a `+45` / `0045` country code prefix as matching.
 */
fun phonesMatch(first: String?, second: String?): Boolean {
    if (first.isNullOrBlank() || second.isNullOrBlank()) return false
    val a = normalisePhoneDigits(first)
    val b = normalisePhoneDigits(second)
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || stripDanishCountryCode(a) == stripDanishCountryCode(b)
}

private fun normalisePhoneDigits(value: String): String {
    val trimmed = value.trim()
    return (if (trimmed.startsWith("+")) "+" else "") + trimmed.filter { it.isDigit() }
}

private fun stripDanishCountryCode(normalised: String): String = when {
    normalised.startsWith("+45") && normalised.length == 11 -> normalised.removePrefix("+45")
    normalised.startsWith("0045") && normalised.length == 12 -> normalised.removePrefix("0045")
    normalised.startsWith("45") && normalised.length == 10 -> normalised.removePrefix("45")
    else -> normalised
}

/* ---------------------------------------------------------------------------------------------
 * S8c & S8c-add & S-Warn · Members tab, roles, dietary notes, add member, removal warning
 * ------------------------------------------------------------------------------------------- */

enum class DietaryRule(val note: String) { Alcohol("no alcohol"), Meat("vegetarian") }

data class GroupMemberItem(
    val userId: String,
    val displayName: String,
    val role: GroupRole = GroupRole.MEMBER,
    val colourIndex: Int = 0,
    val dietaryRules: Set<DietaryRule> = emptySet(),
    val invited: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

/**
 * Snapshot for a group's Members tab. [balances] are each member's net balance in øre and are
 * supplied from the ledger (Person 4). `null` = unknown, so every removal asks for confirmation.
 */
data class GroupMembersData(
    val groupId: String,
    val groupName: String,
    val currentUserId: String,
    val members: List<GroupMemberItem>,
    val balances: Map<String, Long>? = null,
    val suggestions: List<Contact> = emptyList(),
    val canManageMembers: Boolean = true,
    val sync: SyncPresentation = SyncPresentation()
)

fun interface MemberActions {
    /** Adds a registered user, or invites [MemberDraft.Invited]. Saved locally first. */
    suspend fun addMember(groupId: String, member: MemberDraft): SyncStatus
}

fun interface MemberRemovalActions {
    suspend fun removeMember(groupId: String, userId: String): SyncStatus
}

/** "Organiser · no alcohol", "That's you", "Member", "Invited · waiting to join". */
fun memberSubtitle(member: GroupMemberItem, currentUserId: String): String {
    val base = when {
        member.userId == currentUserId -> "That’s you"
        member.invited -> "Invited · joins when they sign up"
        member.role == GroupRole.ADMIN -> "Organiser"
        else -> null
    }
    val notes = DietaryRule.entries.filter { it in member.dietaryRules }.map { it.note }
    return when {
        base != null && notes.isNotEmpty() -> base + " · " + notes.joinToString(" · ")
        base != null -> base
        notes.isNotEmpty() -> notes.joinToString(" · ").replaceFirstChar { it.titlecase(Locale.ROOT) }
        else -> "Member"
    }
}

/** Member chip label: "Owes 140,00", "Owed 512,00", "You owe 286,00" or "Settled". */
fun memberBalanceLabel(balanceCents: Long?, isYou: Boolean): String = when {
    balanceCents == null -> "Balance unavailable"
    balanceCents < 0 && isYou -> "You owe ${formatDanishAmountFromCents(-balanceCents, false)}"
    balanceCents < 0 -> "Owes ${formatDanishAmountFromCents(-balanceCents, false)}"
    balanceCents > 0 -> "Owed ${formatDanishAmountFromCents(balanceCents, false)}"
    else -> "Settled"
}

/** Removing someone with an open (or unknown) balance always needs an explicit second tap. */
fun requiresRemovalWarning(balanceCents: Long?): Boolean = balanceCents != 0L

fun removalWarningText(name: String, balanceCents: Long?): String = when {
    balanceCents == null -> "We can’t check $name’s balance right now. Removing them can leave the group unbalanced."
    balanceCents < 0 -> "$name still owes ${formatDanishAmountFromCents(-balanceCents)}. Removing them can leave the group unbalanced."
    balanceCents > 0 -> "$name is still owed ${formatDanishAmountFromCents(balanceCents)}. Removing them can leave the group unbalanced."
    else -> "$name has no open balance."
}
