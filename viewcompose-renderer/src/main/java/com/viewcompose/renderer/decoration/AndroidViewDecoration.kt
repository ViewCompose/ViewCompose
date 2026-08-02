package com.viewcompose.renderer.decoration

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.R
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.modifier.InnerShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import java.util.IdentityHashMap
import java.util.ServiceLoader

/**
 * Platform-neutral shadow declarations translated into an Android decoration request.
 *
 * The renderer owns this small contract; rasterization and shadow caches live in an optional
 * backend module. Keeping the request immutable also lets modifier patching skip unchanged work.
 *
 * @property dropShadows outer shadows in declaration order; an empty list disables that plane
 * @property innerShadows inset shadows in declaration order; an empty list disables that plane
 * @property defaultShape shape used when an individual shadow does not provide one
 * @property density immutable density snapshot used to convert all request dimensions to pixels
 */
data class AndroidViewDecorationRequest(
    val dropShadows: List<DropShadowModifierElement>,
    val innerShadows: List<InnerShadowModifierElement>,
    val defaultShape: UiShape?,
    val density: UiDensity,
) {
    /** Returns whether the request needs neither the behind-child nor over-child drawing plane. */
    val isEmpty: Boolean
        get() = dropShadows.isEmpty() && innerShadows.isEmpty()
}

/**
 * Describes which parent drawing planes an [AndroidViewDecorationBackend] uses for one child.
 *
 * @property behindChild whether [AndroidViewDecorationBackend.drawBehindChild] must be dispatched
 * @property overChild whether [AndroidViewDecorationBackend.drawOverChild] must be dispatched
 */
data class AndroidViewDecorationPresence(
    val behindChild: Boolean,
    val overChild: Boolean,
) {
    /** Returns whether the backend needs no parent drawing callback for this child. */
    val isEmpty: Boolean
        get() = !behindChild && !overChild

    /** Canonical decoration-presence constants. */
    companion object {
        /** Presence value for a request that produces no visible decoration. */
        val None = AndroidViewDecorationPresence(
            behindChild = false,
            overChild = false,
        )
    }
}

/**
 * Optional Android drawing backend for effects that cannot be expressed by ordinary View state.
 *
 * Implementations are process-scoped and are called on the UI thread during binding or parent
 * drawing. They must retain per-View resources weakly or release them from [clear], avoid changing
 * child layout, and keep expensive rasterization caches outside the renderer module.
 */
interface AndroidViewDecorationBackend {
    /**
     * Applies the latest immutable decoration [request] to [view].
     *
     * Repeated calls replace the complete request. The returned presence controls which drawing
     * callbacks the parent dispatches and must describe the state installed by this call.
     *
     * @param view mounted child whose decoration state is being replaced
     * @param request complete current decoration request
     * @return parent drawing planes required to display the applied decoration
     */
    fun update(
        view: View,
        request: AndroidViewDecorationRequest,
    ): AndroidViewDecorationPresence

    /**
     * Releases all decoration state owned for [view].
     *
     * Implementations must make this operation idempotent because backend replacement and View
     * disposal can converge on the same cleanup path.
     *
     * @param view child whose backend resources must be released
     */
    fun clear(view: View)

    /**
     * Draws the behind-child plane immediately before the parent draws [child].
     *
     * @param canvas parent canvas in [parent] coordinates
     * @param parent direct parent dispatching the draw
     * @param child decorated direct child
     */
    fun drawBehindChild(
        canvas: Canvas,
        parent: ViewGroup,
        child: View,
    )

    /**
     * Draws the over-child plane immediately after the parent draws [child].
     *
     * @param canvas parent canvas in [parent] coordinates
     * @param parent direct parent dispatching the draw
     * @param child decorated direct child
     */
    fun drawOverChild(
        canvas: Canvas,
        parent: ViewGroup,
        child: View,
    )
}

/**
 * Process-level backend registry with optional ServiceLoader discovery.
 *
 * A project that does not package a backend never loads shadow classes and keeps the no-op path.
 * Applications may call [install] to avoid discovery or replace the selected implementation.
 */
object AndroidViewDecorationRuntime {
    private val lock = Any()
    @Volatile
    private var installedBackend: AndroidViewDecorationBackend? = null
    @Volatile
    private var discoveryAttempted: Boolean = false

