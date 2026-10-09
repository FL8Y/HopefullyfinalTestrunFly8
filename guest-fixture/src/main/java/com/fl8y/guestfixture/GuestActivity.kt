package com.fl8y.guestfixture

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Harmless CI guest for real virtual install, Activity rendering, accessibility and input. */
class GuestActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "FLY8 GUEST RENDERED"
            contentDescription = "FLY8 GUEST RENDERED"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        val input = Button(this).apply {
            text = "SEND INPUT"
            contentDescription = "SEND INPUT"
            setOnClickListener {
                status.text = "GUEST INPUT RECEIVED"
                status.contentDescription = "GUEST INPUT RECEIVED"
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            addView(status, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        setContentView(root)
    }
}
