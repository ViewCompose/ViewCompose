package com.viewcompose.studio.preview

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbService
import com.intellij.openapi.project.Project
import java.util.concurrent.TimeUnit
import java.util.UUID

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
    requestIdFactory: () -> String = ::newDeviceDslSourceRequestId,
    sleep: (Long) -> Unit = Thread::sleep,
    nanoTime: () -> Long = System::nanoTime,
): StudioDeviceDslSourceReport {
    return requestDeviceDslReport(
        device = device,
        operation = StudioDeviceDslOperation.Source,
        requestIdFactory = requestIdFactory,
        sleep = sleep,
        nanoTime = nanoTime,
    )
}

internal fun readDeviceDslNodeReport(
    device: StudioAndroidDevice,
    sessionId: Long,
): StudioDeviceDslSourceReport {
    require(sessionId > 0L)
    return requestDeviceDslReport(
        device = device,
        operation = StudioDeviceDslOperation.Nodes,
        sessionId = sessionId,
    )
}

internal fun selectDeviceDslNode(
    device: StudioAndroidDevice,
    sessionId: Long,
    nodeToken: String,
): StudioDeviceDslHighlightResult {
    require(sessionId > 0L)
    require(nodeToken.matches(DEVICE_DSL_NODE_TOKEN))
    val report = requestDeviceDslReport(
        device = device,
        operation = StudioDeviceDslOperation.Select,
        sessionId = sessionId,
        nodeToken = nodeToken,
    )
    return report.highlight
        ?: throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.HighlightRejected)
}

internal fun clearDeviceDslHighlight(device: StudioAndroidDevice): StudioDeviceDslHighlightResult {
    val report = requestDeviceDslReport(
        device = device,
        operation = StudioDeviceDslOperation.Clear,
    )
    return report.highlight
        ?: throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.HighlightRejected)
}

internal fun readDeviceDslTimingReport(
    device: StudioAndroidDevice,
    sessionId: Long,
    phases: Set<StudioDeviceDslTimingPhase> = StudioDeviceDslTimingPhase.entries.toSet(),
): StudioDeviceDslTimingSnapshot {
    require(sessionId > 0L)
    require(phases.isNotEmpty())
    val report = requestDeviceDslReport(
        device = device,
        operation = StudioDeviceDslOperation.Timing,
        sessionId = sessionId,
        timingPhases = phases,
    )
    return report.timing
        ?: throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.TimingRejected)
}

private fun requestDeviceDslReport(
    device: StudioAndroidDevice,
    operation: StudioDeviceDslOperation,
    sessionId: Long? = null,
    nodeToken: String? = null,
    timingPhases: Set<StudioDeviceDslTimingPhase>? = null,
    requestIdFactory: () -> String = ::newDeviceDslSourceRequestId,
    sleep: (Long) -> Unit = Thread::sleep,
    nanoTime: () -> Long = System::nanoTime,
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
    val requestId = requestIdFactory()
    require(requestId.matches(DEVICE_DSL_SOURCE_REQUEST_ID)) {
        "Device DSL source request ID must be a 32-character lowercase hexadecimal nonce."
    }
    val requestStartedAtNanos = nanoTime()
    val requestCommand = buildString {
        append("am broadcast --user current -a $DEVICE_DSL_SOURCE_REQUEST_ACTION ")
        append("-p $foregroundPackage --es $DEVICE_DSL_SOURCE_REQUEST_ID_EXTRA $requestId ")
        append("--es $DEVICE_DSL_SOURCE_REQUEST_OPERATION_EXTRA ${operation.wireValue}")
        sessionId?.let { value ->
            require(value > 0L)
            append(" --el $DEVICE_DSL_SOURCE_REQUEST_SESSION_ID_EXTRA $value")
        }
        nodeToken?.let { value ->
            require(value.matches(DEVICE_DSL_NODE_TOKEN))
            append(" --es $DEVICE_DSL_SOURCE_REQUEST_NODE_TOKEN_EXTRA $value")
        }
        timingPhases?.let { phases ->
            require(phases.isNotEmpty())
            append(" --es $DEVICE_DSL_TIMING_PHASES_EXTRA ")
            append(phases.sortedBy(StudioDeviceDslTimingPhase::ordinal).joinToString(",") { phase ->
                phase.wireValue
            })
        }
    }
    device.shell(requestCommand)
    var lastFailure: Throwable? = null
    var observedStaleResponse = false
    while (nanoTime() - requestStartedAtNanos < RESPONSE_POLL_TIMEOUT_NANOS) {
        val reportText = device.shell(
            "run-as $foregroundPackage cat $DEVICE_DSL_SOURCE_REPORT_PATH",
        )
        val report = runCatching { parseDeviceDslSourceReport(reportText) }
            .onFailure { error -> lastFailure = error }
            .getOrNull()
        if (report != null) {
            if (report.requestId != requestId) {
                observedStaleResponse = true
            } else {
                if (report.packageName != foregroundPackage || report.processId !in processIds) {
                    throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.StaleReport)
                }
                if (report.operation != operation) {
                    throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.StaleReport)
                }
                return report
            }
        }
        sleep(RESPONSE_POLL_INTERVAL_MILLIS)
    }
    throw DeviceDslLocateFailure(
        reason = if (observedStaleResponse) {
            DeviceDslLocateFailureReason.StaleReport
        } else {
            DeviceDslLocateFailureReason.ReportUnavailable
        },
        cause = lastFailure,
    )
}

private fun newDeviceDslSourceRequestId(): String = UUID.randomUUID().toString().replace("-", "")

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
    NoInspectableNode,
    HighlightRejected,
    TimingRejected,
}

private val COMPONENT_PATTERN = Regex(
    "([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+",
)
private val ANDROID_PACKAGE_NAME = Regex(
    "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+",
)
private val DEVICE_DSL_SOURCE_REQUEST_ID = Regex("[a-f0-9]{32}")
private val DEVICE_DSL_NODE_TOKEN = Regex("[a-z0-9]{1,32}")
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
private const val RESPONSE_POLL_INTERVAL_MILLIS = 50L
private const val RESPONSE_POLL_TIMEOUT_NANOS = 5_000_000_000L