    /**
     * Installs [backend] as the process-wide decoration implementation.
     *
     * This operation is synchronized and replaces the implementation used by future updates.
     * Existing decorated Views switch when their next request is bound; callers should install a
     * backend during application initialization, before rendering decorated content.
     *
     * @sample com.viewcompose.renderer.samples.installDecorationBackendSample
     * @param backend process-wide implementation to use instead of service discovery
     */
    fun install(backend: AndroidViewDecorationBackend) {
        synchronized(lock) {
            installedBackend = backend
            discoveryAttempted = true
        }
    }

    /**
     * Returns whether an installed or service-discovered backend is available.
     *
     * The first call may perform one process-local [ServiceLoader] lookup; failures are treated as
     * an absent optional backend and are not retried.
     *
     * @return `true` when decoration requests can be rendered
     */
    fun hasBackend(): Boolean = backendOrNull() != null

    internal fun resetForTests() {
        synchronized(lock) {
            installedBackend = null
            discoveryAttempted = false
        }
    }

    internal fun update(
        view: View,
        request: AndroidViewDecorationRequest,
    ) {
        val previous = applied(view)
        val nextBackend = if (request.isEmpty) null else backendOrNull()
        if (previous?.backend !== nextBackend) {
            previous?.backend?.clear(view)
        }
        val next = nextBackend?.let { backend ->
            val presence = backend.update(view, request)
            if (presence.isEmpty) null else AppliedDecoration(backend, presence)
        }
        if (previous == next) return
        view.setTag(R.id.viewcompose_applied_decoration, next)
        ((view.parent as? ViewGroup)?.getTag(R.id.viewcompose_decoration_parent_drawing)
            as? ViewDecorationDrawing)?.update(view, next)
        view.invalidate()
        (view.parent as? View)?.invalidate()
    }

    internal fun applied(view: View): AppliedDecoration? {
        return view.getTag(R.id.viewcompose_applied_decoration) as? AppliedDecoration
    }

    private fun backendOrNull(): AndroidViewDecorationBackend? {
        installedBackend?.let { return it }
        if (discoveryAttempted) return null
        synchronized(lock) {
            installedBackend?.let { return it }
            if (!discoveryAttempted) {
                installedBackend = runCatching {
                    ServiceLoader.load(
                        AndroidViewDecorationBackend::class.java,
                        AndroidViewDecorationBackend::class.java.classLoader,
                    ).firstOrNull()
                }.getOrNull()
                discoveryAttempted = true
            }
            return installedBackend
        }
    }
}

internal data class AppliedDecoration(
    val backend: AndroidViewDecorationBackend,
    val presence: AndroidViewDecorationPresence,
)

/**
 * Per-parent active decoration index.
 *
 * The common no-decoration draw path is one boolean branch. It performs no child tag lookup and no
 * backend call. When decorations are active, one identity lookup per child is reused by both
 * drawing planes and selects only decorated children.
 */
internal class ViewDecorationDrawing(
    private val parent: ViewGroup,
) {
    private var decoratedChildren: IdentityHashMap<View, AppliedDecoration>? = null
    var hasDecoratedChildren: Boolean = false
        private set

    init {
        parent.setTag(R.id.viewcompose_decoration_parent_drawing, this)
    }

    fun onViewAdded(child: View) {
        update(child, AndroidViewDecorationRuntime.applied(child))
    }

    fun onViewRemoved(child: View) {
        update(child, null)
    }

    fun drawBehindChild(
        canvas: Canvas,
        child: View,
        decoration: AppliedDecoration,
    ) {
        if (!decoration.presence.behindChild) return
        decoration.backend.drawBehindChild(canvas, parent, child)
    }

    fun drawOverChild(
        canvas: Canvas,
        child: View,
        decoration: AppliedDecoration,
    ) {
        if (!decoration.presence.overChild) return
        decoration.backend.drawOverChild(canvas, parent, child)
    }

    fun decorationOrNull(child: View): AppliedDecoration? {
        return decoratedChildren?.get(child)
    }

    internal fun update(
        child: View,
        decoration: AppliedDecoration?,
    ) {
        if (decoration == null) {
            decoratedChildren?.remove(child)
        } else {
            val children = decoratedChildren
                ?: IdentityHashMap<View, AppliedDecoration>().also { decoratedChildren = it }
            children[child] = decoration
        }
        hasDecoratedChildren = decoratedChildren?.isNotEmpty() == true
        if (!hasDecoratedChildren) {
            decoratedChildren = null
        }
        parent.invalidate()
    }
}
