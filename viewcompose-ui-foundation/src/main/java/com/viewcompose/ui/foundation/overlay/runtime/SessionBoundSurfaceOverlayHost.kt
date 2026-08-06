package com.viewcompose.ui.foundation

/**
 * Reconciles platform surface handles by session-scoped declarative identity.
 *
 * Subclasses provide type-specific decode, show, update, and dismiss operations. Invalid or
 * differently typed requests are ignored. An unchanged request reuses its handle without an update.
 */
abstract class SessionBoundSurfaceOverlayHost<Spec : Any, Content : Any, Handle>(
    private val overlayType: OverlayType,
    private val decode: (OverlayRequest) -> Pair<Spec, Content>?,
) : OverlayHost {
    private val activeEntries = mutableMapOf<OverlayEntryId, ActiveEntry<Handle>>()

    /** Reconciles the complete desired request set for [sessionId]. */
    final override fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    ) {
        val nextEntries = requests.mapNotNull { request ->
            request.toSupportedEntry(sessionId)
        }.associateBy { it.entryId }
        val previousKeys = activeEntries.keys.filter { it.sessionId == sessionId }

        previousKeys.filter { it !in nextEntries.keys }.forEach { entryId ->
            dismiss(entryId)
        }

        nextEntries.forEach { (entryId, nextEntry) ->
            val previous = activeEntries[entryId]
            if (previous == null) {
                activeEntries[entryId] = ActiveEntry(
                    request = nextEntry.request,
                    handle = onShow(
                        entryId = entryId,
                        spec = nextEntry.spec,
                        content = nextEntry.content,
                    ),
                )
                return@forEach
            }
            if (previous.request == nextEntry.request) {
                return@forEach
            }
            onUpdate(
                handle = previous.handle,
                spec = nextEntry.spec,
                content = nextEntry.content,
            )
            previous.request = nextEntry.request
        }
    }

    /** Dismisses and forgets all surface handles owned by [sessionId]. */
    final override fun clear(sessionId: OverlaySessionId) {
        val keys = activeEntries.keys.filter { it.sessionId == sessionId }
        keys.forEach { entryId ->
            dismiss(entryId)
        }
    }

    protected abstract fun onShow(
        entryId: OverlayEntryId,
        spec: Spec,
        content: Content,
    ): Handle

    protected abstract fun onUpdate(
        handle: Handle,
        spec: Spec,
        content: Content,
    )

    protected abstract fun onDismiss(handle: Handle)

    private fun dismiss(entryId: OverlayEntryId) {
        activeEntries.remove(entryId)?.let { entry ->
            onDismiss(entry.handle)
        }
    }

    private fun OverlayRequest.toSupportedEntry(
        sessionId: OverlaySessionId,
    ): SupportedEntry<Spec, Content>? {
        if (type != overlayType) {
            return null
        }
        val (spec, content) = decode(this) ?: return null
        return SupportedEntry(
            entryId = OverlayEntryId(
                sessionId = sessionId,
                requestKey = key,
            ),
            request = copy(
                payload = spec,
                contentToken = content,
            ),
            spec = spec,
            content = content,
        )
    }

    private data class ActiveEntry<Handle>(
        var request: OverlayRequest,
        val handle: Handle,
    )

    private data class SupportedEntry<Spec : Any, Content : Any>(
        val entryId: OverlayEntryId,
        val request: OverlayRequest,
        val spec: Spec,
        val content: Content,
    )
}
