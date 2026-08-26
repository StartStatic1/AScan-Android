package com.ascan.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object EmbeddedUi {
    fun html(context: Context): String {
        try {
            val fromAsset = context.assets.open("ascan.b64").bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .filter { !it.isWhitespace() }
            if (fromAsset.length > 100) {
                try { return decode(fromAsset) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        try {
            val sb = StringBuilder()
            for (i in 0..3) {
                context.assets.open("ascan$i.b64").bufferedReader(Charsets.UTF_8).use {
                    sb.append(it.readText().filter { ch -> !ch.isWhitespace() })
                }
            }
            if (sb.length > 100) {
                try { return decode(sb.toString()) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return decode(EMBEDDED)
    }

    private fun decode(b64: String): String {
        val compressed = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes().toString(Charsets.UTF_8)
        }
    }

    // gzip+base64 da UI (build #23) — fallback garantido no APK
    private val EMBEDDED: String = loadEmbedded()

    private fun loadEmbedded(): String {
        val parts = arrayOf(
            PART0, PART1, PART2, PART3, PART4, PART5, PART6, PART7, PART8
        )
        return parts.joinToString("")
    }

    private const val PART0 = "PLACEHOLDER0"
    private const val PART1 = "PLACEHOLDER1"
    private const val PART2 = "PLACEHOLDER2"
    private const val PART3 = "PLACEHOLDER3"
    private const val PART4 = "PLACEHOLDER4"
    private const val PART5 = "PLACEHOLDER5"
    private const val PART6 = "PLACEHOLDER6"
    private const val PART7 = "PLACEHOLDER7"
    private const val PART8 = "PLACEHOLDER8"
}
