package com.platform.smartwastemanager.core.navigation

object Routes {
    // Auth
    const val LOGIN          = "auth/login"
    const val SIGN_UP        = "auth/signup"
    const val RESET_PASSWORD = "auth/reset-password"

    // Home
    const val HOME             = "home"
    const val MANAGE_SCHEDULES = "home/manage-schedules"

    // Zones
    const val ZONE_LIST       = "home/zones/{scheduleDayId}/{scheduleDayName}"
    const val ZONE_PICKER     = "home/zones/picker/{scheduleDayId}/{scheduleDayName}"
    const val MANAGE_ZONES    = "home/manage-zones"
    const val ZONE_MAP_PICKER = "home/zone-map-picker"
    const val ACTIVE_ROUTE    = "home/route/{zoneName}/{scheduleDayId}"

    fun buildZoneList(scheduleDayId: String, scheduleDayName: String) =
        "home/zones/$scheduleDayId/$scheduleDayName"

    fun buildZonePicker(scheduleDayId: String, scheduleDayName: String) =
        "home/zones/picker/$scheduleDayId/$scheduleDayName"

    fun buildActiveRoute(zoneName: String, scheduleDayId: String) =
        "home/route/${zoneName.replace(" ", "_")}/$scheduleDayId"

    // Report
    const val REPORT          = "report"
    const val SCAN            = "report/scan"
    const val REPORT_FORM     = "report/form"
    const val REPORT_HISTORY  = "report/history"
    const val LOCATION_PICKER = "report/location-picker"

    // Map
    const val MAP = "map"

    // Guides
    const val GUIDES            = "guides"
    const val GUIDE_DETAIL      = "guides/{guideId}"
    const val GUIDE_EDITOR      = "guides/editor"
    const val GUIDE_EDITOR_EDIT = "guides/editor/{guideId}"

    fun buildGuideDetail(guideId: String) = "guides/$guideId"
    fun buildGuideEditor(guideId: String) = "guides/editor/$guideId"

    // Notifications (Phase 6 — driver only)
    const val NOTIFICATIONS = "notifications"

    // Announcements
    const val ANNOUNCEMENTS = "announcements"

    // Collection Points (user-facing zone-based system)
    const val COLLECTION_POINTS        = "collection-points"
    const val COLLECTION_POINT_PICKER  = "collection-points/picker"
    const val MANAGE_COLLECTION_POINTS = "collection-points/manage"
}
