package com.lumen.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Articles("Articles", Icons.AutoMirrored.Filled.MenuBook),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat),
    Settings("Settings", Icons.Default.Settings),
}
