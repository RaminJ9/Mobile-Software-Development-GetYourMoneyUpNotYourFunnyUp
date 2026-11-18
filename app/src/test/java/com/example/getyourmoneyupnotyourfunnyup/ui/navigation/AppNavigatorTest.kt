package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigatorTest {

    private fun AppNavigator.destinations() = backStack.map { it.destination }

    @Test
    fun tabs_keepHomeAtTheRoot_soBackAlwaysReturnsHome() {
        val navigator = AppNavigator(Destination.Home)
        navigator.navigate(Destination.GroupDetail("ski"))
        navigator.selectTab(AppTab.Activity)
        assertEquals(listOf(Destination.Home, Destination.Activity), navigator.destinations())

        navigator.selectTab(AppTab.Home)
        assertEquals(listOf(Destination.Home), navigator.destinations())
        assertFalse(navigator.pop())
    }

    @Test
    fun scan_opensOnTop_withTheOpenGroup() {
        val navigator = AppNavigator(Destination.Home)
        navigator.selectTab(AppTab.Scan)
        assertEquals(Destination.Scan(null), navigator.current.destination)
        assertNull(navigator.current.destination.tab)

        navigator.pop()
        navigator.navigate(Destination.GroupDetail("ski"))
        navigator.selectTab(AppTab.Scan)
        assertEquals(listOf(Destination.Home, Destination.GroupDetail("ski"), Destination.Scan("ski")), navigator.destinations())
        assertEquals(AppTab.Groups, Destination.GroupDetail("ski").tab)
    }
}
