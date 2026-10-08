package com.fl8y.lightcloner

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.fl8y.lightcloner.connection.ShizukuConnection
import com.fl8y.lightcloner.connection.WirelessDebuggingLauncher

class MainActivity : Activity() {

    private lateinit var content: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(0, 0, 1)
        window.navigationBarColor = Color.rgb(0, 0, 1)

        setContentView(buildRoot())
        showDashboard()
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
        addCardText(adbCard, "Primary ADB connection path. Open Android Wireless Debugging and pair this device.")
        adbCard.addView(actionButton("Open Wireless Debugging") {
            try {
                WirelessDebuggingLauncher.open(this)
                status.text = "Android Wireless Debugging settings opened."
            } catch (e: Exception) {
                status.text = "Could not open Wireless Debugging settings."
            }
        })
        content.addView(adbCard)

        content.addView(space(12))

        val shizukuCard = card()
        addCardTitle(shizukuCard, "Shizuku")
        addCardText(shizukuCard, "Optional connection path. It does not replace Wireless Debugging.")
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
        addCardTitle(engineCard, "Engine")
        addCardText(
            engineCard,
            "The runtime engine is deliberately not faked. Package, process, activity, task, and preview behavior will be wired to the real virtualization layer."
        )
        content.addView(engineCard)
    }

    private fun showSessions() {
        content.removeAllViews()
        addHeading("Sessions")

        val empty = card()
        addCardTitle(empty, "No active sessions")
        addCardText(
            empty,
            "Once the virtualization engine is integrated, real virtual processes and tasks will appear here. A black Recent Apps preview will not by itself be treated as a dead session."
        )
        content.addView(empty)
    }

    private fun showAbout() {
        content.removeAllViews()
        addHeading("About")

        val card = card()
        addCardTitle(card, "FLY8")
        addCardText(card, "Lightweight application virtualization and connection workspace.")
        addCardText(card, "Version 0.1.0-dev")
        addCardText(card, "The project keeps connection paths independent and builds runtime status around real process, activity, and task state.")
        content.addView(card)

        content.addView(space(12))

        val notice = card()
        addCardTitle(notice, "Open-source components")
        addCardText(notice, "Third-party notices will stay with their corresponding source and build artifacts as components are integrated.")
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
