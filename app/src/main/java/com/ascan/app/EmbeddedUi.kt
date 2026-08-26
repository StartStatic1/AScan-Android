package com.ascan.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object EmbeddedUi {
    private val REMOTE = "https://raw.githubusercontent.com/StartStatic1/AScan-Android/main/app/src/main/assets/ascan.b64"
    private val PARTS = arrayOf("ascan0.b64", "ascan1.b64", "ascan2.b64", "ascan3.b64")

    fun html(context: Context): String {
        try {
            val a = loadFromAssets(context)
            if (a.isNotBlank()) return a
        } catch (_: Exception) {}
        try {
            val r = loadFromUrl(REMOTE)
            if (r.isNotBlank()) return r
        } catch (_: Exception) {}
        throw IllegalStateException("UI nao encontrada")
    }

    private fun loadFromAssets(context: Context): String {
        try {
            val b64 = context.assets.open("ascan.b64").bufferedReader(Charsets.UTF_8)
                .use { it.readText() }.filter { !it.isWhitespace() }
            if (b64.isNotEmpty()) return decode(b64)
        } catch (_: Exception) {}
        val b64 = buildString {
            for (name in PARTS) {
                append(context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
        }.filter { !it.isWhitespace() }
        if (b64.isEmpty()) return ""
        return decode(b64)
    }

    private fun loadFromUrl(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000; requestMethod = "GET"
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use {
            return decode(it.readText().filter { ch -> !ch.isWhitespace() })
        }
    }

    private fun decode(b64: String): String {
        val compressed = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes().toString(Charsets.UTF_8)
        }
    }
}
