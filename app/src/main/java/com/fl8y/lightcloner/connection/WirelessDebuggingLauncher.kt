package com.fl8y.lightcloner.connection

import android.app.Activity
import android.content.Intent
import android.provider.Settings

object WirelessDebuggingLauncher {

    private const val WIRELESS_DEBUGGING_ACTION =
        "android.settings.WIRELESS_DEBUGGING_SETTINGS"

    fun open(activity: Activity) {
        val preferred = Intent(WIRELESS_DEBUGGING_ACTION)
        if (preferred.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(preferred)
            return
        }

        activity.startActivity(Intent(Settings.ACTION_DEVELOPMENT_SETTINGS))
    }
}
