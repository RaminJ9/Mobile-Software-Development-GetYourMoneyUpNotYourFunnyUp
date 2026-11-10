package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.example.getyourmoneyupnotyourfunnyup.model.DateLabels
import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.lastActivityAt
import com.example.getyourmoneyupnotyourfunnyup.model.myNet
import com.example.getyourmoneyupnotyourfunnyup.model.unsyncedExpenseCount

/** One group in a list (Home "Your groups" and the Groups tab). */
@Immutable
data class GroupRowUi(
    val groupId: String,
    val name: String,
    val initial: String,
    val paletteIndex: Int,
    val memberCount: Int,
    /** "Active today", "Active Sun 1 Nov" */
    val activeLabel: String,
    val isFavourite: Boolean,
    /** The user's balance in the group (negative = owes). */
    val netCents: Long,
    /** Expenses saved on the phone but not yet synced. */
    val unsyncedCount: Int,
    val lastActiveAt: Long,
)

fun LedgerSnapshot.groupRow(group: Group, now: Long): GroupRowUi {
    val last = lastActivityAt(group.id)
    val day = DateLabels.relativeDay(last, now)
    return GroupRowUi(
        groupId = group.id,
        name = group.name,
        initial = group.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        paletteIndex = group.colorIndex,
        memberCount = group.members.size,
        activeLabel = "Active " + if (day == "Today" || day == "Yesterday") day.lowercase() else day,
        isFavourite = group.id in favouriteGroupIds,
        netCents = myNet(group.id),
        unsyncedCount = unsyncedExpenseCount(group.id),
        lastActiveAt = last,
    )
}
