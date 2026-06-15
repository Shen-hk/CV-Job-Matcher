package com.example.cv_jobmatcher.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.cv_jobmatcher.domain.model.ResumeData
import com.example.cv_jobmatcher.util.HtmlPdfExporter

/**
 * Real-time HTML resume preview using WebView.
 *
 * Renders [resumeData] as styled HTML using [HtmlPdfExporter.buildHtml],
 * updating automatically whenever data or template selection changes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ResumePreviewWebView(
    resumeData: ResumeData,
    useVibeTemplate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Build HTML whenever inputs change
    val html = remember(resumeData, useVibeTemplate) {
        HtmlPdfExporter.buildHtml(
            context = context,
            resumeData = resumeData,
            config = HtmlPdfExporter.HtmlConfig(useVibeTemplate = useVibeTemplate)
        )
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setBackgroundColor(android.graphics.Color.WHITE)
            webViewClient = WebViewClient()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    // Reload HTML when content changes
    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    )
}