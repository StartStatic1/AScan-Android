package com.ascan.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object EmbeddedUi {
    private val FILES = arrayOf("ascan0.b64", "ascan1.b64", "ascan2.b64", "ascan3.b64")

    fun html(context: Context): String {
        val b64 = buildString {
            for (name in FILES) {
                append(context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
        }.filter { !it.isWhitespace() }
        val compressed = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes().toString(Charsets.UTF_8)
        }
    }
}
