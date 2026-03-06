package com.lumen.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lumen.core.di.companionModule
import com.lumen.core.di.platformModule
import com.lumen.core.di.researchModule
import com.lumen.getPlatformName
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(platformModule, companionModule, researchModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Lumen"
        ) {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Lumen on ${getPlatformName()}")
                    }
                }
            }
        }
    }
}
