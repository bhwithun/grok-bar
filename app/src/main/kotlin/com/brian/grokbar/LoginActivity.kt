package com.brian.grokbar

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.brian.grokbar.data.CreditsProto
import com.brian.grokbar.data.GrokUsageApi
import com.brian.grokbar.data.UsageRepository
import com.brian.grokbar.data.UsageSnapshot
import com.brian.grokbar.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var doneView: TextView
    private val mainHandler = Handler(Looper.getMainLooper())

    private var fetchStarted = false
    private var finished = false
    private var redirectedToGrok = false
    private var capturing = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        statusView = findViewById(R.id.login_status)
        doneView = findViewById(R.id.login_done)
        findViewById<TextView>(R.id.login_cancel).setOnClickListener { finish() }
        doneView.setOnClickListener { captureNow(fromUser = true) }

        CookieManager.getInstance().setAcceptCookie(true)
        webView = findViewById(R.id.login_webview)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = CHROME_UA
        }
        webView.addJavascriptInterface(Bridge(), "GrokBar")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty()
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView, url: String) {
                onLoaded(url)
            }
        }
        webView.loadUrl(GrokUsageApi.LOGIN_PAGE)
        mainHandler.postDelayed(pollCookies, 1500)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private val pollCookies = object : Runnable {
        override fun run() {
            if (finished) return
            val url = webView.url.orEmpty()
            onLoaded(url)
            mainHandler.postDelayed(this, 1500)
        }
    }

    private fun onLoaded(url: String) {
        if (finished) return
        val host = Uri.parse(url).host.orEmpty()
        val path = Uri.parse(url).path.orEmpty()

        when {
            host.endsWith("x.com") || host.endsWith("twitter.com") -> {
                setStatus("Finish signing in with X, then authorize xAI if asked.")
            }
            host.endsWith("accounts.x.ai") -> {
                val signingIn = path.contains("sign-in") || path.contains("login") ||
                    path.contains("authorize") || path.contains("oauth")
                if (signingIn) {
                    setStatus("Sign in with X. After it links, we'll open Grok.")
                } else {
                    setStatus("Account linked. Opening Grok…")
                    openGrokIfNeeded()
                }
            }
            host.endsWith("grok.com") || host.endsWith("grok.x.ai") -> {
                setStatus("Grok is open. Tap Done if this page doesn't close on its own.")
                if (hasGrokCookie()) captureNow(fromUser = false)
            }
        }
    }

    private fun openGrokIfNeeded() {
        if (redirectedToGrok || finished) return
        redirectedToGrok = true
        webView.loadUrl(GrokUsageApi.USAGE_PAGE)
    }

    private fun captureNow(fromUser: Boolean) {
        if (finished || capturing) return
        val cookie = sessionCookie()
        if (!fromUser && !hasGrokCookie()) return
        if (!fromUser && fetchStarted) return

        capturing = true
        fetchStarted = true
        setStatus("Reading your weekly usage…")

        lifecycleScope.launch {
            if (cookie.isNotBlank()) {
                UsageRepository.get(this@LoginActivity).saveCookie(cookie)
            }
        }

        val onGrok = webView.url.orEmpty().contains("grok.com")
        if (!onGrok) {
            openGrokIfNeeded()
            mainHandler.postDelayed({
                capturing = false
                fetchStarted = false
                if (!finished) captureNow(fromUser = true)
            }, 2500)
            return
        }

        CookieManager.getInstance().flush()
        webView.evaluateJavascript(FETCH_SCRIPT, null)
        mainHandler.postDelayed({
            if (!finished) fallbackRefresh()
        }, 3500)
    }

    private fun hasGrokCookie(): Boolean {
        val cookie = CookieManager.getInstance().getCookie("https://grok.com").orEmpty()
        return cookie.length > 20
    }

    private fun sessionCookie(): String {
        val chunks = listOf(
            CookieManager.getInstance().getCookie("https://grok.com"),
            CookieManager.getInstance().getCookie("https://accounts.x.ai")
        ).filterNotNull().filter { it.isNotBlank() }
        val unique = linkedMapOf<String, String>()
        chunks.forEach { chunk ->
            chunk.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { part ->
                val name = part.substringBefore("=")
                unique[name] = part
            }
        }
        return unique.values.joinToString("; ")
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusView.text = text }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onCredits(b64: String) {
            val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return
            val decoded = CreditsProto.parseGrpcWeb(bytes) ?: return
            lifecycleScope.launch {
                persist(
                    UsageSnapshot(
                        usedPercent = decoded.usedPercent,
                        resetAtEpochMs = decoded.resetAtEpochMs,
                        planName = "SuperGrok",
                        products = decoded.products,
                        fetchedAtEpochMs = System.currentTimeMillis(),
                        isDemo = false
                    )
                )
            }
        }

        @JavascriptInterface
        fun onFail(message: String) {
            fallbackRefresh()
        }
    }

    private fun fallbackRefresh() {
        lifecycleScope.launch {
            val repo = UsageRepository.get(this@LoginActivity)
            val cookie = sessionCookie()
            if (cookie.isNotBlank()) repo.saveCookie(cookie)
            val snapshot = withContext(Dispatchers.IO) { repo.refresh(force = true) }
            if (!snapshot.isDemo) {
                persist(snapshot)
            } else if (cookie.isNotBlank()) {
                setStatus("Signed in. Usage will refresh in the app — tap Done if this stays open.")
                capturing = false
                persistSignedInWithoutUsage(cookie)
            } else {
                capturing = false
                fetchStarted = false
                setStatus("Couldn't read a session yet. Finish login, then tap Done.")
            }
        }
    }

    private suspend fun persistSignedInWithoutUsage(cookie: String) {
        val repo = UsageRepository.get(this)
        repo.saveCookie(cookie)
        WidgetUpdater.updateAll(this)
        finishOk()
    }

    private suspend fun persist(snapshot: UsageSnapshot) {
        if (finished) return
        val repo = UsageRepository.get(this)
        val cookie = sessionCookie()
        if (cookie.isNotBlank()) repo.saveCookie(cookie)
        repo.saveSnapshot(snapshot)
        WidgetUpdater.updateAll(this)
        finishOk()
    }

    private fun finishOk() {
        runOnUiThread {
            if (finished) return@runOnUiThread
            finished = true
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val FETCH_SCRIPT = """
            (function() {
              fetch('https://grok.com/grok_api_v2.GrokBuildBilling/GetGrokCreditsConfig', {
                method: 'POST',
                credentials: 'include',
                headers: {
                  'Content-Type': 'application/grpc-web+proto',
                  'X-Grpc-Web': '1',
                  'connect-protocol-version': '1'
                },
                body: new Uint8Array(5)
              }).then(function(res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.arrayBuffer();
              }).then(function(buf) {
                var bytes = new Uint8Array(buf);
                var binary = '';
                for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
                GrokBar.onCredits(btoa(binary));
              }).catch(function(err) {
                GrokBar.onFail(String(err));
              });
            })();
        """.trimIndent()
    }
}
