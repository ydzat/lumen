package com.lumen.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import java.awt.BorderLayout
import java.util.concurrent.CountDownLatch
import javax.swing.JPanel

private var jfxInitialized = false

private fun ensureJavaFXInitialized() {
    if (jfxInitialized) return
    synchronized(JfxLock) {
        if (jfxInitialized) return
        val latch = CountDownLatch(1)
        try {
            Platform.startup { latch.countDown() }
            latch.await()
        } catch (_: IllegalStateException) {
            // Already initialized
        }
        Platform.setImplicitExit(false)
        jfxInitialized = true
    }
}

private object JfxLock

@Composable
actual fun MathWebView(html: String, isDarkTheme: Boolean, modifier: Modifier) {
    val fullHtml = remember(html, isDarkTheme) { wrapWithMathJax(html, isDarkTheme) }
    var contentHeight by remember { mutableStateOf(400) }

    SwingPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight.dp),
        factory = {
            ensureJavaFXInitialized()
            val panel = JPanel(BorderLayout())
            panel.background = java.awt.Color.WHITE

            val jfxPanel = JFXPanel()
            jfxPanel.isOpaque = true
            jfxPanel.background = java.awt.Color.WHITE
            panel.add(jfxPanel, BorderLayout.CENTER)

            Platform.runLater {
                try {
                    val webView = WebView()
                    val engine = webView.engine

                    engine.loadWorker.stateProperty().addListener { _, _, newState ->
                        if (newState == Worker.State.SUCCEEDED) {
                            scheduleHeightUpdates(engine) { height ->
                                contentHeight = height
                                javax.swing.SwingUtilities.invokeLater {
                                    jfxPanel.revalidate()
                                    jfxPanel.repaint()
                                    panel.revalidate()
                                    panel.repaint()
                                }
                            }
                        }
                    }

                    webView.isContextMenuEnabled = false
                    val scene = Scene(webView)
                    scene.fill = javafx.scene.paint.Color.WHITE
                    jfxPanel.scene = scene
                    engine.loadContent(fullHtml, "text/html")

                    javax.swing.SwingUtilities.invokeLater {
                        jfxPanel.revalidate()
                        jfxPanel.repaint()
                        panel.revalidate()
                        panel.repaint()
                    }
                } catch (e: Exception) {
                    System.err.println("[MathWebView] Failed to create WebView: ${e.message}")
                }
            }

            panel
        },
    )
}

private fun scheduleHeightUpdates(
    engine: javafx.scene.web.WebEngine,
    onHeight: (Int) -> Unit,
) {
    val timer = java.util.Timer(true)
    val delays = longArrayOf(500, 2000, 5000)
    for (delay in delays) {
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                Platform.runLater {
                    try {
                        val result = engine.executeScript("document.body.scrollHeight")
                        val height = when (result) {
                            is Int -> result
                            is Number -> result.toInt()
                            is String -> result.toIntOrNull() ?: return@runLater
                            else -> return@runLater
                        }
                        if (height > 50) {
                            onHeight(height + 24)
                        }
                    } catch (_: Exception) {
                        // Script execution may fail if page not ready
                    }
                }
            }
        }, delay)
    }
}
