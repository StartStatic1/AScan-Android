package com.ascan.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object EmbeddedUi {
    // Commit conhecido com ascan.b64 VALIDO (build #23)
    private const val GOOD_RAW =
        "https://raw.githubusercontent.com/StartStatic1/AScan-Android/941f2a0e6fe702710054ac93121da8405eafd51f/app/src/main/assets/ascan.b64"

    fun html(context: Context): String {
        // 1) assets locais
        try {
            val a = context.assets.open("ascan.b64").bufferedReader(Charsets.UTF_8)
                .use { it.readText() }.filter { !it.isWhitespace() }
            if (a.length > 500) {
                try { return decode(a) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 2) raw do commit bom (internet)
        try {
            val r = fetch(GOOD_RAW)
            if (r.length > 500) return decode(r)
        } catch (_: Exception) {}

        throw IllegalStateException("UI nao encontrada. Reinstale o APK do build verde.")
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 40000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AScanApp/1.0")
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
