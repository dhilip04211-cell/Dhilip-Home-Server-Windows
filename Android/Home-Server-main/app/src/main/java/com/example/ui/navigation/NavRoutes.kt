package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Files : Screen("files")
    data object Media : Screen("media")
    data object MediaDetails : Screen("media_details/{mediaId}") {
        fun createRoute(mediaId: String) = "media_details/$mediaId"
    }
    data object Admin : Screen("admin")
    data object Settings : Screen("settings")
    data object Player : Screen("player?url={url}&title={title}") {
        fun createRoute(url: String, title: String = "Media"): String {
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            return "player?url=$encodedUrl&title=$encodedTitle"
        }
    }
}
