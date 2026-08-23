package com.viewcompose.studio.preview

import java.util.UUID

internal fun readDeviceAnimationTimelineReport(
    device: StudioAndroidDevice,
    mode: String,
    transitionId: String? = null,
    requestIdFactory: () -> String = ::newAnimationTimelineRequestId,
    sleep: (Long) -> Unit = Thread::sleep,
    nanoTime: () -> Long = System::nanoTime,
): StudioAnimationTimelineReport {
    require(mode == ANIMATION_TIMELINE_DISCOVER_MODE || mode == ANIMATION_TIMELINE_CAPTURE_MODE) {
        "Unsupported animation timeline request mode '$mode'."
    }
    require(mode != ANIMATION_TIMELINE_CAPTURE_MODE || isStudioTimelineIdentity(transitionId)) {
        "Animation timeline capture requires a valid transition identity."
    }
    val foregroundPackage = parseForegroundPackage(
        activityDump = device.shell("dumpsys activity activities"),
        windowDump = { device.shell("dumpsys window windows") },
    ) ?: throw AnimationTimelineInspectFailure(AnimationTimelineInspectFailureReason.ForegroundAppMissing)
    require(foregroundPackage.matches(STUDIO_ANDROID_PACKAGE_NAME)) {
        "Android reported an invalid foreground package name."
    }
    val processIds = parseProcessIds(device.shell("pidof $foregroundPackage"))
    if (processIds.isEmpty()) {
        throw AnimationTimelineInspectFailure(AnimationTimelineInspectFailureReason.StaleReport)
    }
    val requestId = requestIdFactory()
    require(requestId.matches(STUDIO_ANIMATION_REQUEST_ID)) {
        "Animation timeline request ID must be a 32-character lowercase hexadecimal nonce."
    }
    val transitionArgument = transitionId?.let { identity ->
        " --es $ANIMATION_TIMELINE_TRANSITION_ID_EXTRA $identity"
    }.orEmpty()
    val requestStartedAtNanos = nanoTime()
    device.shell(
        "am broadcast --user current -a $ANIMATION_TIMELINE_REQUEST_ACTION " +
            "-p $foregroundPackage --es $ANIMATION_TIMELINE_REQUEST_ID_EXTRA $requestId " +
            "--es $ANIMATION_TIMELINE_MODE_EXTRA $mode$transitionArgument",
    )
    var lastFailure: Throwable? = null
    var observedStaleResponse = false
    while (nanoTime() - requestStartedAtNanos < ANIMATION_RESPONSE_POLL_TIMEOUT_NANOS) {
        val reportText = device.shell(
            "run-as $foregroundPackage cat $ANIMATION_TIMELINE_REPORT_PATH",
        )
        val report = runCatching { parseAnimationTimelineReport(reportText) }
            .onFailure { error -> lastFailure = error }
            .getOrNull()
        if (report != null) {
            if (report.requestId != requestId) {
                observedStaleResponse = true
            } else {
                val unexpectedSelection = mode == ANIMATION_TIMELINE_CAPTURE_MODE &&
                    report.transitions.any { timeline -> timeline.identity != transitionId }
                if (
                    report.packageName != foregroundPackage ||
                    report.processId !in processIds ||
                    report.mode != mode ||
                    unexpectedSelection
                ) {
                    throw AnimationTimelineInspectFailure(
                        AnimationTimelineInspectFailureReason.StaleReport,
                    )
                }
                return report
            }
        }
        sleep(ANIMATION_RESPONSE_POLL_INTERVAL_MILLIS)
    }
    throw AnimationTimelineInspectFailure(
        reason = if (observedStaleResponse) {
            AnimationTimelineInspectFailureReason.StaleReport
        } else {
            AnimationTimelineInspectFailureReason.ReportUnavailable
        },
        cause = lastFailure,
    )
}

internal class AnimationTimelineInspectFailure(
    val reason: AnimationTimelineInspectFailureReason,
    cause: Throwable? = null,
) : RuntimeException(cause)

internal enum class AnimationTimelineInspectFailureReason {
    NoDevice,
    ForegroundAppMissing,
    ReportUnavailable,
    StaleReport,
    NoAnimation,
    MissingTransition,
    Busy,
}

private fun newAnimationTimelineRequestId(): String = UUID.randomUUID().toString().replace("-", "")

private fun isStudioTimelineIdentity(identity: String?): Boolean {
    return identity != null && identity.length in 1..256 &&
        identity.all { character -> character.isLetterOrDigit() || character == '-' }
}

private val STUDIO_ANDROID_PACKAGE_NAME = Regex(
    "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+",
)
private val STUDIO_ANIMATION_REQUEST_ID = Regex("[a-f0-9]{32}")
private const val ANIMATION_RESPONSE_POLL_INTERVAL_MILLIS = 50L
private const val ANIMATION_RESPONSE_POLL_TIMEOUT_NANOS = 5_000_000_000L
