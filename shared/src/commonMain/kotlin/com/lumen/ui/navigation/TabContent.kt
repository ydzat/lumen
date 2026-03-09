package com.lumen.ui.navigation

import androidx.compose.runtime.Composable
import com.lumen.ui.screen.ArticlesScreen
import com.lumen.ui.screen.ChatScreen
import com.lumen.ui.screen.HomeScreen
import com.lumen.ui.screen.SettingsScreen

@Composable
fun TabContent(tab: Tab) {
    when (tab) {
        Tab.Home -> HomeScreen()
        Tab.Articles -> ArticlesScreen()
        Tab.Chat -> ChatScreen()
        Tab.Settings -> SettingsScreen()
    }
}
