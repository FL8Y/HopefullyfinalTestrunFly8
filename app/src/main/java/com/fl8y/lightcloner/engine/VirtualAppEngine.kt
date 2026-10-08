package com.fl8y.lightcloner.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.util.Log
import com.lody.virtual.client.core.InstallStrategy
import com.lody.virtual.client.core.VirtualCore
import com.lody.virtual.client.ipc.VActivityManager
import com.lody.virtual.os.VUserManager
import com.lody.virtual.remote.InstalledAppInfo
import java.io.File
import java.util.UUID

/**
 * Adapter around the pinned VirtualApp-derived runtime.
 *
 * Package install/launch operations are delegated to the engine's actual
 * package manager and ActivityManager. IDs use VirtualApp user IDs, never
 * Android's real system user IDs or shell commands such as "pm --user".
 */
class VirtualAppEngine(context: Context) : VirtualizationEngine {

    private val appContext = context.applicationContext

    @Volatile
    private var servicesReady = false

    override fun initialize(): Result<Unit> = runCatching {
        Fly8VirtualRuntime.startEngineServices().getOrThrow()
        servicesReady = true
        Log.i(TAG, "Virtual package/activity services are responsive.")
    }.onFailure {
        servicesReady = false
        Log.e(TAG, "Virtual engine initialization failed.", it)
    }

    override fun isInitialized(): Boolean =
        servicesReady && Fly8VirtualRuntime.isBootstrapped()

