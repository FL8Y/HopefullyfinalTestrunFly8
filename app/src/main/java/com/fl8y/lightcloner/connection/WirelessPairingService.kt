package com.fl8y.lightcloner.connection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import com.fl8y.lightcloner.Fly8Diagnostics
import com.fl8y.lightcloner.MainActivity
import com.fl8y.lightcloner.R
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Keeps the wireless ADB pairing workflow alive while the user is in Android Settings.
 *
 * The pairing and normal ADB connection services are different mDNS endpoints:
 * _adb-tls-pairing._tcp appears only while Android's pairing-code screen is active;
 * _adb-tls-connect._tcp is used for the authenticated connection after pairing.
 */
class WirelessPairingService : Service() {
    private lateinit var nsd: NsdManager
    private lateinit var adb: Fly8AdbManager
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var discovery: NsdManager.DiscoveryListener? = null
    private var activeNotificationId = NOTIFICATION_ID_FINDING
    private var foregroundStarted = false
    private var pairingHost: String? = null
    private var pairingPort: Int = -1
    private var pairedHost: String? = null
    private var phase = Phase.FINDING_PAIRING
    private var discoveryStartedAt = 0L
    private var resolveInFlight = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private var phaseTimeout: Runnable? = null

    private enum class Phase { FINDING_PAIRING, ENTER_CODE, PAIRING, FINDING_CONNECT, CONNECTING, CONNECTED, FAILED }

