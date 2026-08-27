package com.viewcompose.studio.preview

import java.util.Locale

internal fun StudioDeviceDslTimingSnapshot.toTopCostText(
    messages: PreviewUiMessages,
    limit: Int = 20,
): String {
    val resultText = result?.toTopCostText(messages, limit).orEmpty()
    val armed = arm ?: return resultText
    return buildString {
        append(
            messages.text(
                "deviceDsl.timing.arm",
                armed.parentSessionId,
                armed.matchedSessionId?.toString() ?: "-",
                armed.matchedPhysicalContainerToken?.toString() ?: "-",
                armed.endReason?.wireValue ?: "armed",
            ),
        )
        if (resultText.isNotEmpty()) {
            append('\n')
            append(resultText)
        }
    }
}

internal fun StudioDeviceDslTimingResult.toTopCostText(
    messages: PreviewUiMessages,
    limit: Int = 20,
): String {
    val additiveRecords = additiveRecords()
    val top = additiveRecords.take(limit)
    return buildString {
        append(messages.text("deviceDsl.timing.summary", completedFrames, records.size))
        append('\n')
        append(
            messages.text(
                "deviceDsl.timing.overhead",
                emptyPairOverheadNanos,
                attemptedClockReads,
                retainedClockReads,
            ),
        )
        append('\n')
        append(
            messages.text(
                "deviceDsl.timing.drops",
                droppedTimedNodes,
                droppedRecords,
                droppedStrings,
            ),
        )
        append('\n')
        append(
            messages.text(
                "deviceDsl.timing.terminal",
                endReason ?: "unknown",
                unsupportedDomains.joinToString().ifEmpty { "none" },
            ),
        )
        if (additiveRecords.isEmpty()) {
            append("\n\n")
            append(messages.text("deviceDsl.timing.empty"))
            return@buildString
        }
        if (top.isNotEmpty()) {
            append("\n\n")
            top.forEachIndexed { index, record ->
                if (index > 0) append('\n')
                append(index + 1)
                append(". ")
                append(record.inspectorLabel(messages))
            }
        }
        if (truncated || recordsTruncated) {
            append("\n\n")
            append(messages.text("deviceDsl.timing.truncated"))
        }
    }
}

internal fun StudioDeviceDslTimingResult.additiveRecords(): List<StudioDeviceDslTimingRecord> {
    return records
        .filter { record ->
            record.inclusion == StudioDeviceDslTimingInclusion.Self ||
                record.inclusion == StudioDeviceDslTimingInclusion.Direct
        }
        .sortedWith(
            compareByDescending<StudioDeviceDslTimingRecord>(
                StudioDeviceDslTimingRecord::durationNanos,
            ).thenBy(StudioDeviceDslTimingRecord::frameId)
                .thenBy(StudioDeviceDslTimingRecord::nodeToken),
        )
}

internal fun StudioDeviceDslTimingRecord.inspectorLabel(
    messages: PreviewUiMessages,
    source: StudioPreviewSourceLocation? = null,
): String {
    val milliseconds = String.format(Locale.US, "%.3f", durationNanos / 1_000_000.0)
    val node = nodeType ?: messages.text("deviceDsl.timing.scope")
    return buildString {
        append(milliseconds)
        append(" ms · ")
        append(phase.wireValue)
        append('/')
        append(inclusion.wireValue)
        append(" · ")
        append(node)
        append(" · frame ")
        append(frameId)
        if (repetitions > 1L) {
            append(" · ×")
            append(repetitions)
        }
        source?.let { location ->
            append(" · ")
            append(location.inspectorLabel())
        }
    }
}