    override fun installedPackages(): List<ClonePackage> {
        requireReady()
        val core = VirtualCore.get()
        return core.getInstalledApps(0)
            .orEmpty()
            .distinctBy { it.packageName }
            .map { info ->
                val users = runCatching { info.installedUsers }.getOrDefault(intArrayOf())
                ClonePackage(
                    packageName = info.packageName,
                    label = resolveLabel(info, users.firstOrNull() ?: DEFAULT_USER),
                    installedInstanceCount = users.size,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    override fun install(packageSource: String): Result<ClonePackage> = runCatching {
        requireReady()
        val apk = File(packageSource)
        require(apk.isFile && apk.canRead() && apk.length() > 0L) {
            "The selected APK cannot be read."
        }

        val core = VirtualCore.get()
        val installResult = core.installPackage(
            apk.absolutePath,
            InstallStrategy.UPDATE_IF_EXIST
        )

        check(installResult != null && installResult.isSuccess) {
            installResult?.error?.takeIf { it.isNotBlank() }
                ?: "The virtualization engine rejected the APK."
        }

        val packageName = installResult.packageName
            ?.takeIf { it.isNotBlank() }
            ?: error("The engine reported success but returned no package name.")

        val info = core.getInstalledAppInfo(packageName, 0)
            ?: error("The engine did not register the installed package.")

        val installedUsers = info.installedUsers ?: intArrayOf()
        if (DEFAULT_USER !in installedUsers) {
            check(core.installPackageAsUser(DEFAULT_USER, packageName)) {
                "The package was installed, but could not be enabled for the default virtual user."
            }
        }

        val refreshed = core.getInstalledAppInfo(packageName, 0)
            ?: error("The installed package disappeared from the virtual package manager.")

        Log.i(TAG, "Virtual package installed: $packageName")
        ClonePackage(
            packageName = packageName,
            label = resolveLabel(refreshed, DEFAULT_USER),
            installedInstanceCount = refreshed.installedUsers?.size ?: 0,
        )
    }.onFailure {
        Log.e(TAG, "Virtual package installation failed.", it)
    }

    override fun uninstall(packageName: String): Result<Unit> = runCatching {
        requireReady()
        require(packageName.isNotBlank()) { "Package name is required." }

        val core = VirtualCore.get()
        val info = core.getInstalledAppInfo(packageName, 0)
            ?: error("Package is not installed in the virtual package manager.")

        val userManager = VUserManager.get()
        val managedProfiles = info.installedUsers
            .orEmpty()
            .filter { userId ->
                userId != DEFAULT_USER &&
                    userManager.getUserInfo(userId)?.name?.startsWith(profilePrefix(packageName)) == true
            }

        check(core.uninstallPackage(packageName)) {
            "The virtual package manager could not uninstall $packageName."
        }

        // Only remove non-default virtual users explicitly created by FLY8 for
        // this package. Never remove a user's unrelated virtual profile.
        managedProfiles.forEach { userId ->
            if (!userManager.removeUser(userId)) {
                Log.w(TAG, "Package was removed, but virtual profile $userId remains.")
            }
        }
        Log.i(TAG, "Virtual package uninstalled: $packageName")
    }.onFailure {
        Log.e(TAG, "Virtual package removal failed for $packageName.", it)
    }

    override fun createInstance(packageName: String): Result<CloneSession> = runCatching {
        requireReady()
        require(packageName.isNotBlank()) { "Package name is required." }

        val core = VirtualCore.get()
        val info = core.getInstalledAppInfo(packageName, 0)
            ?: error("Install the package in FLY8 before creating a clone.")

        val userManager = VUserManager.get()
        val token = UUID.randomUUID().toString().take(8)
        val displayName = profilePrefix(packageName) + token
        val virtualUser = userManager.createUser(displayName, 0)
            ?: error("VirtualApp could not create an isolated virtual user.")

        if (!core.installPackageAsUser(virtualUser.id, packageName)) {
            userManager.removeUser(virtualUser.id)
            error("The package could not be registered for the new virtual user.")
        }

        val refreshed = core.getInstalledAppInfo(packageName, 0)
            ?: error("The virtual package metadata disappeared during cloning.")

        if (virtualUser.id !in (refreshed.installedUsers ?: intArrayOf())) {
            userManager.removeUser(virtualUser.id)
            error("The engine did not confirm package registration for virtual user ${virtualUser.id}.")
        }

        val session = sessionFor(refreshed, virtualUser.id)
        Log.i(TAG, "Created virtual instance: package=$packageName user=${virtualUser.id}")
        session
    }.onFailure {
        Log.e(TAG, "Could not create virtual instance for $packageName.", it)
    }

    override fun launch(instanceId: String): Result<Unit> = runCatching {
        requireReady()
        val (packageName, virtualUserId) = parseInstanceId(instanceId)
        val core = VirtualCore.get()
        val info = core.getInstalledAppInfo(packageName, 0)
            ?: error("The package is no longer installed in the virtual environment.")

        check(virtualUserId in (info.installedUsers ?: intArrayOf())) {
            "The package is not installed for virtual user $virtualUserId."
        }

        val launchIntent = core.getLaunchIntent(packageName, virtualUserId)
            ?: error("No enabled launcher Activity was found for $packageName.")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = VActivityManager.get().startActivity(launchIntent, virtualUserId)

        check(result >= 0) {
            "Virtual ActivityManager rejected the launch (result=$result, package=$packageName, virtualUser=$virtualUserId)."
        }
        Log.i(TAG, "Virtual ActivityManager accepted launch request: package=$packageName user=$virtualUserId result=$result")
    }.onFailure {
        Log.e(TAG, "Virtual app launch failed for instance=$instanceId.", it)
    }

    override fun stop(instanceId: String): Result<Unit> = runCatching {
        requireReady()
        val (packageName, virtualUserId) = parseInstanceId(instanceId)
        val core = VirtualCore.get()

        check(core.isAppInstalledAsUser(virtualUserId, packageName)) {
            "The package is not installed for virtual user $virtualUserId."
        }

        core.killApp(packageName, virtualUserId)

        // A stop request is not reported as completed until the runtime's own
        // process manager reports that the guest process is no longer running.
        var stopped = false
        repeat(STOP_CONFIRM_ATTEMPTS) {
            if (!core.isAppRunning(packageName, virtualUserId)) {
                stopped = true
                return@repeat
            }
            Thread.sleep(STOP_CONFIRM_DELAY_MS)
        }
        check(stopped || !core.isAppRunning(packageName, virtualUserId)) {
            "The runtime still reports the guest process as running after the stop request."
        }
        Log.i(TAG, "Virtual process stop confirmed: package=$packageName user=$virtualUserId")
    }.onFailure {
        Log.e(TAG, "Could not stop virtual instance=$instanceId.", it)
    }

    override fun sessions(): List<CloneSession> {
        requireReady()
        val core = VirtualCore.get()
        val userManager = VUserManager.get()

        return core.getInstalledApps(0)
            .orEmpty()
            .flatMap { info ->
                val users = info.installedUsers.orEmpty()
                users.mapNotNull { userId ->
                    if (!core.isAppInstalledAsUser(userId, info.packageName)) {
                        return@mapNotNull null
                    }
                    sessionFor(
                        info,
                        userId,
                        userName = userManager.getUserInfo(userId)?.name
                    )
                }
            }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.virtualUserId }))
    }

