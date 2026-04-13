package com.platform.smartwastemanager.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Defines the 4 tabs shown in the bottom navigation bar.
 *
 * Each item has:
 * @property route  The navigation route to go to when this tab is tapped.
 * @property label  The text label shown under the icon.
 * @property icon   The Material icon to display.
 */
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(
        route = Routes.HOME,
        label = "Home",
        icon = Icons.Default.Home
    )

    object Report : BottomNavItem(
        route = Routes.REPORT,
        label = "Report",
        icon = Icons.Default.PhotoCamera
    )

    object Map : BottomNavItem(
        route = Routes.MAP,
        label = "Map",
        icon = Icons.Default.Map
    )

    object Guides : BottomNavItem(
        route = Routes.GUIDES,
        label = "Guides",
        icon = Icons.Default.MenuBook
    )

    companion object {
        /** All bottom nav items in display order. */
        val all = listOf(Home, Report, Map, Guides)
    }
}