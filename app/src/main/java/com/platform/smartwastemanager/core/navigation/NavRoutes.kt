package com.platform.smartwastemanager.core.navigation

/**
 * All navigation route strings used in the NavHost.
 * Using an object with constants prevents typos when navigating between screens.
 */
object NavRoutes {
    // ---- Auth screens ----
    const val LOGIN = "auth/login"
    const val SIGN_UP = "auth/signup"
    const val RESET_PASSWORD = "auth/reset-password"

    // ---- Main app screens (bottom nav) ----
    const val HOME = "home"
    const val MANAGE_SCHEDULES = "home/manage-schedules"

    const val REPORT = "report"
    const val REPORT_SCAN = "report/scan"
    const val REPORT_FORM = "report/form"

    const val MAP = "map"

    const val GUIDES = "guides"
    const val GUIDE_DETAIL = "guides/{guideId}"       // navigate with: "guides/$guideId"
    const val GUIDE_EDITOR_CREATE = "guides/editor"
    const val GUIDE_EDITOR_EDIT = "guides/editor/{guideId}" // navigate with: "guides/editor/$guideId"

    /** Helper to build the guide detail route with a real ID */
    fun guideDetail(guideId: String) = "guides/$guideId"

    /** Helper to build the guide editor route with a real ID */
    fun guideEditorEdit(guideId: String) = "guides/editor/$guideId"
}