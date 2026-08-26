package com.ascan.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object EmbeddedUi {
    private val REMOTE_BASE =
        "https://raw.githubusercontent.com/StartStatic1/AScan-Android/main/app/src/main/assets/"

    fun html(context: Context): String {
        try {
            val a = loadFromAssets(context)
            if (a.isNotBlank()) return a
        } catch (_: Exception) {}
        try {
            val r = loadFromRemoteParts()
            if (r.isNotBlank()) return r
        } catch (_: Exception) {}
        throw IllegalStateException("UI nao encontrada (ascan.b64 / ascan0-3)")
    }

    private fun loadFromAssets(context: Context): String {
        // Prefer single file, then 4 parts
        try {
            val single = context.assets.open("ascan.b64").bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .filter { !it.isWhitespace() }
            if (single.isNotEmpty()) {
                try {
                    return decode(single)
                } catch (_: Exception) {
                    // fall through to parts
                }
            }
        } catch (_: Exception) {}

        val sb = StringBuilder()
        for (i in 0..3) {
            context.assets.open("ascan$i.b64").bufferedReader(Charsets.UTF_8).use {
                sb.append(it.readText().filter { ch -> !ch.isWhitespace() })
            }
        }
        return decode(sb.toString())
    }

    private fun loadFromRemoteParts(): String {
        val sb = StringBuilder()
        for (i in 0..3) {
            val url = REMOTE_BASE + "ascan$i.b64"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use {
                sb.append(it.readText().filter { ch -> !ch.isWhitespace() })
            }
        }
        return decode(sb.toString())
    }

    private fun decode(b64: String): String {
        val compressed = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes().toString(Charsets.UTF_8)
        }
    }
}
