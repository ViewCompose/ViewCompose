package com.viewcompose.ui.foundation


/**
 * Identifies overlay commits owned by one render session.
 *
 * @property value stable identifier that must not be reused by simultaneously active sessions
 */
data class OverlaySessionId(
    val value: String,
)

/** Overlay surface and transient-feedback types supported by the core protocol. */
enum class OverlayType {
    Dialog,
    Snackbar,
    Toast,
    Popup,
    ModalBottomSheet,
}

/**
 * Describes one overlay that a render frame wants to keep active.
 *
 * A host scopes [key] by the committing [OverlaySessionId]. A later commit replaces the request
 * with the same scoped key and removes requests omitted by that session.
 *
 * @property key stable identity within one render session
 * @property type protocol discriminator used by specialized hosts
 * @property payload type-specific behavior and presentation options
 * @property contentToken optional type-specific content snapshot or identity token
 */
data class OverlayRequest(
    val key: String,
    val type: OverlayType,
    val payload: Any? = null,
    val contentToken: Any? = null,
)

/**
 * Reconciles declarative overlay requests with platform surfaces.
 *
 * Calls are isolated by [OverlaySessionId]. Implementations must not remove overlays owned by a
 * different session and must make repeated commits of an unchanged request idempotent.
 */
interface OverlayHost {
    /**
     * Makes [requests] the complete desired overlay set for [sessionId].
     *
     * Requests previously committed by this session but absent from [requests] must be dismissed.
     */
    fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    )

    /** Dismisses every active overlay owned by [sessionId]. */
    fun clear(sessionId: OverlaySessionId)
}

/** Default platform-independent overlay host implementations. */
object OverlayHostDefaults {
    /**
     * A host that ignores every request.
     *
     * This fallback keeps the core renderer operational when no platform overlay module is present.
     */
    val noOp: OverlayHost = object : OverlayHost {
        override fun commit(
            sessionId: OverlaySessionId,
            requests: List<OverlayRequest>,
        ) = Unit

        override fun clear(sessionId: OverlaySessionId) = Unit
    }

}

internal val LocalOverlayHost = uiLocalOf(
    debugName = "OverlayHost",
    debugValueFormatter = { host ->
        if (host === OverlayHostDefaults.noOp) "none" else host::class.qualifiedName.orEmpty()
    },
) { OverlayHostDefaults.noOp }

/** Exposes the overlay host installed for the current composition. */
object OverlayHostContext {
    /** Returns the nearest provided host, or [OverlayHostDefaults.noOp] when none is installed. */
    val current: OverlayHost
        get() = UiLocals.current(LocalOverlayHost)
}

/**
 * Provides an overlay host within the content scope.
 */
fun UiTreeBuilder.ProvideOverlayHost(
    host: OverlayHost,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalOverlayHost, host) {
        content()
    }
}
