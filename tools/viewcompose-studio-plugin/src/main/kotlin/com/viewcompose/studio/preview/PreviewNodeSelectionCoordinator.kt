package com.viewcompose.studio.preview

internal class PreviewNodeSelectionCoordinator(
    snapshot: StudioPreviewRenderSnapshot,
    initialNodeId: String?,
    private val onSelectionChanged: (String?) -> Unit,
) {
    private val index = PreviewRuntimeNodeIndex.from(snapshot)
    private val consumers = mutableListOf<(String?) -> Unit>()
    private var publishing = false

    var selectedNodeId: String? = initialNodeId?.takeIf(index::contains)
        private set

    fun register(consumer: (String?) -> Unit) {
        consumers += consumer
        consumer(selectedNodeId)
    }

    fun select(nodeId: String?) {
        val normalized = nodeId?.takeIf(index::contains)
        if (publishing || normalized == selectedNodeId) return
        selectedNodeId = normalized
        onSelectionChanged(normalized)
        publishing = true
        try {
            consumers.toList().forEach { consumer -> consumer(normalized) }
        } finally {
            publishing = false
        }
    }

    fun selectSource(
        filePath: String,
        line: Int,
    ) {
        select(index.findNodeId(filePath, line))
    }
}
