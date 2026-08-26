package com.ascan.app

import android.content.Context
import java.security.MessageDigest

object UnlockStore {
    // Código padrão: AScan@2026  (só o hash fica no APK)
    private const val CODE_HASH =
        "333f287599251371866e22bb401fd3e4e5313601f41627967c47ec37fcd15462"

    private const val PREFS = "ascan_lock"
    private const val KEY_UNLOCKED = "unlocked"

    fun isUnlocked(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNLOCKED, false)

    fun unlock(ctx: Context, code: String): Boolean {
        val ok = sha256(code.trim()) == CODE_HASH
        if (ok) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UNLOCKED, true)
                .apply()
        }
        return ok
    }

    fun lock(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UNLOCKED, false)
            .apply()
    }

    fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
