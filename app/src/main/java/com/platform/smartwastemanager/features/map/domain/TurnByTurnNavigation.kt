package com.platform.smartwastemanager.features.map.domain

data class TurnByTurnNavigation(
    val steps: List<String> = emptyList(),
    val distanceMeters: Int? = null,
    val durationSeconds: Int? = null
)
