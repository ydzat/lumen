package com.lumen.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific WebView that renders HTML with MathJax for math formula support.
 * Used when section HTML contains <math> tags (e.g. arXiv papers).
 *
 * @param html Raw section HTML containing <math> MathML elements
 * @param isDarkTheme Whether to use dark theme styling
 * @param modifier Layout modifier
 */
@Composable
expect fun MathWebView(html: String, isDarkTheme: Boolean, modifier: Modifier = Modifier)

/**
 * Checks whether an HTML string contains MathML elements that require
 * WebView + MathJax rendering (as opposed to the simpler Compose renderer).
 */
fun htmlContainsMath(html: String): Boolean {
    return html.contains("<math", ignoreCase = true)
}

/**
 * Wraps section HTML with MathJax CDN and minimal styling to produce
 * a complete HTML document for WebView rendering.
 */
internal fun wrapWithMathJax(html: String, isDarkTheme: Boolean): String {
    val textColor = if (isDarkTheme) "#ECECEC" else "#1C1B1F"
    val bgColor = if (isDarkTheme) "#1C1B1F" else "#FFFFFF"
    val linkColor = if (isDarkTheme) "#A8C7FA" else "#1A56DB"
    val borderColor = if (isDarkTheme) "#444444" else "#CCCCCC"
    val headerBg = if (isDarkTheme) "#2A2A2A" else "#F5F5F5"
    val codeBg = if (isDarkTheme) "#2A2A2A" else "#F5F5F5"
    val quoteBorder = if (isDarkTheme) "#555555" else "#AAAAAA"

    return """<!DOCTYPE html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Sans:wght@400;600;700&display=swap">
<style>
body {
  margin: 0; padding: 4px 0;
  font-family: 'Noto Sans', sans-serif;
  font-size: 15px; line-height: 1.6;
  color: $textColor; background: $bgColor;
  word-wrap: break-word; overflow-wrap: break-word;
  -webkit-text-fill-color: $textColor;
  -webkit-text-stroke: 1px $textColor;
  paint-order: stroke fill;
  -webkit-font-smoothing: antialiased;
}
* {
  -webkit-text-stroke: inherit;
  -webkit-text-fill-color: inherit;
}
h1,h2,h3,h4,h5,h6 { margin: 12px 0 4px; }
h1 { font-size: 1.4em; } h2 { font-size: 1.25em; } h3 { font-size: 1.1em; }
p { margin: 4px 0 8px; }
img { max-width: 100%; height: auto; border-radius: 6px; }
table { border-collapse: collapse; margin: 8px 0; width: 100%; overflow-x: auto; display: block; }
th, td { border: 1px solid $borderColor; padding: 6px 8px; text-align: left; }
th { background: $headerBg; font-weight: 600; }
a { color: $linkColor; text-decoration: none; }
a:hover { text-decoration: underline; }
blockquote { border-left: 3px solid $quoteBorder; margin: 8px 0; padding: 4px 12px; font-style: italic; }
pre, code { background: $codeBg; border-radius: 3px; font-family: 'SF Mono', 'Cascadia Code', monospace; font-size: 0.9em; }
pre { padding: 8px 12px; overflow-x: auto; }
code { padding: 1px 4px; }
figure { margin: 8px 0; text-align: center; }
figcaption { font-size: 0.85em; color: ${if (isDarkTheme) "#AAAAAA" else "#666666"}; margin-top: 4px; }
.ltx_eqn_table, table.ltx_equation { border: none; width: auto; display: table; margin: 8px auto; }
.ltx_eqn_table td, table.ltx_equation td { border: none; padding: 2px 4px; }
</style>
<script>
MathJax = {
  options: { enableMenu: false },
  chtml: { fontURL: 'https://cdn.jsdelivr.net/npm/mathjax@3/es5/output/chtml/fonts/woff-v2' },
  startup: {
    pageReady: function() {
      return MathJax.startup.defaultPageReady().then(function() {
        if (window.javaObj) {
          window.javaObj.updateHeight(document.body.scrollHeight);
        }
      });
    }
  }
};
</script>
<script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js" async></script>
</head>
<body>
$html
</body></html>"""
}
