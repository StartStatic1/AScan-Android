package com.ascan.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object EmbeddedUi {
    private val REMOTE = "https://raw.githubusercontent.com/StartStatic1/AScan-Combos/main/ascan.b64"
    private val FILES = arrayOf("ascan0.b64", "ascan1.b64", "ascan2.b64", "ascan3.b64")

    fun html(context: Context): String {
        try {
            val fromAssets = loadFromAssets(context)
            if (fromAssets.isNotBlank()) return fromAssets
        } catch (_: Exception) {}

        val remote = loadFromUrl(REMOTE)
        if (remote.isNotBlank()) return remote

        throw IllegalStateException("UI nao encontrada (assets nem GitHub)")
    }

    private fun loadFromAssets(context: Context): String {
        val b64 = buildString {
            for (name in FILES) {
                try {
                    append(context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() })
                } catch (_: Exception) {
                    try {
                        return decode(context.assets.open("ascan.b64").bufferedReader(Charsets.UTF_8).use { it.readText() })
                    } catch (_: Exception) {
                        return ""
                    }
                }
            }
        }.filter { !it.isWhitespace() }
        if (b64.isEmpty()) return ""
        return decode(b64)
    }

    private fun loadFromUrl(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            requestMethod = "GET"
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val b64 = reader.readText().filter { !it.isWhitespace() }
            return decode(b64)
        }
    }

    private fun decode(b64: String): String {
        val compressed = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes().toString(Charsets.UTF_8)
        }
    }
}
