package com.viewcompose.studio.preview

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbService
import com.intellij.openapi.project.Project
import java.util.concurrent.TimeUnit

internal data class StudioAndroidDevice(
    val serialNumber: String,
    val displayName: String,
    val androidVersion: String?,
    val emulator: Boolean,
    private val shellExecutor: (String) -> String,
) {
    fun shell(command: String): String = shellExecutor(command)
}

internal class DeviceDslAdbBridge(
    private val project: Project,
) {
    fun onlineDevices(): List<StudioAndroidDevice> {
        val bridge = AdbService.getInstance()
            .getDebugBridge(project)
            .get(ADB_BRIDGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return bridge.devices
            .asSequence()
            .filter(IDevice::isOnline)
            .map(::toStudioDevice)
            .sortedWith(
                compareBy<StudioAndroidDevice> { device -> device.emulator }
                    .thenBy(StudioAndroidDevice::displayName)
                    .thenBy(StudioAndroidDevice::serialNumber),
            )
            .toList()
    }

    private fun toStudioDevice(device: IDevice): StudioAndroidDevice {
        val manufacturer = device.propertyOrNull(IDevice.PROP_DEVICE_MANUFACTURER)
        val model = device.propertyOrNull(IDevice.PROP_DEVICE_MODEL)
        val displayName = when {
            manufacturer != null && model != null -> "$manufacturer $model"
            model != null -> model
            else -> device.serialNumber
        }
        return StudioAndroidDevice(
            serialNumber = device.serialNumber,
            displayName = displayName,
            androidVersion = device.propertyOrNull(IDevice.PROP_BUILD_VERSION),
            emulator = device.isEmulator,
            shellExecutor = { command -> device.executeAndCollect(command) },
        )
    }
}

internal fun readDeviceDslSourceReport(
    device: StudioAndroidDevice,
): StudioDeviceDslSourceReport {
    val foregroundPackage = parseForegroundPackage(
        activityDump = device.shell("dumpsys activity activities"),
        windowDump = { device.shell("dumpsys window windows") },
    ) ?: throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.ForegroundAppMissing)
    require(foregroundPackage.matches(ANDROID_PACKAGE_NAME)) {
        "Android reported an invalid foreground package name."
    }
    val processIds = parseProcessIds(device.shell("pidof $foregroundPackage"))
    if (processIds.isEmpty()) {
        throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.StaleReport)
    }
    val reportText = device.shell(
        "run-as $foregroundPackage cat $DEVICE_DSL_SOURCE_REPORT_PATH",
    )
    val report = runCatching { parseDeviceDslSourceReport(reportText) }
        .getOrElse {
            throw DeviceDslLocateFailure(
                reason = DeviceDslLocateFailureReason.ReportUnavailable,
                cause = it,
            )
        }
    if (report.packageName != foregroundPackage || report.processId !in processIds) {
        throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.StaleReport)
    }
    return report
}

internal fun parseForegroundPackage(
    activityDump: String,
    windowDump: () -> String,
): String? {
    ACTIVITY_FOREGROUND_MARKERS.forEach { marker ->
        activityDump.lineSequence()
            .firstOrNull { line -> marker in line }
            ?.let(::parsePackageFromComponentLine)
            ?.let { packageName -> return packageName }
    }
    return windowDump().lineSequence()
        .filter { line -> WINDOW_FOREGROUND_MARKERS.any(line::contains) }
        .mapNotNull(::parsePackageFromComponentLine)
        .firstOrNull()
}

internal fun parseProcessIds(output: String): Set<Int> {
    return output
        .splitToSequence(Regex("\\s+"))
        .mapNotNull(String::toIntOrNull)
        .filter { processId -> processId > 0 }
        .toSet()
}

private fun parsePackageFromComponentLine(line: String): String? {
    return COMPONENT_PATTERN.find(line)?.groupValues?.get(1)
}

private fun IDevice.propertyOrNull(name: String): String? {
    return runCatching { getProperty(name) }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
}

private fun IDevice.executeAndCollect(command: String): String {
    val receiver = CollectingOutputReceiver()
    executeShellCommand(command, receiver, SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    return receiver.output.orEmpty().trim()
}

internal class DeviceDslLocateFailure(
    val reason: DeviceDslLocateFailureReason,
    cause: Throwable? = null,
) : RuntimeException(cause)

internal enum class DeviceDslLocateFailureReason {
    NoDevice,
    ForegroundAppMissing,
    ReportUnavailable,
    StaleReport,
    NoVisibleDsl,
    SourceMissing,
}

private val COMPONENT_PATTERN = Regex(
    "([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+",
)
private val ANDROID_PACKAGE_NAME = Regex(
    "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+",
)
private val ACTIVITY_FOREGROUND_MARKERS = listOf(
    "topResumedActivity=",
    "mResumedActivity:",
    "ResumedActivity:",
)
private val WINDOW_FOREGROUND_MARKERS = listOf(
    "mCurrentFocus=",
    "mFocusedApp=",
)
private const val ADB_BRIDGE_TIMEOUT_SECONDS = 15L
private const val SHELL_TIMEOUT_SECONDS = 10L
