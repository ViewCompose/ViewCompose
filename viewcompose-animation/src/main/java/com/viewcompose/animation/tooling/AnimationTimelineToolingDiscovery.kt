package com.viewcompose.animation.tooling

internal object AnimationTimelineToolingDiscovery {
    private val slot = AnimationTimelineToolingSlot()

    val tooling: AnimationTimelineTooling?
        get() = slot.resolve()

    fun install(tooling: AnimationTimelineTooling) {
        slot.install(tooling)
    }
}

internal class AnimationTimelineToolingSlot {
    @Volatile
    private var resolved = false

    @Volatile
    private var selected: AnimationTimelineTooling? = null

    private var candidate: AnimationTimelineTooling? = null
    private var ambiguous = false

    fun install(tooling: AnimationTimelineTooling) {
        synchronized(this) {
            if (resolved || ambiguous) return
            val current = candidate
            when {
                current == null -> candidate = tooling
                current !== tooling -> {
                    candidate = null
                    ambiguous = true
                }
            }
        }
    }

    fun resolve(): AnimationTimelineTooling? {
        if (resolved) return selected
        return synchronized(this) {
            if (!resolved) {
                selected = if (ambiguous) null else candidate
                candidate = null
                resolved = true
            }
            selected
        }
    }
}