    override fun onCreate() {
        super.onCreate()
        nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        adb = Fly8AdbManager.get(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SUBMIT_CODE -> {
                ensureForeground("Pairing code submitted securely.", notificationId = activeNotificationId)
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_PAIRING_CODE)?.toString()?.trim().orEmpty()
                submitCode(code)
            }
            ACTION_CANCEL -> {
                Fly8Diagnostics.record("ADB pairing", "User cancelled the wireless debugging pairing flow.")
                stopWorkflow()
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT -> {
                worker.execute {
                    runCatching { adb.disconnect() }
                    main.post {
                        publishStatus("ADB disconnected. Wireless Debugging remains available.", false)
                        stopWorkflow()
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                ensureForeground("Finding wireless debugging pairing service…")
                if (phase == Phase.FINDING_PAIRING || phase == Phase.FAILED) {
                    phase = Phase.FINDING_PAIRING
                    discoveryStartedAt = SystemClock.elapsedRealtime()
                    schedulePhaseTimeout(
                        Phase.FINDING_PAIRING, PAIRING_DISCOVERY_TIMEOUT_MS,
                        "Timed out waiting for the pairing service. Reopen “Pair device with pairing code” and try again.",
                    )
                    publishStatus("Finding wireless debugging pairing service…", false)
                    startDiscovery(SERVICE_PAIRING)
                }
            }
            ACTION_RETRY_CONNECT -> startConnectDiscovery()
        }
        return START_STICKY
    }

    private fun ensureForeground(
        message: String,
        notificationId: Int = NOTIFICATION_ID_FINDING,
        showCodeInput: Boolean = false,
    ) {
        activeNotificationId = notificationId
        val notification = buildNotification(message, showCodeInput = showCodeInput)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(notificationId, notification)
        }
        foregroundStarted = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wireless Debugging",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Pairing discovery, secure code entry, and wireless ADB connection status."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String, showCodeInput: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, REQUEST_OPEN_APP, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        val cancelPending = PendingIntent.getService(
            this, REQUEST_CANCEL,
            Intent(this, WirelessPairingService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_fly8)
            .setContentTitle("FLY8 · Wireless Debugging")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(phase != Phase.CONNECTED && phase != Phase.FAILED)
            .setOnlyAlertOnce(false)
            .setContentIntent(openPending)
            .apply { if (phase != Phase.CONNECTED) addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending) }

        if (showCodeInput && phase == Phase.ENTER_CODE) {
            val submitIntent = Intent(this, WirelessPairingService::class.java).setAction(ACTION_SUBMIT_CODE)
            val submitPending = PendingIntent.getService(
                this, REQUEST_SUBMIT_CODE, submitIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
            )
            val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
                .setLabel("Enter the 6-digit code shown on this device")
                .setAllowFreeFormInput(true)
                .setEditChoicesBeforeSending(RemoteInput.EDIT_CHOICES_BEFORE_SENDING_DISABLED)
                .build()
            val action = NotificationCompat.Action.Builder(android.R.drawable.ic_lock_lock, "Pair", submitPending)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build()
            builder.addAction(action)
                .setContentText("Pairing service found · enter the code from Android Settings.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Pairing service found at ${pairingHost.orEmpty()}:$pairingPort. Enter the six-digit code shown on your device, then tap Pair."
                    )
                )
        }

        if (phase == Phase.CONNECTED) {
            val disconnectPending = PendingIntent.getService(
                this, REQUEST_DISCONNECT,
                Intent(this, WirelessPairingService::class.java).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect ADB", disconnectPending)
        } else if (phase == Phase.FAILED && pairedHost != null) {
            val retryPending = PendingIntent.getService(
                this, REQUEST_RETRY,
                Intent(this, WirelessPairingService::class.java).setAction(ACTION_RETRY_CONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )
            builder.addAction(android.R.drawable.ic_menu_rotate, "Retry connection", retryPending)
        }
        return builder.build()
    }

    private fun updateNotification(message: String, showCodeInput: Boolean = false, alertAgain: Boolean = false) {
        val nextId = if (showCodeInput) NOTIFICATION_ID_CODE else activeNotificationId
        if (alertAgain && nextId != activeNotificationId) {
            val previous = activeNotificationId
            ensureForeground(message, nextId, showCodeInput)
            if (previous != nextId) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(previous)
        } else {
            activeNotificationId = nextId
            val notification = buildNotification(message, showCodeInput)
            if (foregroundStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        nextId,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                    )
                } else startForeground(nextId, notification)
            } else {
                startForeground(nextId, notification)
                foregroundStarted = true
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(nextId, notification)
        }
    }

    private fun startDiscovery(serviceType: String) {
        stopDiscovery()
        val manager = nsd
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Fly8Diagnostics.record("ADB pairing", "mDNS discovery started for $serviceType.")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.orEmpty().contains(serviceType, ignoreCase = true)) return
                if (resolveInFlight) return
                resolveInFlight = true
                try {
                    manager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            resolveInFlight = false
                            Fly8Diagnostics.record("ADB pairing", "mDNS resolve failed for $serviceType (code $errorCode).")
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            resolveInFlight = false
                            val host = resolved.host?.hostAddress ?: return
                            val port = resolved.port
                            if (port !in 1..65535) return
                            if (phase == Phase.FINDING_PAIRING && serviceType == SERVICE_PAIRING) {
                                pairingHost = host
                                pairingPort = port
                                phase = Phase.ENTER_CODE
                                schedulePhaseTimeout(
                                    Phase.ENTER_CODE, CODE_ENTRY_TIMEOUT_MS,
                                    "Pairing code entry expired. Reopen “Pair device with pairing code” and use the FLY8 notification.",
                                )
                                Fly8Diagnostics.record("ADB pairing", "Pairing endpoint found on port $port; code is not logged.")
                                updateNotification(
                                    "Pairing service found · enter the six-digit code from Android Settings.",
                                    showCodeInput = true,
                                    alertAgain = true,
                                )
                                publishStatus("Pairing service found. Enter the six-digit code in the FLY8 notification.", false)
                            } else if (phase == Phase.FINDING_CONNECT && serviceType == SERVICE_CONNECT) {
                                val expected = pairedHost
                                if (expected != null && !sameAddress(expected, host)) {
                                    Fly8Diagnostics.record("ADB pairing", "Ignoring connect endpoint on a different host.")
                                    return
                                }
                                stopDiscovery()
                                connectTo(host, port)
                            }
                        }
                    })
                } catch (error: Exception) {
                    resolveInFlight = false
                    Fly8Diagnostics.record("ADB pairing", "Could not resolve mDNS service.", error)
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                if (phase == Phase.ENTER_CODE) {
                    phase = Phase.FINDING_PAIRING
                    schedulePhaseTimeout(
                        Phase.FINDING_PAIRING, PAIRING_DISCOVERY_TIMEOUT_MS,
                        "Timed out waiting for the pairing service. Reopen “Pair device with pairing code” and try again.",
                    )
                    updateNotification("Pairing service disappeared. Reopen Pair device with pairing code and try again.")
                    publishStatus("Pairing service disappeared; reopen the pairing-code screen.", false)
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Fly8Diagnostics.record("ADB pairing", "mDNS discovery stopped for $serviceType.")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (discovery !== listener) {
                    Fly8Diagnostics.record("ADB pairing", "Ignored stale discovery-start failure for $serviceType.")
                    return
                }
                discovery = null
                releaseMulticastLock()
                clearPhaseTimeout()
                phase = Phase.FAILED
                val message = "Could not start wireless debugging discovery (error $errorCode). Check Wi-Fi and try again."
                Fly8Diagnostics.record("ADB pairing", message)
                updateNotification(message)
                publishStatus(message, false)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Fly8Diagnostics.record("ADB pairing", "mDNS discovery stop failed for $serviceType (code $errorCode).")
            }
        }
        discovery = listener
        try {
            acquireMulticastLock()
            manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) {
            if (discovery === listener) discovery = null
            releaseMulticastLock()
            clearPhaseTimeout()
            phase = Phase.FAILED
            val message = "Wireless debugging discovery failed: ${error.message ?: error.javaClass.simpleName}"
            Fly8Diagnostics.record("ADB pairing", message, error)
            updateNotification(message)
            publishStatus(message, false)
        }
    }

    private fun schedulePhaseTimeout(expected: Phase, timeoutMs: Long, message: String) {
        clearPhaseTimeout()
        val timeout = Runnable {
            if (phase != expected) return@Runnable
            Fly8Diagnostics.record("ADB pairing", "Timed out in phase ${expected.name}.")
            phaseTimeout = null
            fail(message)
            publishStatus(message, false)
        }
        phaseTimeout = timeout
        main.postDelayed(timeout, timeoutMs)
    }

    private fun clearPhaseTimeout() {
        phaseTimeout?.let(main::removeCallbacks)
        phaseTimeout = null
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("FLY8-wireless-adb-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
        Fly8Diagnostics.record("ADB pairing", "Wi-Fi multicast lock acquired for active mDNS discovery.")
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        multicastLock = null
        runCatching {
            if (lock.isHeld) lock.release()
        }.onFailure { error ->
            Fly8Diagnostics.record("ADB pairing", "Could not release Wi-Fi multicast lock.", error)
        }
    }

    private fun stopDiscovery() {
        val listener = discovery
        discovery = null
        resolveInFlight = false
        if (listener != null) {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        // This lock is scoped to discovery only; it is not held while pairing or connected.
        releaseMulticastLock()
    }

    private fun submitCode(code: String) {
        if (phase != Phase.ENTER_CODE) {
            publishStatus("No active pairing service was found. Open Pair device with pairing code first.", false)
            return
        }
        val host = pairingHost
        val port = pairingPort
        if (host.isNullOrBlank() || port !in 1..65535) {
            fail("Pairing endpoint expired. Reopen Pair device with pairing code.")
            return
        }
        if (!code.matches(Regex("\\d{6}"))) {
            updateNotification("Pairing code must contain exactly six digits. Wait for the pairing service and submit the code again.", showCodeInput = true)
            publishStatus("Pairing code must contain six digits.", false)
            return
        }
        clearPhaseTimeout()
        phase = Phase.PAIRING
        stopDiscovery()
        updateNotification("Authenticating the pairing code…")
        publishStatus("Pairing with the discovered wireless debugging endpoint…", false)
        worker.execute {
            try {
                adb.pair(host, port, code)
                Fly8Diagnostics.record("ADB pairing", "Pairing handshake completed on endpoint port $port.")
                pairedHost = host
                main.post {
                    phase = Phase.FINDING_CONNECT
                    schedulePhaseTimeout(
                        Phase.FINDING_CONNECT, CONNECT_DISCOVERY_TIMEOUT_MS,
                        "Pairing succeeded, but the normal ADB service was not found in time. Reopen Wireless Debugging and retry.",
                    )
                    updateNotification("Paired. Finding the authenticated ADB connection service…")
                    publishStatus("Pairing succeeded. Finding the normal ADB connection endpoint…", false)
                    startDiscovery(SERVICE_CONNECT)
                }
            } catch (error: Throwable) {
                Fly8Diagnostics.record("ADB pairing", "Pairing handshake failed.", error)
                main.post { fail(connectionMessage(error)) }
            }
        }
    }

    private fun startConnectDiscovery() {
        phase = Phase.FINDING_CONNECT
        schedulePhaseTimeout(
            Phase.FINDING_CONNECT, CONNECT_DISCOVERY_TIMEOUT_MS,
            "Could not find the normal ADB service in time. Reopen Wireless Debugging and retry.",
        )
        updateNotification("Finding the authenticated ADB connection service…")
        startDiscovery(SERVICE_CONNECT)
    }

    private fun connectTo(host: String, port: Int) {
        clearPhaseTimeout()
        phase = Phase.CONNECTING
        updateNotification("Connecting securely to wireless ADB…")
        publishStatus("Connecting securely to $host:$port…", false)
        worker.execute {
            try {
                val connected = adb.connect(host, port)
                if (!connected || !adb.isConnected) {
                    throw IOException("The ADB connection did not reach an authenticated connected state.")
                }
                main.post {
                    clearPhaseTimeout()
                    phase = Phase.CONNECTED
                    val message = "Wireless ADB connected to $host:$port."
                    Fly8Diagnostics.record("ADB pairing", "Authenticated ADB connection verified on port $port.")
                    updateNotification(message)
                    publishStatus(message, true)
                }
            } catch (error: Throwable) {
                Fly8Diagnostics.record("ADB pairing", "Authenticated ADB connection failed.", error)
                main.post {
                    pairedHost = host
                    clearPhaseTimeout()
                    phase = Phase.FAILED
                    val message = "Paired, but connection failed: ${connectionMessage(error)}"
                    updateNotification(message)
                    publishStatus(message, false)
                }
            }
        }
    }

    private fun fail(message: String) {
        clearPhaseTimeout()
        stopDiscovery()
        phase = Phase.FAILED
        Fly8Diagnostics.record("ADB pairing", message)
        updateNotification(message)
        publishStatus(message, false)
    }

    private fun connectionMessage(error: Throwable): String = when (error) {
        is java.net.ConnectException -> "Could not reach the endpoint. Check Wi-Fi and retry."
        is SecurityException -> "Android rejected the network operation. Check Wi-Fi and notification permissions."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "The wireless ADB operation failed."
    }

    private fun sameAddress(first: String, second: String): Boolean {
        return runCatching {
            InetAddress.getByName(first).hostAddress == InetAddress.getByName(second).hostAddress
        }.getOrDefault(first == second)
    }

    private fun publishStatus(message: String, connected: Boolean) {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, message)
                .putExtra(EXTRA_CONNECTED, connected),
        )
    }

    private fun stopWorkflow() {
        clearPhaseTimeout()
        stopDiscovery()
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        clearPhaseTimeout()
        stopDiscovery()
        releaseMulticastLock()
        worker.shutdownNow()
        if (phase != Phase.CONNECTED) runCatching { adb.disconnect() }
        super.onDestroy()
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    companion object {
        const val ACTION_START = "com.fl8y.lightcloner.adb.START_PAIRING"
        const val ACTION_STATUS = "com.fl8y.lightcloner.adb.PAIRING_STATUS"
        const val ACTION_SUBMIT_CODE = "com.fl8y.lightcloner.adb.SUBMIT_CODE"
        const val ACTION_CANCEL = "com.fl8y.lightcloner.adb.CANCEL_PAIRING"
        const val ACTION_DISCONNECT = "com.fl8y.lightcloner.adb.DISCONNECT"
        const val ACTION_RETRY_CONNECT = "com.fl8y.lightcloner.adb.RETRY_CONNECT"
        const val EXTRA_STATUS = "status"
        const val EXTRA_CONNECTED = "connected"
        private const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"
        private const val SERVICE_CONNECT = "_adb-tls-connect._tcp"
        private const val CHANNEL_ID = "fly8_wireless_debugging"
        private const val KEY_PAIRING_CODE = "fly8_pairing_code"
        private const val NOTIFICATION_ID_FINDING = 8041
        private const val NOTIFICATION_ID_CODE = 8042
        private const val REQUEST_OPEN_APP = 8043
        private const val REQUEST_CANCEL = 8044
        private const val REQUEST_SUBMIT_CODE = 8045
        private const val REQUEST_DISCONNECT = 8046
        private const val REQUEST_RETRY = 8047
        private const val PAIRING_DISCOVERY_TIMEOUT_MS = 180_000L
        private const val CODE_ENTRY_TIMEOUT_MS = 120_000L
        private const val CONNECT_DISCOVERY_TIMEOUT_MS = 30_000L
    }
}
