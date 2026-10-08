package com.fl8y.lightcloner.engine

/**
 * Runtime contract for the virtualized application environment.
 *
 * The reference behavior requires real package, process, activity and task
 * isolation. This contract intentionally avoids fake task entries.
 */
interface VirtualizationEngine {

    fun initialize(): Result<Unit>

    fun isInitialized(): Boolean

    fun installedPackages(): List<ClonePackage>

    fun install(packageSource: String): Result<ClonePackage>

    fun uninstall(packageName: String): Result<Unit>

    fun launch(instanceId: String): Result<Unit>

    fun stop(instanceId: String): Result<Unit>

    fun sessions(): List<CloneSession>
}

data class ClonePackage(
    val packageName: String,
    val label: String,
)

data class CloneSession(
    val instanceId: String,
    val packageName: String,
    val state: State,
    val taskVisible: Boolean,
    val previewAvailable: Boolean,
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
