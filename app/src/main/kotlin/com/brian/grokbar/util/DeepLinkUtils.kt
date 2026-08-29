package com.brian.grokbar.util

import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController

object DeepLinkUtils {

    fun isDeepLink(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return data.scheme == "grokbar" && data.host == "open"
    }

    fun routeFromDeepLink(uri: Uri): String? {
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        return when {
            segments.isEmpty() || segments[0].equals("home", ignoreCase = true) -> "home"
            segments[0].equals("settings", ignoreCase = true) -> "settings"
            else -> null
        }
    }

    fun handleDeepLink(intent: Intent?, navController: NavController): Boolean {
        val data = intent?.data ?: return false
        if (!isDeepLink(intent)) return false
        val route = routeFromDeepLink(data) ?: return false
        navController.navigate(route)
        return true
    }
}
