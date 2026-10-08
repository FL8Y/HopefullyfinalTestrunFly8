package com.fl8y.lightcloner.connection

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.IOException

/**
 * Async facade over LibADB's blocking API.
 *
 * Pairing codes are never persisted by this layer.
 */
class WirelessAdbController(
    context: Context,
    private val callback: Callback,
) {

    interface Callback {
        fun onStatus(message: String)
        fun onConnected(host: String, port: Int)
        fun onPairingSucceeded(host: String, port: Int)
        fun onFailure(message: String, error: Throwable? = null)
    }

    private val manager = Fly8AdbManager(context)
    private val main = Handler(Looper.getMainLooper())

    fun pair(host: String, pairingPort: Int, pairingCode: String) {
        val safeHost = host.trim()
        val safeCode = pairingCode.trim()

        if (safeHost.isEmpty()) {
            callback.onFailure("Host is required.")
            return
        }
        if (pairingPort !in 1..65535) {
            callback.onFailure("Pairing port is invalid.")
            return
        }
        if (!safeCode.matches(Regex("\\d{6}"))) {
            callback.onFailure("Pairing code must contain exactly six digits.")
            return
        }

        Thread {
            try {
                postStatus("Pairing with $safeHost:$pairingPort…")
                manager.pair(safeHost, pairingPort, safeCode)
                post { callback.onPairingSucceeded(safeHost, pairingPort) }
            } catch (t: Throwable) {
                post { callback.onFailure(connectionMessage(t), t) }
            }
        }.start()
    }

    fun connect(host: String, connectPort: Int) {
        val safeHost = host.trim()

        if (safeHost.isEmpty()) {
            callback.onFailure("Host is required.")
            return
        }
        if (connectPort !in 1..65535) {
            callback.onFailure("Connection port is invalid.")
            return
        }

        Thread {
            try {
                postStatus("Connecting to $safeHost:$connectPort…")
                val connected = manager.connect(safeHost, connectPort)
                if (!connected) {
                    throw IOException("ADB connection was not established.")
                }
                post { callback.onConnected(safeHost, connectPort) }
            } catch (t: Throwable) {
                post { callback.onFailure(connectionMessage(t), t) }
            }
        }.start()
    }

    fun autoConnect(timeoutMs: Long = 8_000L) {
        Thread {
            try {
                postStatus("Discovering paired ADB endpoint…")
                val connected = manager.autoConnect(timeoutMs)
                if (!connected) {
                    throw IOException("ADB connection was not established.")
                }
                post { callback.onConnected(manager.hostAddress, -1) }
            } catch (t: Throwable) {
                post { callback.onFailure(connectionMessage(t), t) }
            }
        }.start()
    }

    fun close() {
        runCatching { manager.close() }
    }

    private fun connectionMessage(error: Throwable): String {
        return when (error) {
            is AdbPairingRequiredException ->
                "The device is not paired for ADB. Open Wireless Debugging and pair this device first."
            is AdbAuthenticationFailedException ->
                "ADB authentication was rejected. Re-pair the device and try again."
            is SecurityException ->
                "The device rejected the network operation."
            is java.net.ConnectException ->
                "Could not reach the ADB endpoint. Check the IP address, port, and Wi-Fi connection."
            else ->
                error.message?.takeIf { it.isNotBlank() } ?: "ADB operation failed."
        }
    }

    private fun postStatus(message: String) {
        main.post { callback.onStatus(message) }
    }

    private fun post(action: () -> Unit) {
        main.post(action)
    }
}
