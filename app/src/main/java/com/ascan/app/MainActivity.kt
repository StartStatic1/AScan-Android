package com.ascan.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
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
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null

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
            hint = "Codigo"
            setHintTextColor(Color.parseColor("#5A5A72"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A1A26"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }

        val btn = Button(this).apply {
            text = "DESBLOQUEAR"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#A855F7"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val info = TextView(this).apply {
            text = "t.me/ApkBugado"
            setTextColor(Color.parseColor("#5A5A72"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }

        btn.setOnClickListener {
            val code = input.text?.toString().orEmpty()
            if (UnlockStore.unlock(this@MainActivity, code)) {
                Toast.makeText(this@MainActivity, "Acesso liberado", Toast.LENGTH_SHORT).show()
                openApp()
            } else {
                Toast.makeText(this@MainActivity, "Codigo invalido", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(title)
        root.addView(sub)
        root.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
        root.addView(btn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(info)
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openApp() {
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
            settings.userAgentString = settings.userAgentString + " AScanApp/1.0"
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.defaultTextEncodingName = "utf-8"
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false
            }
            webChromeClient = WebChromeClient()
        }
        wv.loadDataWithBaseURL(
            "https://app.ascan.local/",
            EmbeddedUi.html(),
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
        super.onDestroy()
    }
}
