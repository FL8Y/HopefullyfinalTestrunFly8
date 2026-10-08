package com.fl8y.lightcloner

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.fl8y.lightcloner.connection.ShizukuConnection
import com.fl8y.lightcloner.connection.WirelessAdbController
import com.fl8y.lightcloner.connection.WirelessDebuggingLauncher
import com.fl8y.lightcloner.engine.ClonePackage
import com.fl8y.lightcloner.engine.CloneSession
import com.fl8y.lightcloner.engine.VirtualAppEngine
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity(), WirelessAdbController.Callback {

    private enum class Screen { DASHBOARD, SESSIONS, ABOUT }

    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var adb: WirelessAdbController
    private lateinit var engine: VirtualAppEngine

    private val mainHandler = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var currentScreen = Screen.DASHBOARD
    private var lastStatus = "Starting FLY8…"
    private var packages: List<ClonePackage> = emptyList()
    private var sessions: List<CloneSession> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(0, 0, 1)
        window.navigationBarColor = Color.rgb(0, 0, 1)

        adb = WirelessAdbController(applicationContext, this)
        engine = VirtualAppEngine(applicationContext)

        setContentView(buildRoot())
        showDashboard()
        refreshEngineData()
    }

    override fun onDestroy() {
        adb.close()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color("000001"))
        }

        val title = TextView(this).apply {
            text = "FLY8"
            textSize = 30f
            setTextColor(color("FCFDFD"))
            setPadding(dp(20), dp(18), dp(20), dp(8))
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }

        val scroll = ScrollView(this).apply { addView(content) }
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(color("010205"))
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }

        nav.addView(navButton("Dashboard") { showDashboard() })
        nav.addView(navButton("Sessions") { showSessions() })
        nav.addView(navButton("About") { showAbout() })

        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(64)))
        return root
    }

    private fun showDashboard() {
        currentScreen = Screen.DASHBOARD
        content.removeAllViews()
        addHeading("Connection")
        addStatusView()

        val adbCard = card()
        addCardTitle(adbCard, "Wireless Debugging")
        addCardText(
            adbCard,
            "Primary ADB connection. Pair with Android Wireless Debugging or reconnect to an already-paired endpoint."
        )
        adbCard.addView(actionButton("Open Wireless Debugging") {
            runCatching {
                WirelessDebuggingLauncher.open(this)
                setStatus("Wireless Debugging settings opened.")
            }.onFailure {
                setStatus("Could not open Wireless Debugging settings: ${it.message}")
            }
        })
        adbCard.addView(actionButton("Pair with pairing code") { showPairDialog() })
        adbCard.addView(actionButton("Connect to paired device") { showConnectDialog() })
        adbCard.addView(actionButton("Auto-discover paired device") { adb.autoConnect() })
        content.addView(adbCard)

        content.addView(space(12))
        val shizukuCard = card()
        addCardTitle(shizukuCard, "Shizuku")
        addCardText(shizukuCard, "Optional connection path. It does not replace Wireless Debugging / ADB.")
        shizukuCard.addView(actionButton("Check Shizuku") {
            setStatus(ShizukuConnection.statusText(this))
        })
        shizukuCard.addView(actionButton("Request Shizuku permission") {
            ShizukuConnection.requestPermission()
            setStatus("Shizuku permission request sent when its service is available.")
        })
        content.addView(shizukuCard)

        content.addView(space(18))
        addHeading("Virtualized Applications")
        val engineCard = card()
        addCardTitle(engineCard, "APK library")
        addCardText(
            engineCard,
            "APK imports go through the virtual package manager. Launch actions go through the virtual ActivityManager; FLY8 does not create pretend Recent Apps entries."
        )
        engineCard.addView(actionButton("Import APK") { openApkPicker() })
        engineCard.addView(actionButton("Refresh virtual apps and sessions") { refreshEngineData() })
        content.addView(engineCard)

        content.addView(space(8))
        renderPackages(content)
    }

    private fun showSessions() {
        currentScreen = Screen.SESSIONS
        content.removeAllViews()
        addHeading("Sessions")
        addStatusView()
        content.addView(actionButton("Refresh sessions") { refreshEngineData() })
        content.addView(space(8))

        if (sessions.isEmpty()) {
            val empty = card()
            addCardTitle(empty, "No virtual instances")
            addCardText(
                empty,
                "Import an APK from Dashboard. Sessions appear here only after the virtualization engine reports a real installed virtual user."
            )
            content.addView(empty)
            return
        }

        sessions.forEach { session -> content.addView(renderSession(session)) }
    }

    private fun showAbout() {
        currentScreen = Screen.ABOUT
        content.removeAllViews()
        addHeading("About")
        addStatusView()

        val card = card()
        addCardTitle(card, "FLY8")
        addCardText(card, "Lightweight application virtualization with independent Wireless Debugging / ADB and optional Shizuku routes.")
        addCardText(card, "Version 0.2.0-dev")
        addCardText(card, "Virtual process liveness, Activity/task state and Recent Apps preview availability are tracked as separate facts.")
        content.addView(card)

        content.addView(space(12))
        val notices = card()
        addCardTitle(notices, "Open-source components")
        addCardText(notices, "License notices are maintained in the source repository for third-party components actually integrated into this build.")
        content.addView(notices)
    }

    private fun renderPackages(parent: LinearLayout) {
        if (packages.isEmpty()) {
            val empty = card()
            addCardTitle(empty, "No virtual APKs installed")
            addCardText(empty, "Import a single APK to create the first virtual package. Split APK sets are not yet handled by this picker.")
            parent.addView(empty)
            return
        }

        packages.forEach { pkg ->
            val packageCard = card()
            addCardTitle(packageCard, pkg.label)
            addCardText(packageCard, pkg.packageName)
            addCardText(packageCard, "Virtual instances: ${pkg.installedInstanceCount}")
            packageCard.addView(actionButton("Create isolated instance") {
                enqueueEngineAction(
                    "Creating a virtual profile for ${pkg.label}…",
                    { engine.createInstance(pkg.packageName).map { Unit } }
                ) { refreshEngineData() }
            })
            packageCard.addView(actionButton("Remove package and its FLY8 instances") {
                AlertDialog.Builder(this)
                    .setTitle("Remove ${pkg.label}?")
                    .setMessage("This removes the package from the virtual environment, including its FLY8-managed instances and their virtual data.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        enqueueEngineAction(
                            "Removing ${pkg.packageName}…",
                            { engine.uninstall(pkg.packageName) }
                        ) { refreshEngineData() }
                    }
                    .show()
            })
            parent.addView(packageCard)
        }
    }

    private fun renderSession(session: CloneSession): View {
        val sessionCard = card()
        addCardTitle(sessionCard, session.label)
        addCardText(sessionCard, session.packageName)
        addCardText(sessionCard, "Virtual user: ${session.virtualUserId} · State: ${session.state}")
        val taskText = session.taskVisible?.let { if (it) "visible" else "not visible" } ?: "unknown"
        val previewText = session.previewAvailable?.let { if (it) "available" else "unavailable" } ?: "unknown"
        addCardText(sessionCard, "Recent Apps task: $taskText · Preview: $previewText")

        sessionCard.addView(actionButton("Launch / resume actual guest Activity") {
            enqueueEngineAction(
                "Requesting virtual Activity launch for ${session.packageName}…",
                { engine.launch(session.instanceId) }
            ) { refreshEngineData() }
        })
        sessionCard.addView(actionButton("Stop guest process") {
            enqueueEngineAction(
                "Stopping ${session.packageName} in virtual user ${session.virtualUserId}…",
                { engine.stop(session.instanceId) }
            ) { refreshEngineData() }
        })
        return sessionCard
    }

    private fun openApkPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
        }
        try {
            startActivityForResult(intent, REQUEST_PICK_APK)
        } catch (error: Exception) {
            setStatus("Could not open the system APK picker: ${error.message}")
        }
    }

    @Deprecated("Uses the compatible platform activity-result API for this minimal host.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_APK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        importApk(uri)
    }

    private fun importApk(uri: Uri) {
        enqueueEngineAction("Importing selected APK into the virtual package manager…", {
            runCatching {
                if (!engine.isInitialized()) engine.initialize().getOrThrow()
                val importDir = File(cacheDir, "fly8-apk-imports").apply { mkdirs() }
                val staged = File(importDir, "import-${UUID.randomUUID()}.apk")
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "The selected APK could not be opened." }
                        staged.outputStream().use { output -> input.copyTo(output) }
                    }
                    engine.install(staged.absolutePath).getOrThrow()
                } finally {
                    staged.delete()
                }
                Unit
            }
        }) { refreshEngineData() }
    }

    private fun refreshEngineData() {
        setStatus("Checking the real virtual runtime…")
        io.execute {
            val result = runCatching {
                if (!engine.isInitialized()) {
                    engine.initialize().getOrThrow()
                }
                val newPackages = engine.installedPackages()
                val newSessions = engine.sessions()
                newPackages to newSessions
            }
            mainHandler.post {
                result.fold(
                    onSuccess = { (newPackages, newSessions) ->
                        packages = newPackages
                        sessions = newSessions
                        setStatus("Virtual runtime ready · ${packages.size} package(s) · ${sessions.size} instance(s).")
                        when (currentScreen) {
                            Screen.DASHBOARD -> showDashboard()
                            Screen.SESSIONS -> showSessions()
                            Screen.ABOUT -> Unit
                        }
                    },
                    onFailure = { failure ->
                        setStatus("Virtual runtime unavailable: ${failure.message ?: failure.javaClass.simpleName}")
                        Log.e(TAG, "Virtual runtime refresh failed.", failure)
                    }
                )
            }
        }
    }

    private fun enqueueEngineAction(
        progressMessage: String,
        action: () -> Result<*>,
        onSuccess: () -> Unit = {},
    ) {
        setStatus(progressMessage)
        io.execute {
            val result = runCatching { action().getOrThrow() }
            mainHandler.post {
                result.fold(
                    onSuccess = {
                        setStatus("Operation completed.")
                        onSuccess()
                    },
                    onFailure = { failure ->
                        setStatus("Operation failed: ${failure.message ?: failure.javaClass.simpleName}")
                        Log.e(TAG, progressMessage, failure)
                    }
                )
            }
        }
    }

    private fun showPairDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }

        val host = field("Device IP", "192.168.1.10")
        val port = field("Pairing port", "37000")
        val code = field("6-digit pairing code", "123456").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        form.addView(host)
        form.addView(port)
        form.addView(code)

        AlertDialog.Builder(this)
            .setTitle("Pair device")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Pair") { _, _ ->
                val pairingPort = port.text.toString().trim().toIntOrNull()
                if (pairingPort == null) {
                    setStatus("Pairing port is invalid.")
                } else {
                    adb.pair(host.text.toString(), pairingPort, code.text.toString())
                }
            }
            .show()
    }

    private fun showConnectDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val host = field("Device IP", "192.168.1.10")
        val port = field("Wireless ADB port", "5555")
        form.addView(host)
        form.addView(port)

        AlertDialog.Builder(this)
            .setTitle("Connect to paired device")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Connect") { _, _ ->
                val connectPort = port.text.toString().trim().toIntOrNull()
                if (connectPort == null) {
                    setStatus("Connection port is invalid.")
                } else {
                    adb.connect(host.text.toString(), connectPort)
                }
            }
            .show()
    }

    private fun field(label: String, hintText: String): EditText {
        return EditText(this).apply {
            contentDescription = label
            hint = hintText
            setTextColor(color("FCFDFD"))
            setHintTextColor(color("626773"))
            textSize = 15f
            setPadding(dp(4), dp(12), dp(4), dp(12))
        }
    }

    override fun onStatus(message: String) = setStatus(message)

    override fun onConnected(host: String, port: Int) {
        setStatus(if (port > 0) "ADB connected to $host:$port." else "ADB connection established through discovery.")
    }

    override fun onPairingSucceeded(host: String, port: Int) {
        setStatus("ADB pairing protocol completed for $host:$port. Establish the ADB connection next.")
    }

    override fun onFailure(message: String, error: Throwable?) {
        setStatus(message)
        if (error != null) Log.e(TAG, "ADB operation failed.", error)
    }

    private fun addStatusView() {
        status = TextView(this).apply {
            text = lastStatus
            textSize = 13f
            setTextColor(color("626773"))
            setPadding(0, 0, 0, dp(16))
        }
        content.addView(status)
    }

    private fun setStatus(message: String) {
        lastStatus = message
        if (::status.isInitialized) status.text = message
    }

    private fun addHeading(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 22f
            setTextColor(color("FCFDFD"))
            setPadding(0, dp(10), 0, dp(10))
        })
    }

    private fun addCardTitle(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(color("FCFDFD"))
            setPadding(dp(16), dp(16), dp(16), dp(4))
        })
    }

    private fun addCardText(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(color("FCFDFD"))
            setPadding(dp(16), dp(4), dp(16), dp(12))
        })
    }

    private fun actionButton(label: String, click: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(color("FCFDFD"))
            setOnClickListener { click() }
            setBackgroundColor(color("176E94"))
            isAllCaps = false
            setPadding(dp(12), dp(4), dp(12), dp(4))
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
                setMargins(dp(12), dp(6), dp(12), dp(12))
            }
        }
    }

    private fun navButton(label: String, click: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(color("FCFDFD"))
            isAllCaps = false
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color("01132A"))
        }
    }

    private fun space(height: Int): Space = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun color(hex: String): Int = Color.parseColor("#$hex")

    companion object {
        private const val TAG = "FLY8.MainActivity"
        private const val REQUEST_PICK_APK = 0xF801
    }
}
