package com.fl8y.lightcloner

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.fl8y.lightcloner.connection.WirelessPairingService
import com.fl8y.lightcloner.engine.VirtualAppEngine
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end tests: real guest UI/input plus private foreground notification-service startup. */
@RunWith(AndroidJUnit4::class)
class GuestVirtualizationSmokeTest {
    @Test
    fun actualGuestActivityRendersAndAcceptsInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtureApk = File(context.cacheDir, "fly8-guest-fixture.apk")
        assertTrue(
            "The CI guest APK was not staged into FLY8's private cache.",
            fixtureApk.isFile && fixtureApk.length() > 0L,
        )
        val engine = VirtualAppEngine(context)
        var instanceId: String? = null
        try {
            engine.initialize().getOrThrow()
            val installed = engine.install(fixtureApk.absolutePath).getOrThrow()
            assertEquals(FIXTURE_PACKAGE, installed.packageName)
            val instance = engine.createInstance(installed.packageName).getOrThrow()
            instanceId = instance.instanceId
            Log.i(TAG, "GUEST_SMOKE_INSTANCE_CREATED package=${installed.packageName} virtualUser=${instance.virtualUserId}")
            engine.launch(instance.instanceId).getOrThrow()

            val device = UiDevice.getInstance(instrumentation)
            val rendered = device.wait(Until.findObject(By.text(TEXT_RENDERED)), RENDER_TIMEOUT_MS)
            assertNotNull("VirtualApp accepted launch but guest UI text never became visible.", rendered)
            val inputButton = device.wait(Until.findObject(By.text(TEXT_SEND_INPUT)), INPUT_TIMEOUT_MS)
            assertNotNull("Guest UI rendered, but its input button was not visible.", inputButton)
            inputButton!!.click()
            assertTrue("Guest Activity did not reflect the click event.",
                device.wait(Until.hasObject(By.text(TEXT_INPUT_RECEIVED)), INPUT_TIMEOUT_MS))
            Log.i(TAG, "GUEST_SMOKE_PASS rendered=true input=true virtualUser=${instance.virtualUserId}")
        } finally {
            instanceId?.let { id -> runCatching { engine.stop(id).getOrThrow() } }
            runCatching { engine.uninstall(FIXTURE_PACKAGE).getOrThrow() }
            fixtureApk.delete()
        }
    }

    @Test
    fun pairingForegroundServicePublishesItsOwnNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val serviceIntent = Intent(context, WirelessPairingService::class.java)
            .setAction(WirelessPairingService.ACTION_START)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            val manager = context.getSystemService(NotificationManager::class.java)
            val deadline = SystemClock.elapsedRealtime() + NOTIFICATION_TIMEOUT_MS
            var found: Notification? = null
            while (SystemClock.elapsedRealtime() < deadline && found == null) {
                found = manager.activeNotifications.map { it.notification }.firstOrNull { notification ->
                    notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() == NOTIFICATION_TITLE
                }
                if (found == null) SystemClock.sleep(100)
            }
            assertNotNull("WirelessPairingService did not publish its FLY8 notification.", found)
            assertTrue("Notification does not report pairing discovery.",
                found!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                    .contains("Finding wireless debugging pairing service"))
            Log.i(TAG, "PAIRING_NOTIFICATION_PASS foreground=true title=true discoveryState=true")
        } finally {
            ContextCompat.startForegroundService(context,
                Intent(context, WirelessPairingService::class.java).setAction(WirelessPairingService.ACTION_CANCEL))
            SystemClock.sleep(250)
            context.stopService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "FLY8.GuestSmoke"
        private const val FIXTURE_PACKAGE = "com.fl8y.guestfixture"
        private const val TEXT_RENDERED = "FLY8 GUEST RENDERED"
        private const val TEXT_SEND_INPUT = "SEND INPUT"
        private const val TEXT_INPUT_RECEIVED = "GUEST INPUT RECEIVED"
        private const val NOTIFICATION_TITLE = "FLY8 · Wireless Debugging"
        private const val RENDER_TIMEOUT_MS = 25_000L
        private const val INPUT_TIMEOUT_MS = 7_000L
        private const val NOTIFICATION_TIMEOUT_MS = 5_000L
    }
}
