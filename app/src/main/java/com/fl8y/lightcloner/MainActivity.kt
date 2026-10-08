package com.fl8y.lightcloner

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
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

class MainActivity : Activity(), WirelessAdbController.Callback {

    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var adb: WirelessAdbController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(0, 0, 1)
        window.navigationBarColor = Color.rgb(0, 0, 1)

        adb = WirelessAdbController(applicationContext, this)
        setContentView(buildRoot())
        showDashboard()
    }

    override fun onDestroy() {
        adb.close()
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
            setPadding(dp(20), dp(8), dp(20), dp(90))
        }

        val scroll = ScrollView(this).apply {
            addView(content)
        }

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
        content.removeAllViews()

        addHeading("Connection")
        status = TextView(this).apply {
            setTextColor(color("626773"))
            textSize = 13f
            text = "Choose a connection method."
            setPadding(0, 0, 0, dp(16))
        }
        content.addView(status)

        val adbCard = card()
        addCardTitle(adbCard, "Wireless Debugging")
        addCardText(
            adbCard,
            "Primary ADB connection. Pair with Android Wireless Debugging or reconnect to an already-paired endpoint."
        )
        adbCard.addView(actionButton("Open Wireless Debugging") {
            runCatching {
                WirelessDebuggingLauncher.open(this)
                status.text = "Wireless Debugging settings opened."
            }.onFailure {
                status.text = "Could not open Wireless Debugging settings."
            }
        })
        adbCard.addView(actionButton("Pair with pairing code") {
            showPairDialog()
        })
        adbCard.addView(actionButton("Connect to paired device") {
            showConnectDialog()
        })
        adbCard.addView(actionButton("Auto-discover paired device") {
            adb.autoConnect()
        })
        content.addView(adbCard)

        content.addView(space(12))

        val shizukuCard = card()
        addCardTitle(shizukuCard, "Shizuku")
        addCardText(
            shizukuCard,
            "Optional connection path. It does not replace Wireless Debugging / ADB."
        )
        shizukuCard.addView(actionButton("Check Shizuku") {
            status.text = ShizukuConnection.statusText(this)
        })
        shizukuCard.addView(actionButton("Request Shizuku permission") {
            ShizukuConnection.requestPermission()
            status.text = "Waiting for Shizuku permission."
        })
        content.addView(shizukuCard)

        content.addView(space(18))
        addHeading("Virtualized Applications")

        val engineCard = card()
        addCardTitle(engineCard, "Runtime")
        addCardText(
            engineCard,
            "The clone runtime is being integrated separately. It will use real virtual package, process, activity, and task state rather than synthetic Recent Apps cards."
        )
        content.addView(engineCard)
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
                val p = port.text.toString().trim().toIntOrNull()
                if (p == null) {
                    status.text = "Pairing port is invalid."
                    return@setPositiveButton
                }
                adb.pair(host.text.toString(), p, code.text.toString())
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
                val p = port.text.toString().trim().toIntOrNull()
                if (p == null) {
                    status.text = "Connection port is invalid."
                    return@setPositiveButton
                }
                adb.connect(host.text.toString(), p)
            }
            .show()
    }

    private fun field(label: String, hint: String): EditText {
        return EditText(this).apply {
            contentDescription = label
            this.hint = hint
            setTextColor(color("FCFDFD"))
            setHintTextColor(color("626773"))
            textSize = 15f
            setPadding(dp(4), dp(12), dp(4), dp(12))
        }
    }

    override fun onStatus(message: String) {
        status.text = message
    }

    override fun onConnected(host: String, port: Int) {
        status.text = if (port > 0) {
            "ADB connected to $host:$port."
        } else {
            "ADB connected."
        }
    }

    override fun onPairingSucceeded(host: String, port: Int) {
        status.text = "ADB pairing succeeded for $host:$port."
    }

    override fun onFailure(message: String, error: Throwable?) {
        status.text = message
    }

    private fun showSessions() {
        content.removeAllViews()
        addHeading("Sessions")

        val empty = card()
        addCardTitle(empty, "No active sessions")
        addCardText(
            empty,
            "The runtime will report real virtual processes and tasks. A black Recent Apps preview alone will not be treated as a dead session."
        )
        content.addView(empty)
    }

    private fun showAbout() {
        content.removeAllViews()
        addHeading("About")

        val card = card()
        addCardTitle(card, "FLY8")
        addCardText(card, "Lightweight application virtualization and connection workspace.")
        addCardText(card, "Version 0.2.0-dev")
        addCardText(card, "Wireless Debugging / ADB is independent from the optional Shizuku path.")
        content.addView(card)

        content.addView(space(12))

        val notice = card()
        addCardTitle(notice, "Open-source components")
        addCardText(
            notice,
            "FLY8 will ship required license and attribution notices for third-party components actually integrated into the build."
        )
        content.addView(notice)
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

    private fun space(height: Int): Space {
        return Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun color(hex: String): Int =
        Color.parseColor("#$hex")
}
