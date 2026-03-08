package com.lumen.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MathWebView(html: String, isDarkTheme: Boolean, modifier: Modifier) {
    val fullHtml = remember(html, isDarkTheme) { wrapWithMathJax(html, isDarkTheme) }
    val density = LocalDensity.current
    var contentHeightDp by remember { mutableIntStateOf(200) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = contentHeightDp.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        // Measure content height after MathJax renders
                        view.evaluateJavascript(
                            "(function() { return document.body.scrollHeight; })()",
                        ) { result ->
                            val px = result?.toIntOrNull() ?: return@evaluateJavascript
                            with(density) {
                                contentHeightDp = (px / density.density).toInt() + 16
                            }
                        }
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://cdn.jsdelivr.net",
                fullHtml,
                "text/html",
                "utf-8",
                null,
            )
        },
    )
}