    private fun sessionFor(
        info: InstalledAppInfo,
        userId: Int,
        userName: String? = VUserManager.get().getUserInfo(userId)?.name,
    ): CloneSession {
        val running = runCatching {
            VirtualCore.get().isAppRunning(info.packageName, userId)
        }.getOrElse {
            Log.w(TAG, "Unable to query process state for ${info.packageName} / $userId.", it)
            false
        }

        return CloneSession(
            instanceId = instanceId(info.packageName, userId),
            packageName = info.packageName,
            label = resolveLabel(info, userId) +
                if (userId == DEFAULT_USER) "" else " · $userId",
            virtualUserId = userId,
            state = if (running) CloneSession.State.RUNNING else CloneSession.State.INSTALLED,
            taskVisible = null,
            previewAvailable = null,
        )
    }

    private fun resolveLabel(info: InstalledAppInfo, userId: Int): String {
        return runCatching {
            val appInfo: ApplicationInfo = info.getApplicationInfo(userId)
                ?: throw IllegalStateException("No ApplicationInfo for virtual user $userId")
            val text = if (appInfo.labelRes != 0) {
                VirtualCore.get().getResources(info.packageName)
                    .getText(appInfo.labelRes)
                    .toString()
            } else {
                appInfo.nonLocalizedLabel?.toString()
                    ?: appInfo.loadLabel(appContext.packageManager)?.toString()
                    ?: info.packageName
            }
            text.takeIf { it.isNotBlank() } ?: info.packageName
        }.getOrElse {
            Log.w(TAG, "Could not resolve virtual label for ${info.packageName}.", it)
            info.packageName
        }
    }

    private fun requireReady() {
        check(isInitialized()) {
            Fly8VirtualRuntime.failureMessage()
                ?: "The virtual engine has not completed its Binder/service initialization."
        }
    }

    private fun instanceId(packageName: String, userId: Int): String =
        "$packageName@$userId"

    private fun parseInstanceId(instanceId: String): Pair<String, Int> {
        val splitAt = instanceId.lastIndexOf('@')
        require(splitAt > 0 && splitAt < instanceId.lastIndex) {
            "Invalid virtual instance ID."
        }
        val packageName = instanceId.substring(0, splitAt)
        val userId = instanceId.substring(splitAt + 1).toIntOrNull()
            ?: throw IllegalArgumentException("Invalid virtual user ID in instance ID.")
        require(userId >= 0) { "Virtual user ID must be non-negative." }
        return packageName to userId
    }

    private fun profilePrefix(packageName: String): String = "FLY8 Clone: $packageName:"

    companion object {
        private const val TAG = "FLY8.VirtualAppEngine"
        private const val DEFAULT_USER = 0
        private const val STOP_CONFIRM_ATTEMPTS = 8
        private const val STOP_CONFIRM_DELAY_MS = 180L
    }
}
