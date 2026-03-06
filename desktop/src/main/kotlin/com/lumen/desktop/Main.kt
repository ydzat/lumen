package com.lumen.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lumen.core.di.companionModule
import com.lumen.core.di.memoryModule
import com.lumen.core.di.platformModule
import com.lumen.core.di.researchModule
import com.lumen.ui.navigation.Tab
import com.lumen.ui.navigation.TabContent
import com.lumen.ui.theme.LumenTheme
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(platformModule, companionModule, memoryModule, researchModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Lumen",
        ) {
            LumenTheme {
                var selectedTab by remember { mutableStateOf(Tab.Home) }
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail {
                        Tab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        TabContent(selectedTab)
                    }
                }
            }
        }
    }
}
