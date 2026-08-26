package com.ascan.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object EmbeddedUi {
    fun html(context: Context): String {
        val b64 = context.assets.open("ascan.b64").bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .filter { !it.isWhitespace() }
        val compressed = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes().toString(Charsets.UTF_8)
        }
    }
}
