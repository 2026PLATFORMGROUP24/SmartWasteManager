package com.platform.smartwastemanager.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Report
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
            BottomNavItem(Routes.ANNOUNCEMENTS, "Alerts", Icons.Default.Campaign),
            BottomNavItem(Routes.REPORT,        "Report",        Icons.Default.Report),
            BottomNavItem(Routes.MAP,           "Map",           Icons.Default.LocationOn),
            BottomNavItem(Routes.GUIDES,        "Guides",        Icons.Default.MenuBook)
        )
    }
}