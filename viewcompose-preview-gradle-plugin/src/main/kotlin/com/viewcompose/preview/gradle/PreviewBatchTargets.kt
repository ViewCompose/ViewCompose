package com.viewcompose.preview.gradle

import java.io.File

internal data class PreviewBatchTarget(
    val previewId: String,
    val variantId: String,
)

internal fun File.readPreviewBatchTargets(): List<PreviewBatchTarget> {
    require(isFile) { "Preview batch target file does not exist: '$absolutePath'." }
    val targets = readLines()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapIndexed { index, line ->
            val fields = line.split('\t')
            require(fields.size == 2 && fields.none(String::isBlank)) {
                "Invalid preview batch target at line ${index + 1}."
            }
            PreviewBatchTarget(
                previewId = fields[0],
                variantId = fields[1],
            )
        }
    require(targets.isNotEmpty()) { "Preview batch target file is empty." }
    require(targets.size == targets.distinct().size) {
        "Preview batch target file contains duplicate targets."
    }
    return targets
}
