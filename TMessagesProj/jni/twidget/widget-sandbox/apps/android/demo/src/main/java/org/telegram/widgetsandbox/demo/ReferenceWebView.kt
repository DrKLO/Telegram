// Reference mode (dev tooling only, never a product path — see CLAUDE.md):
// the same corpus file rendered natively by the system WebView, without the
// QuickJS sandbox, validator or renderer protocol, so the sandboxed result and
// its speed can be compared against what the engine does on its own. Mirrors
// the web prototype's `?reference=1` (vite.config.ts `/reference/` route +
// src/main.ts `updateReference`) and `ReferenceWebView.swift` in the iOS/macOS
// demo: the document gets a doctype and is served with `connect-src 'none'`
// from a private origin whose interceptor refuses every other URL. Lives in
// the demo, not in widget-sandbox; the corpus browser hides it for `attacks/`.

package org.telegram.widgetsandbox.demo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.telegram.widgetsandbox.RendererWebView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled")
class ReferenceWebView(context: Context, html: String, theme: String) {
    companion object {
        const val ORIGIN_SUFFIX = ".tgref.invalid"

        /** Same as the web dev server's REFERENCE_CSP. */
        const val CSP = "default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'unsafe-inline'; " +
            "img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; frame-src 'none'; " +
            "child-src 'none'; worker-src 'none'; form-action 'none'; base-uri 'none'"

        /**
         * = the web dev server's reference mode: a doctype (no library is bundled since profile 1, so a CDN script fails here as in
         * the sandbox), and right after it what the renderer adds to every widget with the reference theme (`Corpus/reference.css`,
         * written by `scripts/sync-demo-corpus.ts`): the profile's UA layer and the default palettes and reference text tokens.
         */
        fun rewrite(html: String, referenceCss: String = ""): String {
            val doc = if (Regex("""^\s*<!doctype""", RegexOption.IGNORE_CASE).containsMatchIn(html)) html else "<!DOCTYPE html>\n$html"
            if (referenceCss.isEmpty()) return doc
            val doctype = Regex("""^\s*<!doctype[^>]*>""", RegexOption.IGNORE_CASE).find(doc) ?: return doc
            val end = doctype.range.last + 1
            return doc.substring(0, end) + "<style data-tw-ua>" + referenceCss + "</style>" + doc.substring(end)
        }

        @Volatile private var cachedCss: String? = null

        /** The demo's copy of the reference stylesheet; empty when the corpus sync has not written it. */
        fun referenceCss(context: Context): String = cachedCss
            ?: runCatching { context.assets.open("Corpus/reference.css").use { it.readBytes().toString(Charsets.UTF_8) } }.getOrDefault("").also { cachedCss = it }

        /** Reports the page's own timings, its height (like the web UI's ResizeObserver + poll) and uncaught errors. */
        const val PROBE_SCRIPT = """
            (function () {
              var post = function (m) { try { window.ref.post(JSON.stringify(m)); } catch (e) {} };
              var last = -1;
              var size = function () {
                var h = Math.min(Math.max(Math.ceil(document.documentElement.getBoundingClientRect().height), 50), 600);
                if (h !== last) { last = h; post({ type: 'size', height: h }); }
              };
              window.addEventListener('error', function (e) { post({ type: 'error', message: String(e.message || e.error || 'error').slice(0, 300) }); });
              window.addEventListener('unhandledrejection', function (e) { post({ type: 'error', message: 'Unhandled rejection: ' + String(e.reason).slice(0, 280) }); });
              document.addEventListener('DOMContentLoaded', function () {
                size();
                try { new ResizeObserver(size).observe(document.documentElement); new ResizeObserver(size).observe(document.body); } catch (e) {}
                setInterval(size, 1000);
              });
              window.addEventListener('load', function () {
                var nav = performance.getEntriesByType('navigation')[0];
                var t0 = nav ? 0 : performance.timing.navigationStart;
                var dcl = nav ? nav.domContentLoadedEventStart : performance.timing.domContentLoadedEventStart - t0;
                post({ type: 'timing', domContentLoaded: dcl, load: nav ? nav.loadEventStart : performance.timing.loadEventStart - t0, now: performance.now() });
                size();
              });
            })();
        """
    }

    val webView: WebView
    /** `size` (height), `timing` (domContentLoaded, load, ms since navigation start), `error` (message). Main thread. */
    var onMessage: ((JSONObject) -> Unit)? = null

    val documentUrl: String = "https://w-${UUID.randomUUID().toString().lowercase()}$ORIGIN_SUFFIX/"
    private val main = Handler(Looper.getMainLooper())
    private val html: ByteArray
    @Volatile private var destroyed = false
    @Volatile private var documentLoaded = false
    private var started = false

