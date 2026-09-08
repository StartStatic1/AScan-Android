package com.ascan.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import android.webkit.JsResult
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
            data.clipData != null -> Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        cb.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (UnlockStore.isUnlocked(this)) openApp() else showUnlockScreen()
    }

    private fun showUnlockScreen() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val btn = Button(this).apply {
            text = "ENTRAR"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7C3AED"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
            setOnClickListener {
                if (UnlockStore.unlock(this@MainActivity, input.text?.toString().orEmpty())) openApp()
                else Toast.makeText(this@MainActivity, "Codigo invalido", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(title); root.addView(sub); root.addView(input); root.addView(btn)
        setContentView(root)
    }

    private fun injectProxyCardJs(): String = """
        (function(){
          try{
            if(document.getElementById('ascan-proxy-card')) return 'exists';
            var actions = document.querySelector('.actions');
            var btn = document.querySelector('.btn-start');
            var anchor = actions || (btn && btn.parentNode);
            if(!anchor) return 'no-anchor';
            var host = anchor.parentNode || document.body;
            var card = document.createElement('div');
            card.id = 'ascan-proxy-card';
            card.className = 'card';
            card.style.cssText = 'margin:12px 0;padding:12px;border-radius:12px;background:#14141c;border:1px solid #2a2a3a;';
            card.innerHTML = '<div style="font-weight:700;margin-bottom:10px;color:#e879f9">Proxy</div>'
              + '<div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px">'
              + '<button type="button" id="btn-proxy-online" style="flex:1;min-width:90px;padding:10px;border:0;border-radius:10px;background:#2563eb;color:#fff;font-weight:600">Online</button>'
              + '<button type="button" id="btn-proxy-offline" style="flex:1;min-width:90px;padding:10px;border:0;border-radius:10px;background:#2a2a3a;color:#fff">Offline</button>'
              + '<button type="button" id="btn-proxy-clear" style="flex:1;min-width:90px;padding:10px;border:0;border-radius:10px;background:#2a2a3a;color:#fff">Limpar</button>'
              + '</div>'
              + '<label style="font-size:12px;color:#8b8ba3">Cole proxies offline (host:port)</label>'
              + '<textarea id="proxy-paste" rows="3" placeholder="1.2.3.4:8080" style="width:100%;margin-top:6px;padding:10px;border-radius:9px;border:1px solid #333;background:#1a1a24;color:#fff;font-family:monospace;font-size:12px"></textarea>'
              + '<div id="proxy-status" style="margin-top:8px;font-size:13px;color:#8b8ba3">Sem proxy (direto)</div>';
            host.insertBefore(card, anchor);
            function tmsg(m){ try{ if(typeof toast==='function') toast(m); }catch(e){} }
            function refresh(){
              var el=document.getElementById('proxy-status'); if(!el) return;
              try{
                if(window.AScanNative && AScanNative.getProxyStatus){
                  var st=JSON.parse(AScanNative.getProxyStatus());
                  el.textContent = st.count>0 ? ('Proxy ON · '+st.count) : 'Sem proxy (direto)';
                  el.style.color = st.count>0 ? '#4ade80' : '#8b8ba3';
                  return;
                }
              }catch(e){}
              el.textContent='Sem proxy (direto)';
            }
            var bo=document.getElementById('btn-proxy-online');
            var bf=document.getElementById('btn-proxy-offline');
            var bc=document.getElementById('btn-proxy-clear');
            if(bo) bo.onclick=function(){
              if(!(window.AScanNative&&AScanNative.loadProxiesOnline)){ tmsg('Bridge ausente'); return; }
              tmsg('Baixando proxies (20-40s)...');
              setTimeout(function(){
                try{ var n=AScanNative.loadProxiesOnline(); refresh(); tmsg(n>0?('OK '+n+' proxies'):'Nenhum proxy'); }
                catch(e){ tmsg('Erro: '+e); }
              }, 40);
            };
            if(bf) bf.onclick=function(){
              var t=((document.getElementById('proxy-paste')||{}).value||'').trim();
              if(!t){ tmsg('Cole host:port'); return; }
              if(!(window.AScanNative&&AScanNative.loadProxiesFromText)){ tmsg('Bridge ausente'); return; }
              try{ var n=AScanNative.loadProxiesFromText(t); refresh(); tmsg(n>0?('OK '+n+' offline'):'Formato invalido'); }
              catch(e){ tmsg('Erro'); }
            };
            if(bc) bc.onclick=function(){
              try{ if(window.AScanNative&&AScanNative.clearProxies) AScanNative.clearProxies(); }catch(e){}
              var el=document.getElementById('proxy-paste'); if(el) el.value='';
              refresh(); tmsg('Proxies limpos');
            };
            refresh();
            return 'ok';
          }catch(e){ return 'err:'+e; }
        })();
    """.trimIndent()

    private fun injectHitsButtonsJs(): String = """
        (function(){
          function tmsg(m){ try{ if(typeof toast==='function') toast(m); }catch(e){} }
          function allRaws(){
            try{
              if(typeof getAllRaws==='function') return getAllRaws();
              var a=[]; if(window.state&&state.hitsLog){ for(var s in state.hitsLog) a=a.concat(state.hitsLog[s]); }
              return a;
            }catch(e){ return []; }
          }
          function bind(sel, fn){
            var el=document.querySelector(sel); if(!el||!el.parentNode) return;
            var n=el.cloneNode(true); el.parentNode.replaceChild(n,el);
            n.addEventListener('click', function(e){ e.preventDefault(); e.stopPropagation(); fn(); });
          }
          bind('#btn-download', function(){
            var h=allRaws();
            if(!h||!h.length){ tmsg('Nenhum HIT para salvar (so conta HITS, nao erros)'); return; }
            var name='hits_AScan_'+(new Date().toISOString().slice(0,10))+'.txt';
            var text='\\uFEFF'+h.join('\\n\\n');
            try{
              if(window.AScanNative && AScanNative.saveText){
                var r=AScanNative.saveText(name, text);
                tmsg(r&&String(r).indexOf('fail')===0 ? ('Falha: '+r) : ('Salvo em Downloads/'+name));
              } else tmsg('Bridge saveText ausente');
            }catch(e){ tmsg('Erro ao salvar: '+e); }
          });
          bind('.btn-copy', function(){
            var h=allRaws();
            if(!h||!h.length){ tmsg('Nenhum HIT para copiar'); return; }
            var t=h.join('\\n\\n');
            if(navigator.clipboard&&navigator.clipboard.writeText){
              navigator.clipboard.writeText(t).then(function(){ tmsg('Hits copiados!'); }).catch(function(){ tmsg('Falha ao copiar'); });
            } else tmsg('Clipboard indisponivel');
          });
          return 'hits-bound';
        })();
    """.trimIndent()

    private fun injectUiFixes(wv: WebView) {
        wv.evaluateJavascript(injectProxyCardJs(), null)
        wv.evaluateJavascript(injectHitsButtonsJs(), null)

        val js = try {
            assets.open("inject_v111.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            "void 0;"
        }
        if (js.isNotBlank() && js != "void 0;") {
            wv.evaluateJavascript(js, null)
        }

        wv.postDelayed({
            wv.evaluateJavascript(injectProxyCardJs(), null)
            wv.evaluateJavascript(injectHitsButtonsJs(), null)
        }, 600)
        wv.postDelayed({
            wv.evaluateJavascript(injectProxyCardJs(), null)
            wv.evaluateJavascript(injectHitsButtonsJs(), null)
        }, 1500)
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
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.defaultTextEncodingName = "utf-8"
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(AScanBridge(this@MainActivity, { webView }, httpExecutor), "AScanNative")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.let { injectUiFixes(it) }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    runOnUiThread {
                        AlertDialog.Builder(this@MainActivity)
                            .setMessage(message ?: "")
                            .setPositiveButton("OK") { _, _ -> result?.confirm() }
                            .setOnCancelListener { result?.confirm() }
                            .show()
                    }
                    return true
                }
                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    runOnUiThread {
                        AlertDialog.Builder(this@MainActivity)
                            .setMessage(message ?: "")
                            .setPositiveButton("OK") { _, _ -> result?.confirm() }
                            .setNegativeButton("Cancelar") { _, _ -> result?.cancel() }
                            .setOnCancelListener { result?.cancel() }
                            .show()
                    }
                    return true
                }
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
                        }
                        fileChooserLauncher.launch(Intent.createChooser(intent, "Escolher arquivo .txt"))
                        true
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        filePathCallback?.onReceiveValue(null)
                        false
                    }
                }
            }
        }
        wv.loadDataWithBaseURL("https://app.ascan.local/", html, "text/html", "UTF-8", null)
        webView = wv
        setContentView(wv)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        httpExecutor.shutdownNow()
        super.onDestroy()
    }
}
