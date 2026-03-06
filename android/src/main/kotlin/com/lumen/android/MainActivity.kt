package com.lumen.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lumen.ui.navigation.Tab
import com.lumen.ui.screen.ArticlesScreen
import com.lumen.ui.screen.ChatScreen
import com.lumen.ui.screen.HomeScreen
import com.lumen.ui.screen.SettingsScreen
import com.lumen.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumenTheme {
                var selectedTab by remember { mutableStateOf(Tab.Home) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        when (selectedTab) {
                            Tab.Home -> HomeScreen()
                            Tab.Articles -> ArticlesScreen()
                            Tab.Chat -> ChatScreen()
                            Tab.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
