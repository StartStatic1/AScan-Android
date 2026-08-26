package com.ascan.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val httpExecutor = Executors.newFixedThreadPool(12)

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = filePathCallback
        filePathCallback = null
        if (cb == null) return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            cb.onReceiveValue(null)
            return@registerForActivityResult
        }
        val data = result.data!!
        val uris: Array<Uri>? = when {
            data.clipData != null -> {
                Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        cb.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (UnlockStore.isUnlocked(this)) {
            openApp()
        } else {
            showUnlockScreen()
        }
    }

    private fun showUnlockScreen() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "AScan"
            setTextColor(Color.parseColor("#E879F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Digite o codigo de acesso"
            setTextColor(Color.parseColor("#8B8BA3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Codigo"
            setHintTextColor(Color.parseColor("#5A5A72"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A1A24"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btn = Button(this).apply {
            text = "ENTRAR"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7C3AED"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
            setOnClickListener {
                val code = input.text?.toString().orEmpty()
                if (UnlockStore.unlock(this@MainActivity, code)) {
                    openApp()
                } else {
                    Toast.makeText(this@MainActivity, "Codigo invalido", Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(title)
        root.addView(sub)
        root.addView(input)
        root.addView(btn)
        setContentView(root)
    }

    inner class AScanBridge {
        private fun doHttp(url: String, timeoutMs: Int): String {
            return try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = timeoutMs.coerceIn(2000, 30000)
                    readTimeout = timeoutMs.coerceIn(2000, 30000)
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept", "application/json,text/plain,*/*")
                    setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                JSONObject()
                    .put("ok", code in 200..299)
                    .put("status", code)
                    .put("body", body)
                    .toString()
            } catch (e: Exception) {
                JSONObject()
                    .put("ok", false)
                    .put("status", 0)
                    .put("error", e.message ?: "net")
                    .put("body", "")
                    .toString()
            }
        }

        @JavascriptInterface
        fun httpGet(url: String, timeoutMs: Int): String {
            return doHttp(url, timeoutMs)
        }

        @JavascriptInterface
        fun httpGetAsync(url: String, timeoutMs: Int, callbackId: String) {
            httpExecutor.execute {
                val result = doHttp(url, timeoutMs)
                val safeId = callbackId.replace("'", "").replace("\\", "").replace("\n", "")
                val b64 = android.util.Base64.encodeToString(
                    result.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                runOnUiThread {
                    webView?.evaluateJavascript(
                        "try{window.__ascanCb&&window.__ascanCb('$safeId','$b64');}catch(e){}",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun closeApp() {
            runOnUiThread { finishAffinity() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openApp() {
        val html = try {
            EmbeddedUi.html(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao carregar UI: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val wv = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.defaultTextEncodingName = "utf-8"
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(AScanBridge(), "AScanNative")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    return try {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/*", "*/*"))
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                        }
                        fileChooserLauncher.launch(
                            Intent.createChooser(intent, "Escolher arquivo .txt")
                        )
                        true
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        filePathCallback?.onReceiveValue(null)
                        Toast.makeText(
                            this@MainActivity,
                            "Nao foi possivel abrir o seletor de arquivos",
                            Toast.LENGTH_SHORT
                        ).show()
                        false
                    }
                }
            }
        }
        wv.loadDataWithBaseURL(
            "https://app.ascan.local/",
            html,
            "text/html",
            "UTF-8",
            null
        )
        webView = wv
        setContentView(wv)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        httpExecutor.shutdownNow()
        super.onDestroy()
    }
}
