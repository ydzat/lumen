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
import com.lumen.core.config.ConfigStore
import com.lumen.ui.navigation.Tab
import com.lumen.ui.navigation.TabContent
import com.lumen.ui.screen.OnboardingScreen
import com.lumen.ui.theme.LumenTheme
import com.lumen.ui.theme.ThemeState
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val configStore: ConfigStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = configStore.load()
        ThemeState.mode = config.preferences.theme
        setContent {
            LumenTheme {
                var showOnboarding by remember { mutableStateOf(!config.preferences.hasCompletedOnboarding) }

                if (showOnboarding) {
                    OnboardingScreen(onComplete = { showOnboarding = false })
                } else {
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
                            TabContent(selectedTab)
                        }
                    }
                }
            }
        }
    }
}
