package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab

/**
 * Signed-in screens from `docs/navigation_model.drawio.xml`. Logging in and registering happen before
 * the shell exists (see `AppRoot` in `MainActivity.kt`).
 */
sealed interface Destination {
    /** S3 View groups (Home). */
    data object Home : Destination

    /** Groups tab with search and filters (S-filter / S-empty). */
    data object Groups : Destination

    data object Activity : Destination

    /** View profile */
    data object Profile : Destination

    /** View group (members, balances, expense details, pay). */
    data class GroupDetail(val groupId: String) : Destination

    /** Create group */
    data object CreateGroup : Destination

    /** Add expense view (camera / manual), optionally pre-filled with a group. */
    data class Scan(val groupId: String? = null) : Destination
}

/** The bottom-nav tab highlighted for a destination, or null when the bar is hidden. */
val Destination.tab: AppTab?
    get() = when (this) {
        Destination.Home -> AppTab.Home
        Destination.Groups, is Destination.GroupDetail -> AppTab.Groups
        Destination.Activity -> AppTab.Activity
        Destination.Profile -> AppTab.Profile
        Destination.CreateGroup, is Destination.Scan -> null
    }

/** One screen on the back stack. It owns the ViewModels created while it is shown. */
class NavEntry internal constructor(val destination: Destination, val key: String) : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * Minimal back stack for the app. Lives in an Activity-scoped ViewModel so it survives rotation;
 * popped entries clear their ViewModels.
 */
class AppNavigator(start: Destination) : ViewModel() {
    private var nextId = 0L
    val backStack: SnapshotStateList<NavEntry> = mutableStateListOf(newEntry(start))

    val current: NavEntry get() = backStack.last()

    fun navigate(destination: Destination) {
        backStack.add(newEntry(destination))
    }

    /** Returns false when already at the root (the system should handle Back). */
    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex).viewModelStore.clear()
        return true
    }

    /** Replaces the current screen, e.g. Create group → the new group. */
    fun replace(destination: Destination) {
        val old = backStack.removeAt(backStack.lastIndex)
        backStack.add(newEntry(destination))
        old.viewModelStore.clear()
    }

    /** Clears the stack, e.g. when logging out. */
    fun resetTo(destination: Destination) {
        val old = backStack.toList()
        backStack.clear()
        backStack.add(newEntry(destination))
        old.forEach { it.viewModelStore.clear() }
    }

    /**
     * Bottom-nav switch. Home stays at the root so Back from any tab returns Home. Scan is not a tab:
     * it opens the full-screen scan flow on top, pre-filled with the group that is open.
     */
    fun selectTab(tab: AppTab) {
        val target = when (tab) {
            AppTab.Home -> Destination.Home
            AppTab.Groups -> Destination.Groups
            AppTab.Activity -> Destination.Activity
            AppTab.Profile -> Destination.Profile
            AppTab.Scan -> return navigate(Destination.Scan((current.destination as? Destination.GroupDetail)?.groupId))
        }
        if (current.destination == target) return
        val home = backStack.firstOrNull { it.destination == Destination.Home } ?: newEntry(Destination.Home)
        val next = if (target == Destination.Home) {
            listOf(home)
        } else {
            listOf(home, backStack.firstOrNull { it.destination == target } ?: newEntry(target))
        }
        val removed = backStack.filter { it !in next }
        backStack.clear()
        backStack.addAll(next)
        removed.forEach { it.viewModelStore.clear() }
    }

    override fun onCleared() {
        backStack.forEach { it.viewModelStore.clear() }
    }

    private fun newEntry(destination: Destination) = NavEntry(destination, "${nextId++}:$destination")
}
