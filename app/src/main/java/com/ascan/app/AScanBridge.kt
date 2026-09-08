package com.ascan.app

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class AScanBridge(
    private val activity: MainActivity,
    private val webViewProvider: () -> WebView?,
    private val httpExecutor: ExecutorService
) {
    @Volatile private var proxies: List<String> = emptyList()
    @Volatile private var proxyMode: String = "off"
    private val proxyCursor = AtomicInteger(0)

    private val userAgents = listOf(
        "TiviMate/5.1.0 (Linux; Android 13)",
        "IPTV Smarters Pro/3.1.5 (Linux; Android 12)",
        "OTT Navigator/1.7.2.2 (Linux; Android 11)",
        "GSE SMART IPTV/7.4 (Linux; Android 13)",
        "XCIPTV/1.9.9 (Linux; Android 12)",
        "VLC/3.0.20 LibVLC/3.0.20",
        "okhttp/4.12.0",
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    )

    private val proxySources = listOf(
        "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=10000&country=all&ssl=all&anonymity=all",
        "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=10000&country=BR",
        "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=10000&country=US",
        "https://raw.githubusercontent.com/mmpx12/proxy-list/master/http.txt",
        "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
        "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS_RAW.txt",
        "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/http/data.txt"
    )

    private val repoProxyUrls = listOf(
        "https://raw.githubusercontent.com/StartStatic1/AScan-AgenT-2.0-/main/proxies/list.txt",
        "https://raw.githubusercontent.com/StartStatic1/AScan-Combos/main/proxies.txt"
    )

    private fun randomUa(): String = userAgents[Random.nextInt(userAgents.size)]

    private fun parseProxyLine(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) return null
        val cleaned = t.removePrefix("http://").removePrefix("https://").removePrefix("socks5://")
        return if (cleaned.contains(":")) cleaned else null
    }

    private fun nextProxy(): Proxy? {
        val list = proxies
        if (list.isEmpty() || proxyMode != "on") return null
        val idx = Math.floorMod(proxyCursor.getAndIncrement(), list.size)
        val raw = list[idx]
        return try {
            val hostPort = raw.substringAfter("@")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":").substringBefore("/").toInt()
            Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        } catch (_: Exception) {
            null
        }
    }

    private fun doHttp(url: String, timeoutMs: Int, forceProxy: Boolean = true): String {
        return try {
            val timeout = timeoutMs.coerceIn(2000, 30000)
            val proxy = if (forceProxy) nextProxy() else null
            val conn = (if (proxy != null) URL(url).openConnection(proxy) else URL(url).openConnection()) as HttpURLConnection
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", randomUa())
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            conn.setRequestProperty("Cache-Control", "no-cache")
            val code = try {
                conn.responseCode
            } catch (e: Exception) {
                if (proxy != null) return doHttp(url, timeoutMs, forceProxy = false)
                throw e
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            JSONObject().put("ok", code in 200..299).put("status", code).put("body", body).toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("status", 0).put("error", e.message ?: "net").put("body", "").toString()
        }
    }

    private fun fetchTextDirect(url: String, timeoutMs: Int = 20000): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AScanApp/1.1")
            setRequestProperty("Accept", "text/plain,*/*")
        }
        if (conn.responseCode !in 200..299) return ""
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun parseProxyBlob(text: String): List<String> =
        text.lineSequence().mapNotNull { parseProxyLine(it) }.distinct().toList()

    @JavascriptInterface
    fun httpGet(url: String, timeoutMs: Int): String = doHttp(url, timeoutMs, forceProxy = true)

    @JavascriptInterface
    fun httpGetAsync(url: String, timeoutMs: Int, callbackId: String) {
        httpExecutor.execute {
            val result = doHttp(url, timeoutMs)
            val safeId = callbackId.replace("'", "").replace("\\", "").replace("\n", "")
            val b64 = android.util.Base64.encodeToString(result.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            activity.runOnUiThread {
                webViewProvider()?.evaluateJavascript(
                    "try{window.__ascanCb&&window.__ascanCb('$safeId','$b64');}catch(e){}",
                    null
                )
            }
        }
    }

    @JavascriptInterface
    fun loadProxiesOnline(): Int {
        val found = linkedSetOf<String>()
        for (src in proxySources) {
            try {
                val body = fetchTextDirect(src, 15000)
                found.addAll(parseProxyBlob(body))
                if (found.size >= 2500) break
            } catch (_: Exception) {
            }
        }
        proxies = found.toList()
        proxyMode = if (proxies.isNotEmpty()) "on" else "off"
        proxyCursor.set(0)
        return proxies.size
    }

    @JavascriptInterface
    fun loadProxiesFromText(text: String): Int {
        val list = parseProxyBlob(text)
        proxies = list
        proxyMode = if (list.isNotEmpty()) "on" else "off"
        proxyCursor.set(0)
        return list.size
    }

    @JavascriptInterface
    fun loadProxiesFromRepo(): Int {
        val found = linkedSetOf<String>()
        for (src in repoProxyUrls) {
            try {
                found.addAll(parseProxyBlob(fetchTextDirect(src, 15000)))
            } catch (_: Exception) {
            }
        }
        if (found.isEmpty()) return loadProxiesOnline()
        proxies = found.toList()
        proxyMode = "on"
        proxyCursor.set(0)
        return proxies.size
    }

    @JavascriptInterface
    fun clearProxies() {
        proxies = emptyList()
        proxyMode = "off"
        proxyCursor.set(0)
    }

    @JavascriptInterface
    fun getProxyStatus(): String {
        return JSONObject()
            .put("count", proxies.size)
            .put("mode", proxyMode)
            .toString()
    }

    @JavascriptInterface
    fun saveText(filename: String, content: String): String {
        return try {
            val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "hits_AScan.txt" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return "erro:uri"
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, safeName)).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
            activity.runOnUiThread {
                Toast.makeText(activity, "Salvo em Downloads: $safeName", Toast.LENGTH_SHORT).show()
            }
            "ok"
        } catch (e: Exception) {
            activity.runOnUiThread {
                Toast.makeText(activity, "Erro ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            "erro:${e.message}"
        }
    }

    @JavascriptInterface
    fun closeApp() {
        activity.runOnUiThread { activity.finishAffinity() }
    }
}
