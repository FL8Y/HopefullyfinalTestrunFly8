package com.fl8y.lightcloner.engine

import android.content.Context
import android.util.Log
import com.fl8y.lightcloner.Fly8Diagnostics
import com.lody.virtual.client.core.VirtualCore
import com.lody.virtual.client.stub.VASettings
import com.lody.virtual.os.VUserManager

/**
 * Boots the real VirtualApp-derived framework in the FLY8 host process.
 *
 * Bootstrap errors are retained and surfaced to the UI rather than swallowed
 * or converted into a fake "engine ready" state.
 */
object Fly8VirtualRuntime {
    private const val TAG = "FLY8.VirtualRuntime"

    @Volatile
    private var startupCompleted = false

    @Volatile
    private var initializationFailure: Throwable? = null

    @Volatile
    private var initializerDispatched = false

    fun attachBaseContext(context: Context) {
        if (startupCompleted || initializationFailure != null) return

        try {
            VASettings.ENABLE_IO_REDIRECT = true
            VASettings.ENABLE_INNER_SHORTCUT = false
            Fly8Diagnostics.record("Runtime", "VirtualCore startup requested.")
            VirtualCore.get().startup(context)
            startupCompleted = true
            Fly8Diagnostics.record("Runtime", "VirtualCore startup completed.")
            Log.i(TAG, "VirtualCore startup completed.")
        } catch (failure: Throwable) {
            initializationFailure = failure
            Fly8Diagnostics.record("Runtime", "VirtualCore startup failed.", failure)
            Log.e(TAG, "VirtualCore startup failed.", failure)
        }
    }

    fun onApplicationCreate() {
        if (!startupCompleted || initializerDispatched) return

        try {
            // FLY8 intentionally does not enable the upstream Xposed plug-in
            // or inherit upstream app-specific delegates.
            VirtualCore.get().initialize(object : VirtualCore.VirtualInitializer() {})
            initializerDispatched = true
            Fly8Diagnostics.record("Runtime", "VirtualCore process initialization callback completed.")
            Log.i(TAG, "VirtualCore process initialization callback completed.")
        } catch (failure: Throwable) {
            initializationFailure = failure
            Fly8Diagnostics.record("Runtime", "VirtualCore process initialization callback failed.", failure)
            Log.e(TAG, "VirtualCore process initialization callback failed.", failure)
        }
    }

    fun requireStartup() {
        initializationFailure?.let {
            throw IllegalStateException("Virtualization runtime bootstrap failed.", it)
        }
        check(startupCompleted) {
            "Virtualization runtime has not completed Application bootstrap."
        }
        check(initializerDispatched) {
            "Virtualization process initialization has not completed."
        }
    }

    fun startEngineServices(): Result<Unit> = runCatching {
        Fly8Diagnostics.record("Service", "Checking virtual service startup.")
        requireStartup()
        VirtualCore.get().waitForEngine()

        // Force a real Binder-backed query before reporting the engine as ready.
        val virtualUsers = VUserManager.get().users
            ?: throw IllegalStateException("Virtual user service did not return a user list.")
        if (virtualUsers.isEmpty()) {
            throw IllegalStateException("Virtual user service returned no users.")
        }

        Fly8Diagnostics.record("Service", "Virtual service answered; virtual-user count=${virtualUsers.size}.")
        Log.i(TAG, "Virtual service answered; virtual-user count=${virtualUsers.size}.")
    }.onFailure {
        Fly8Diagnostics.record("Service", "Virtual service initialization failed.", it)
    }

    fun isBootstrapped(): Boolean =
        startupCompleted && initializerDispatched && initializationFailure == null

    fun failureMessage(): String? = initializationFailure?.let {
        "${it.javaClass.simpleName}: ${it.message ?: "no message"}"
    }
}
