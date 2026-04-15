package com.platform.smartwastemanager.core.navigation

object Routes {

    // ---- Auth routes ----
    const val LOGIN          = "auth/login"
    const val SIGN_UP        = "auth/signup"
    const val RESET_PASSWORD = "auth/reset-password"

    // ---- Main app routes (bottom nav) ----
    const val HOME    = "home"
    const val REPORT  = "report"
    const val MAP     = "map"
    const val GUIDES  = "guides"

    // ---- Nested routes ----
    const val MANAGE_SCHEDULES = "home/manage-schedules"
    const val SCAN             = "report/scan"
    const val REPORT_FORM      = "report/form"
    const val GUIDE_DETAIL     = "guides/{guideId}"
    const val GUIDE_EDITOR     = "guides/editor"
    const val GUIDE_EDITOR_EDIT = "guides/editor/{guideId}"

    // ---- Routes feature — new ----
    // Zone list for a specific schedule day (driver only)
    const val ZONE_LIST = "home/zones/{scheduleDayId}/{scheduleDayName}"

    // Zone map picker — draw a new zone (driver only)
    const val ZONE_MAP_PICKER = "home/zones/picker"

    // Active collection route screen
    // zoneName is passed as a query parameter via SavedStateHandle / navBackStackEntry arguments
    const val ACTIVE_ROUTE = "home/zones/active-route/{zoneName}"

    // ---- Helper functions ----

    fun buildGuideDetail(guideId: String) = "guides/$guideId"

    fun buildGuideEditor(guideId: String) = "guides/editor/$guideId"

    /**
     * Builds the zone list route for a specific schedule day.
     * @param scheduleDayId   Firestore document ID of the CollectionDay.
     * @param scheduleDayName Day label for display, e.g. "Monday".
     */
    fun buildZoneList(scheduleDayId: String, scheduleDayName: String) =
        "home/zones/$scheduleDayId/$scheduleDayName"

    /**
     * Builds the active route screen route, encoding the zone name in the path.
     * Spaces are replaced with underscores for URL safety.
     */
    fun buildActiveRoute(zoneName: String) =
        "home/zones/active-route/${zoneName.replace(" ", "_")}"
}