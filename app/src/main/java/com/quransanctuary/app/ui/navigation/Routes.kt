package com.quransanctuary.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Home : Route
    @Serializable
    data class Reader(val surah: Int) : Route
    @Serializable
    data object Player : Route
    @Serializable
    data object Library : Route
    @Serializable
    data object Settings : Route
    @Serializable
    data class ReciterDetail(val id: String) : Route
}
