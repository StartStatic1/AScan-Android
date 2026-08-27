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
import java.net.URL
import java.util.concurrent.ExecutorService

class AScanBridge(
    private val activity: MainActivity,
    private val webViewProvider: () -> WebView?,
    private val httpExecutor: ExecutorService
) {
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
            JSONObject().put("ok", code in 200..299).put("status", code).put("body", body).toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("status", 0).put("error", e.message ?: "net").put("body", "").toString()
        }
    }

    @JavascriptInterface
    fun httpGet(url: String, timeoutMs: Int): String = doHttp(url, timeoutMs)

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