    init {
        var doc = rewrite(html, referenceCss(context) + demoReferenceCss())
        // The probe runs before the page's own scripts: as a document-start script where
        // the WebView supports it, otherwise inlined at the top (CSP allows 'unsafe-inline' here).
        val startScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (!startScript) doc = doc.replaceFirst(Regex("""(?i)(<!doctype[^>]*>\s*)"""), "$1<script>$PROBE_SCRIPT</script>")
        this.html = doc.toByteArray(Charsets.UTF_8)

        // The same themed context as the sandbox renderer, so `prefers-color-scheme` follows the Dark toggle.
        webView = WebView(RendererWebView.themedContext(context, theme))
        val s = webView.settings
        s.javaScriptEnabled = true
        // Not a product path, but still no network: the belt the sandbox renderer wears too.
        s.blockNetworkLoads = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        // The reference keeps what the engine gives a page by default (the sandbox shim emulates localStorage).
        s.domStorageEnabled = true
        s.databaseEnabled = false
        s.setGeolocationEnabled(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        s.mediaPlaybackRequiresUserGesture = false
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.useWideViewPort = false
        s.loadWithOverviewMode = false
        s.setNeedInitialFocus(false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) WebSettingsCompat.setSafeBrowsingEnabled(s, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.addJavascriptInterface(Bridge(), "ref")
        if (startScript) WebViewCompat.addDocumentStartJavaScript(webView, PROBE_SCRIPT, setOf("*"))
        webView.setDownloadListener { _, _, _, _, _ -> }
    }

    /** Starts the load once the view is handed to the view hierarchy (the AndroidView factory). Main thread. */
    fun start() {
        if (started || destroyed) return
        started = true
        webView.loadUrl(documentUrl)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        webView.removeJavascriptInterface("ref")
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    inner class Bridge {
        @JavascriptInterface
        fun post(json: String) {
            val m = try { JSONObject(json) } catch (e: Exception) { return }
            main.post { if (!destroyed) onMessage?.invoke(m) }
        }
    }

    private fun error(message: String) {
        main.post { if (!destroyed) onMessage?.invoke(JSONObject().put("type", "error").put("message", message)) }
    }

    /** Serves the rewritten document; refuses everything else. */
    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            val url = request.url
            val headers = mapOf("Content-Security-Policy" to CSP, "Cache-Control" to "no-store", "Referrer-Policy" to "no-referrer")
            if (url.toString() == documentUrl && request.isForMainFrame) {
                return WebResourceResponse("text/html", "utf-8", 200, "OK", headers, ByteArrayInputStream(html))
            }
            return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", mapOf("Content-Length" to "0"), ByteArrayInputStream(ByteArray(0)))
        }

        // Only the document itself navigates.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!documentLoaded && request.isForMainFrame && request.url.toString() == documentUrl) return false
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (url == documentUrl) documentLoaded = true
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()
        override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) = dontResend.sendToTarget()
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            error("web content process terminated (crash=${detail.didCrash()})")
            (view.parent as? ViewGroup)?.removeView(view)
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean = false
        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean { result.cancel(); return true }
        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean { result.cancel(); return true }
        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean { result.cancel(); return true }
        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean { result.cancel(); return true }
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) = callback.invoke(origin, false, false)
        override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<android.net.Uri>>, fileChooserParams: FileChooserParams): Boolean = false
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) error("console: ${consoleMessage.message().take(300)}")
            return true
        }
    }
}

/** Observable façade for Compose, like WidgetModel for the sandbox (= ReferenceModel.swift). */
class ReferenceModel(context: Context, html: String, theme: String) {
    val view = ReferenceWebView(context, html, theme)
    var height by mutableStateOf(24.0)
        private set
    var loadMs by mutableStateOf<Double?>(null)
        private set
    var domContentLoadedMs by mutableStateOf<Double?>(null)
        private set
    var errors by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        view.onMessage = { m ->
            when (m.optString("type")) {
                "size" -> height = m.optDouble("height", 24.0)
                "timing" -> { loadMs = m.optDouble("load", 0.0); domContentLoadedMs = m.optDouble("domContentLoaded", 0.0) }
                "error" -> if (errors.size < 50) errors = errors + m.optString("message", "error")
            }
        }
    }

    val loaded: Boolean get() = loadMs != null
    fun destroy() = view.destroy()
}

/** The natively rendered twin of ChatBubble (dev tooling): the system WebView runs the widget's own scripts here. */
@Composable
fun ReferenceBubble(model: ReferenceModel, widthDp: Int = 360) {
    Column {
        Text("reference — native WebView, no sandbox, dev tooling only", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        Column(modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
            // key: a new model (theme change, Restart) must get a new AndroidView, or the factory is never re-run.
            key(model) {
                AndroidView(
                    factory = { model.view.start(); model.view.webView },
                    modifier = Modifier.width(widthDp.dp).height(maxOf(model.height, 24.0).dp),
                )
            }
        }
        val timing = model.loadMs?.let { String.format("load event at %.0f ms (DOMContentLoaded %.0f ms)", it, model.domContentLoadedMs ?: 0.0) } ?: "… loading"
        Text(timing, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        for (e in model.errors.takeLast(5)) Text(e, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Red, maxLines = 2, modifier = Modifier.padding(start = 8.dp))
    }
}
