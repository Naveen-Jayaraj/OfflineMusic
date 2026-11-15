package com.example.offlinemusic.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.example.offlinemusic.downloader.engine.LucidaDownloader
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONTokener

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LucidaVerificationDialog(query: String, onDismiss: () -> Unit, onVerified: (String) -> Unit) {
    val onPageReady = rememberUpdatedState(onVerified)
    val onClose = rememberUpdatedState(onDismiss)
    val webViewAvailable = remember { WebView.getCurrentWebViewPackage() != null }
    val searchUrl = remember(query) { LucidaDownloader.searchUrl(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(horizontal = 10.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Verify Lucida") },
        text = {
            if (webViewAvailable) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Complete the browser check to resume your search.")
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = LucidaVerificationClient(
                                    searchUrl = searchUrl,
                                    onVerified = { onPageReady.value(it) },
                                    onClose = { onClose.value() }
                                )
                                loadUrl(searchUrl.toString())
                            }
                        },
                        onReset = null,
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.destroy()
                        }
                    )
                }
            } else {
                Text("Android System WebView is required to complete this check.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private class LucidaVerificationClient(
    private val searchUrl: HttpUrl,
    private val onVerified: (String) -> Unit,
    private val onClose: () -> Unit
) : WebViewClient() {
    private var pageFailed = false
    private var deliveredResults = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request.isForMainFrame && !isLucidaOrigin(request.url.toString().toHttpUrlOrNull())

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        pageFailed = false
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (request.isForMainFrame) pageFailed = true
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) pageFailed = true
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onClose()
        return true
    }

    override fun onPageFinished(view: WebView, url: String) {
        val currentUrl = view.url ?: return
        val loadedUrl = currentUrl.toHttpUrlOrNull() ?: return
        if (pageFailed || deliveredResults || !isLucidaOrigin(loadedUrl)) return
        if (loadedUrl.encodedPath.trimEnd('/') != searchUrl.encodedPath ||
            loadedUrl.queryParameter("service") != searchUrl.queryParameter("service") ||
            loadedUrl.queryParameter("query") != searchUrl.queryParameter("query")
        ) return

        // Read the search page already loaded by the verified browser.
        val script = "Array.from(document.scripts, script => script.textContent).join('\\n')"
        view.evaluateJavascript(script) { value ->
            if (deliveredResults || !view.isAttachedToWindow || currentUrl != view.url) return@evaluateJavascript
            val scripts = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull() ?: return@evaluateJavascript
            deliveredResults = true
            onVerified(scripts)
        }
    }

    private fun isLucidaOrigin(url: HttpUrl?): Boolean =
        url != null && url.isHttps && url.host == searchUrl.host && url.port == searchUrl.port
}
