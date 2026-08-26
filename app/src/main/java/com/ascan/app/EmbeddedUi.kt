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
    // UI valida em CDN (commit 941f2a0e — build #23)
    private val SOURCES = arrayOf(
        "https://cdn.jsdelivr.net/gh/StartStatic1/AScan-Android@941f2a0e6fe702710054ac93121da8405eafd51f/app/src/main/assets/ascan.b64",
        "https://raw.githack.com/StartStatic1/AScan-Android/941f2a0e6fe702710054ac93121da8405eafd51f/app/src/main/assets/ascan.b64"
    )

    private val io = Executors.newSingleThreadExecutor()

    fun html(context: Context): String {
        // 1) asset local ascan.b64
        tryDecodeAsset(context, "ascan.b64")?.let { return it }

        // 2) partes ascan0-3
        try {
            val sb = StringBuilder()
            for (i in 0..3) {
                context.assets.open("ascan$i.b64").bufferedReader(Charsets.UTF_8).use {
                    sb.append(it.readText().filter { ch -> !ch.isWhitespace() })
                }
            }
            if (sb.length > 500) {
                try { return decode(sb.toString()) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 3) CDN (sempre funciona offline-build)
        var lastErr: Exception? = null
        for (url in SOURCES) {
            try {
                val fut = io.submit(Callable { fetch(url) })
                val r = fut.get(40, TimeUnit.SECONDS)
                if (r.length > 500) return decode(r)
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw IllegalStateException("UI falhou: ${lastErr?.message ?: "sem fonte"}")
    }

    private fun tryDecodeAsset(context: Context, name: String): String? {
        return try {
            val a = context.assets.open(name).bufferedReader(Charsets.UTF_8)
                .use { it.readText() }.filter { !it.isWhitespace() }
            if (a.length > 500) decode(a) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 40000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AScanApp/1.0")
            instanceFollowRedirects = true
        }
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${conn.responseCode} $url")
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
