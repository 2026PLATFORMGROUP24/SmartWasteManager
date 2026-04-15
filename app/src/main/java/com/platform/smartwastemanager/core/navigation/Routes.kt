package com.platform.smartwastemanager.core.navigation

object Routes {

    // ---- Auth ----
    const val LOGIN          = "auth/login"
    const val SIGN_UP        = "auth/signup"
    const val RESET_PASSWORD = "auth/reset-password"

    // ---- Bottom nav ----
    const val HOME    = "home"
    const val REPORT  = "report"
    const val MAP     = "map"
    const val GUIDES  = "guides"

    // ---- Nested routes ----
    const val MANAGE_SCHEDULES  = "home/manage-schedules"
    const val SCAN              = "report/scan"
    const val REPORT_FORM       = "report/form"
    const val GUIDE_DETAIL      = "guides/{guideId}"
    const val GUIDE_EDITOR      = "guides/editor"
    const val GUIDE_EDITOR_EDIT = "guides/editor/{guideId}"

    // ---- Location picker (new — for waste report form) ----
    const val LOCATION_PICKER   = "report/location-picker"

    // ---- Routes feature ----
    const val ZONE_LIST       = "home/zones/{scheduleDayId}/{scheduleDayName}"
    const val ZONE_MAP_PICKER = "home/zones/picker"
    const val ACTIVE_ROUTE    = "home/zones/active-route/{zoneName}"

    // ---- Helpers ----
    fun buildGuideDetail(guideId: String)  = "guides/$guideId"
    fun buildGuideEditor(guideId: String)  = "guides/editor/$guideId"
    fun buildZoneList(scheduleDayId: String, scheduleDayName: String) =
        "home/zones/$scheduleDayId/$scheduleDayName"
    fun buildActiveRoute(zoneName: String) =
        "home/zones/active-route/${zoneName.replace(" ", "_")}"
}