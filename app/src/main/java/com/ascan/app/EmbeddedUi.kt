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
    private const val COMBOS = "https://raw.githubusercontent.com/StartStatic1/AScan-Combos/main"
    private const val COMBOS_CDN = "https://cdn.jsdelivr.net/gh/StartStatic1/AScan-Combos@main"

    private val SINGLE_SOURCES = arrayOf(
        "$COMBOS_CDN/ascan.b64",
        "$COMBOS/ascan.b64"
    )

    private val io = Executors.newSingleThreadExecutor()

    fun html(context: Context): String {
        tryDecodeAsset(context, "ascan.b64")?.let { return it }

        try {
            val sb = StringBuilder()
            for (i in 0..3) {
                context.assets.open("ascan$i.b64").bufferedReader(Charsets.UTF_8).use {
                    sb.append(it.readText().filter { ch -> !ch.isWhitespace() })
                }
            }
            if (sb.length > 5000) {
                try { return decode(sb.toString()) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        try {
            val joined = fetchParts()
            if (joined.length > 5000) return decode(joined)
        } catch (_: Exception) {}

        var lastErr: Exception? = null
        for (url in SINGLE_SOURCES) {
            try {
                val fut = io.submit(Callable { fetch(url) })
                val r = fut.get(40, TimeUnit.SECONDS)
                if (r.length > 5000) return decode(r)
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw IllegalStateException("UI falhou: ${lastErr?.message ?: "sem fonte"}")
    }

    private fun fetchParts(): String {
        val bases = arrayOf(COMBOS_CDN, COMBOS)
        var last: Exception? = null
        for (base in bases) {
            try {
                val sb = StringBuilder()
                for (i in 0..3) {
                    val fut = io.submit(Callable { fetch("$base/ascan$i.b64") })
                    sb.append(fut.get(25, TimeUnit.SECONDS))
                }
                if (sb.length > 5000) return sb.toString()
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("parts fail")
    }

    private fun tryDecodeAsset(context: Context, name: String): String? {
        return try {
            val a = context.assets.open(name).bufferedReader(Charsets.UTF_8)
                .use { it.readText() }.filter { !it.isWhitespace() }
            if (a.length > 5000) decode(a) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 40000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AScanApp/1.1")
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
