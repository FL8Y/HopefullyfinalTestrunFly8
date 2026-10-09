package com.fl8y.lightcloner

import android.util.Log
import java.util.ArrayDeque

/**
 * Bounded in-memory diagnostics for connection and virtualized-guest failures.
 * Pairing codes and private keys must never be passed to record().
 */
object Fly8Diagnostics {
    private const val TAG = "FLY8.Diagnostics"
    private const val MAX_ENTRIES = 300
    private val lock = Any()
    private val entries = ArrayDeque<String>()

    fun record(component: String, message: String, failure: Throwable? = null) {
        val details = failure?.let {
            " | ${it.javaClass.simpleName}: ${it.message.orEmpty()}"
        }.orEmpty()
        val line = "${System.currentTimeMillis()} [${singleLine(component)}] ${singleLine(message)}${singleLine(details)}"
        synchronized(lock) {
            entries.addLast(line)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        if (failure == null) Log.i(TAG, line) else Log.e(TAG, line, failure)
    }

    fun snapshot(): String = synchronized(lock) {
        if (entries.isEmpty()) "No diagnostic events have been captured yet."
        else entries.joinToString(separator = "\n")
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    private fun singleLine(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').take(900)
}
