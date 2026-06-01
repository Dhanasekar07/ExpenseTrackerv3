package com.example.expensetracker

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class TransactionDeduplicator(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("expense_dedup", Context.MODE_PRIVATE)

    companion object {
        private const val WINDOW_MS = 10_000L
        private const val PREFIX    = "h_"
    }

    /** Hash = SHA-256 of "amount|source|second-level-timestamp" */
    fun makeHash(amount: Double, source: String, tsSeconds: Long): String {
        val raw   = "$amount|$source|$tsSeconds"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Returns true if we should SKIP this transaction (already handled). */
    fun isSeen(hash: String): Boolean {
        val t = prefs.getLong(PREFIX + hash, -1L)
        return t != -1L && (System.currentTimeMillis() - t) < WINDOW_MS
    }

    /** Mark this hash as seen right now. */
    fun markSeen(hash: String) {
        prefs.edit().putLong(PREFIX + hash, System.currentTimeMillis()).apply()
    }

    /** Remove stale hashes (call once on app start). */
    fun cleanup() {
        val now  = System.currentTimeMillis()
        val edit = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PREFIX) }
            .forEach { key ->
                if (now - prefs.getLong(key, 0L) > WINDOW_MS) edit.remove(key)
            }
        edit.apply()
    }
}
