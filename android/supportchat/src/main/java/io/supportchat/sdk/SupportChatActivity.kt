package io.supportchat.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.app.NotificationManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject

/** Hosts the shared SupportChat chat widget in a WebView and bridges it to the native SDK. */
class SupportChatActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var topInset: View
    private lateinit var bottomInset: View
    private lateinit var content: FrameLayout
    private var webView: WebView? = null
    private lateinit var spinner: ProgressBar
    private lateinit var errorView: LinearLayout
    private var widgetReady = false
    private var resumed = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    internal val isOnScreen: Boolean get() = resumed
    private var primaryColor = Color.parseColor("#4F46E5")
    private var colorScheme = "auto"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = SupportChat.config ?: run { finish(); return }

        buildLayout()
        applyColors()
        createWebView()
        SupportChat.openChats.add(this)
        load(config)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        send("visibility", JSONObject().put("visible", true))
        getSystemService(NotificationManager::class.java)?.cancel(SupportChat.NOTIFICATION_ID)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        send("visibility", JSONObject().put("visible", false))
    }

    override fun onDestroy() {
        SupportChat.openChats.remove(this)
        webView?.apply {
            removeJavascriptInterface(BRIDGE_NAME)
            (parent as? FrameLayout)?.removeView(this)
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyColors()
    }

    // ---- layout -----------------------------------------------------------

    private fun buildLayout() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        topInset = View(this)
        bottomInset = View(this)
        content = FrameLayout(this)
        spinner = ProgressBar(this).apply { isIndeterminate = true }
        errorView = buildErrorView().apply { visibility = View.GONE }

        content.addView(spinner, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        content.addView(errorView, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        root.addView(topInset, LinearLayout.LayoutParams(MATCH_PARENT, 0))
        root.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(bottomInset, LinearLayout.LayoutParams(MATCH_PARENT, 0))
        setContentView(root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Edge-to-edge: status bar gets the header color, keyboard and nav bar get padding.
            @Suppress("DEPRECATION") window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                topInset.layoutParams = topInset.layoutParams.apply { height = bars.top }
                bottomInset.layoutParams = bottomInset.layoutParams.apply { height = maxOf(bars.bottom, ime.bottom) }
                root.setPadding(bars.left, 0, bars.right, 0)
                root.requestLayout()
                WindowInsets.CONSUMED
            }
        } else {
            root.fitsSystemWindows = true
        }
    }

    private fun buildErrorView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)
        addView(TextView(context).apply {
            text = "Couldn't load the chat."
            gravity = Gravity.CENTER
            textSize = 16f
        })
        addView(Button(context).apply {
            text = "Try again"
            setOnClickListener { SupportChat.config?.let { load(it) } }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = 24 })
        addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(android.R.string.cancel)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    private val isDark: Boolean
        get() = when (colorScheme) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

    private fun applyColors() {
        val background = if (isDark) Color.parseColor("#0F1115") else Color.WHITE
        root.setBackgroundColor(background)
        topInset.setBackgroundColor(primaryColor)
        bottomInset.setBackgroundColor(background)
        webView?.setBackgroundColor(background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = primaryColor
        }
    }

    // ---- web view ---------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView() {
        val web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString SupportChatAndroid/${SupportChat.VERSION}"
        }
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.addJavascriptInterface(Bridge(this), BRIDGE_NAME)
        web.webViewClient = Client()
        web.webChromeClient = Chrome()
        content.addView(web, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        webView = web
        WebView.setWebContentsDebuggingEnabled(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
    }

    private fun load(config: SupportChat.Config) {
        widgetReady = false
        errorView.visibility = View.GONE
        spinner.visibility = View.VISIBLE
        webView?.visibility = View.VISIBLE
        val url = Uri.parse("${config.serverUrl}/widget/frame.html").buildUpon()
            .appendQueryParameter("appId", config.appId)
            .appendQueryParameter("host", "android")
            .appendQueryParameter("v", SupportChat.VERSION)
            .build()
        webView?.loadUrl(url.toString())
    }

    private fun showError() {
        spinner.visibility = View.GONE
        webView?.visibility = View.INVISIBLE
        errorView.visibility = View.VISIBLE
    }

    private fun isWidgetUrl(uri: Uri?): Boolean {
        val base = SupportChat.config?.serverUrl?.let(Uri::parse) ?: return false
        return uri != null && uri.scheme == base.scheme && uri.host == base.host && uri.port == base.port &&
            uri.path.orEmpty().contains("/widget/")
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** `<input type=file>` support: the system document picker, no storage permission needed. */
    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(
            view: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            val intent = Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, params.acceptTypes.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            return try {
                startActivityForResult(Intent.createChooser(intent, null), REQUEST_FILE)
                true
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                false
            }
        }
    }

    @Deprecated("Activity result API needs AndroidX; this keeps the SDK dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_FILE) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val callback = fileCallback ?: return
        fileCallback = null
        if (resultCode != RESULT_OK || data == null) {
            callback.onReceiveValue(null)
            return
        }
        val clip = data.clipData
        val uris = if (clip != null) Array(clip.itemCount) { clip.getItemAt(it).uri } else data.data?.let { arrayOf(it) }
        callback.onReceiveValue(uris)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (isWidgetUrl(request.url)) return false
            openExternal(request.url)
            return true
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) showError()
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (request.isForMainFrame && response.statusCode >= 400) showError()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Recreate instead of letting the host app crash.
            content.removeView(view)
            view.destroy()
            webView = null
            createWebView()
            SupportChat.config?.let { load(it) }
            return true
        }
    }

    // ---- bridge -----------------------------------------------------------

    /** Sends a message to the widget (`window.SupportChat.receive`). Main thread only. */
    internal fun send(type: String, payload: JSONObject = JSONObject()) {
        if (!widgetReady) return
        val message = JSONObject().put("source", "supportchat").put("type", type).put("payload", payload)
        webView?.evaluateJavascript("window.SupportChat && window.SupportChat.receive($message);", null)
    }

    private fun handle(raw: String) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (message.optString("source") != "supportchat") return
        val payload = message.optJSONObject("payload") ?: JSONObject()

        when (message.optString("type")) {
            "ready" -> {
                widgetReady = true
                spinner.visibility = View.GONE
                send("boot", SupportChat.bootPayload())
                if (resumed) send("visibility", JSONObject().put("visible", true))
            }
            "session" -> payload.optString("visitorId").takeIf { it.isNotEmpty() }?.let {
                SupportChat.didStartSession(it, payload.optString("token").ifEmpty { null }, payload.optString("expiresAt").ifEmpty { null })
            }
            "unread" -> SupportChat.setUnread(payload.optInt("count", 0))
            "message" -> payload.optJSONObject("message")?.let {
                SupportChat.notifyMessage(it.optString("body"), it.optString("authorName").ifEmpty { null })
            }
            "config" -> payload.optJSONObject("theme")?.let { theme ->
                runCatching { primaryColor = Color.parseColor(theme.optString("primaryColor", "#4F46E5")) }
                colorScheme = theme.optString("colorScheme", "auto")
                applyColors()
            }
            "link" -> payload.optString("url").takeIf { it.isNotEmpty() }?.let { openExternal(Uri.parse(it)) }
            "close" -> finish()
            "error" -> SupportChat.notifyError(SupportChatError(payload.optString("code", "unknown"), payload.optString("message").ifEmpty { null }))
        }
    }

    /** Called by JavaScript on a background thread; hops to the main thread. */
    private class Bridge(activity: SupportChatActivity) {
        private val ref = java.lang.ref.WeakReference(activity)

        @JavascriptInterface
        fun postMessage(json: String) {
            val activity = ref.get() ?: return
            activity.runOnUiThread { if (!activity.isFinishing) activity.handle(json) }
        }
    }

    private companion object {
        const val BRIDGE_NAME = "SupportChatAndroid"
        const val REQUEST_FILE = 0x50B
    }
}
