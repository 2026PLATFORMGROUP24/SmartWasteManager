package com.platform.smartwastemanager.core.navigation

/**
 * A single place for all navigation route strings.
 *
 * Using an object with constants prevents typos — instead of writing the
 * string "auth/login" in multiple places, you write Routes.LOGIN everywhere.
 * If you ever need to rename a route, you only change it here.
 */
object Routes {

    // ---- Auth routes ----
    const val LOGIN = "auth/login"
    const val SIGN_UP = "auth/signup"
    const val RESET_PASSWORD = "auth/reset-password"

    // ---- Main app routes (bottom nav) ----
    const val HOME = "home"
    const val REPORT = "report"
    const val MAP = "map"
    const val GUIDES = "guides"

    // ---- Nested routes (used in later phases) ----
    const val MANAGE_SCHEDULES = "home/manage-schedules"
    const val SCAN = "report/scan"
    const val REPORT_FORM = "report/form"
    const val GUIDE_DETAIL = "guides/{guideId}"           // use buildGuideDetail()
    const val GUIDE_EDITOR = "guides/editor"
    const val GUIDE_EDITOR_EDIT = "guides/editor/{guideId}" // use buildGuideEditor()

    // ---- Helper functions to build routes with arguments ----

    /** Builds the route to a specific guide's detail screen. */
    fun buildGuideDetail(guideId: String) = "guides/$guideId"

    /** Builds the route to edit a specific guide. */
    fun buildGuideEditor(guideId: String) = "guides/editor/$guideId"
}