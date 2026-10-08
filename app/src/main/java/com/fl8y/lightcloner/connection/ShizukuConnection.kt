package com.fl8y.lightcloner.connection

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuConnection {

    const val REQUEST_CODE = 0xF8

    fun isBinderAlive(): Boolean = Shizuku.pingBinder()

    fun hasPermission(): Boolean {
        return Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun statusText(context: Context): String {
        return when {
            !Shizuku.pingBinder() -> "Shizuku unavailable"
            hasPermission() -> "Shizuku connected"
            else -> "Permission required"
        }
    }
}
