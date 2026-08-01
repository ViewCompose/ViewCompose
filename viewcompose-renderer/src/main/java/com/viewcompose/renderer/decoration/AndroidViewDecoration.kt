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
 */
data class AndroidViewDecorationRequest(
    val dropShadows: List<DropShadowModifierElement>,
    val innerShadows: List<InnerShadowModifierElement>,
    val defaultShape: UiShape?,
    val density: UiDensity,
) {
    val isEmpty: Boolean
        get() = dropShadows.isEmpty() && innerShadows.isEmpty()
}

/** Describes which parent drawing planes are used by one decorated child. */
data class AndroidViewDecorationPresence(
    val behindChild: Boolean,
    val overChild: Boolean,
) {
    val isEmpty: Boolean
        get() = !behindChild && !overChild

    companion object {
        val None = AndroidViewDecorationPresence(
            behindChild = false,
            overChild = false,
        )
    }
}

/**
 * Optional Android drawing backend. Implementations must keep all heavy caches outside renderer.
 */
interface AndroidViewDecorationBackend {
    fun update(
        view: View,
        request: AndroidViewDecorationRequest,
    ): AndroidViewDecorationPresence

    fun clear(view: View)

    fun drawBehindChild(
        canvas: Canvas,
        parent: ViewGroup,
        child: View,
    )

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

    fun install(backend: AndroidViewDecorationBackend) {
        synchronized(lock) {
            installedBackend = backend
            discoveryAttempted = true
        }
    }

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
 * backend call. When decorations are active, identity lookup selects only decorated children.
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
    ) {
        if (!hasDecoratedChildren) return
        val decoration = decoratedChildren?.get(child) ?: return
        if (!decoration.presence.behindChild) return
        decoration.backend.drawBehindChild(canvas, parent, child)
    }

    fun drawOverChild(
        canvas: Canvas,
        child: View,
    ) {
        if (!hasDecoratedChildren) return
        val decoration = decoratedChildren?.get(child) ?: return
        if (!decoration.presence.overChild) return
        decoration.backend.drawOverChild(canvas, parent, child)
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
