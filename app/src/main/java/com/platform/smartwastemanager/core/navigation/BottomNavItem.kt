package com.platform.smartwastemanager.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom navigation items — shown to all users.
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    companion object {
        val all = listOf(
            BottomNavItem(Routes.HOME,          "Home",          Icons.Default.Home),
            BottomNavItem(Routes.ANNOUNCEMENTS, "Announce", Icons.Default.Campaign),
            BottomNavItem(Routes.REPORT,        "Scan",          Icons.Default.DocumentScanner),
            BottomNavItem(Routes.MAP,           "Map",           Icons.Default.LocationOn),
            BottomNavItem(Routes.GUIDES,        "Guides",        Icons.Default.MenuBook)
        )
    }
}