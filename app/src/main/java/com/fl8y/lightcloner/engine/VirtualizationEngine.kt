package com.fl8y.lightcloner.engine

/**
 * Contract implemented by the real VirtualApp-derived runtime.
 *
 * The implementation must query the runtime for package/user/process facts;
 * it must never synthesize "running", Recent Apps task, or preview state.
 */
interface VirtualizationEngine {

    /** Starts/checks the real engine and Binder services. */
    fun initialize(): Result<Unit>

    /** True only when this process completed runtime bootstrap. */
    fun isInitialized(): Boolean

    /** Packages installed in the virtual package manager. */
    fun installedPackages(): List<ClonePackage>

    /** Imports the package into the virtual package manager. */
    fun install(packageSource: String): Result<ClonePackage>

    /** Removes the package and all FLY8-managed virtual instances of it. */
    fun uninstall(packageName: String): Result<Unit>

    /** Creates an independently user-isolated virtual instance. */
    fun createInstance(packageName: String): Result<CloneSession>

    /** Launches the actual guest Activity through the virtual ActivityManager. */
    fun launch(instanceId: String): Result<Unit>

    /** Requests termination of the real guest process for this instance. */
    fun stop(instanceId: String): Result<Unit>

    /** Re-queries installed users and process state from the virtualization engine. */
    fun sessions(): List<CloneSession>
}

data class ClonePackage(
    val packageName: String,
    val label: String,
    val installedInstanceCount: Int = 0,
)

data class CloneSession(
    /** Stable FLY8-owned instance identifier; not an Android system user ID. */
    val instanceId: String,
    val packageName: String,
    val label: String,
    /** VirtualApp user ID. This is not an Android OS user/profile ID. */
    val virtualUserId: Int,
    val state: State,
    /** null means the runtime has not established this fact. */
    val taskVisible: Boolean?,
    /** null means snapshot availability has not been inspected. */
    val previewAvailable: Boolean?,
) {
    enum class State {
        INSTALLED,
        STARTING,
        RUNNING,
        BACKGROUND,
        STOPPING,
        STOPPED,
        FAILED,
    }
}
