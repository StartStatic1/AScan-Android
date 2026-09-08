package com.ascan.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object EmbeddedUi {
    // UI ESTAVEL pinada (commit com startAttack/combo funcionando)
    private const val PINNED = "https://raw.githubusercontent.com/StartStatic1/AScan-Combos/51d40ae2be278ce5e9f7d349e901849a38fe348f/ascan.b64"
    private const val PINNED_CDN = "https://cdn.jsdelivr.net/gh/StartStatic1/AScan-Combos@51d40ae2be278ce5e9f7d349e901849a38fe348f/ascan.b64"

    private val SOURCES = arrayOf(
        PINNED_CDN,
        PINNED,
        "https://cdn.jsdelivr.net/gh/StartStatic1/AScan-Combos@main/ascan.b64",
        "https://raw.githubusercontent.com/StartStatic1/AScan-Combos/main/ascan.b64"
    )

    private val io = Executors.newSingleThreadExecutor()

    fun html(context: Context): String {
        tryDecodeAsset(context, "ascan.b64")?.let { return it }

        var lastErr: Exception? = null
        for (url in SOURCES) {
            try {
                val fut = io.submit(Callable { fetch(url) })
                val r = fut.get(45, TimeUnit.SECONDS)
                if (r.length > 8000) {
                    try { return decode(r) } catch (e: Exception) { lastErr = e }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }

        try {
            val sb = StringBuilder()
            for (i in 0..3) {
                context.assets.open("ascan$i.b64").bufferedReader(Charsets.UTF_8).use {
                    sb.append(it.readText().filter { ch -> !ch.isWhitespace() })
                }
            }
            if (sb.length > 8000) return decode(sb.toString())
        } catch (e: Exception) {
            lastErr = e
        }

        throw IllegalStateException("UI falhou: ${lastErr?.message ?: "sem fonte"}")
    }

    private fun tryDecodeAsset(context: Context, name: String): String? {
        return try {
            val a = context.assets.open(name).bufferedReader(Charsets.UTF_8)
                .use { it.readText() }.filter { !it.isWhitespace() }
            if (a.length > 8000) decode(a) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 45000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AScanApp/1.1.1")
            instanceFollowRedirects = true
        }
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use {
            it.readText().filter { ch -> !ch.isWhitespace() }
        }
    }

    private fun decode(b64: String): String {
        val compressed = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes().toString(Charsets.UTF_8)
        }
    }
}
