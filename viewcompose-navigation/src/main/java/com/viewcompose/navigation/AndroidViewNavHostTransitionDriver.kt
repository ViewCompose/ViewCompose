package com.viewcompose.navigation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Trace
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.viewcompose.navigation.core.NavCommand
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class AndroidViewNavHostTransitionDriver(
    private val sessionStore: NavDestinationSessionStore,
    private val specProvider: () -> NavTransitionSpec,
) : NavHostTransitionDriver {
    private val interruptedViews = linkedSetOf<View>()
    private val backSettleController = BackProgressSpringController(
        interruptedViews = interruptedViews,
        preserveForNextTransition = ::preserveForNextTransition,
        resetView = ::resetProperties,
    )

    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        backSettleController.cancelActive(preserveVisualState = true)
        val outgoing = destinationViews(
            transition.beforeScene.visibleEntryIds -
                transition.afterScene.visibleEntryIds,
        )
        val incoming = destinationViews(
            transition.afterScene.visibleEntryIds -
                transition.beforeScene.visibleEntryIds,
        )
        val animatedViews = outgoing + incoming
        val redirectedViews = interruptedViews.toSet()
        interruptedViews.clear()
        (animatedViews + redirectedViews).distinct().forEach { view ->
            view.animate().cancel()
        }
        (redirectedViews - animatedViews.toSet()).forEach(::resetProperties)
        if (animatedViews.isEmpty()) {
            onCompleted()
            return NavHostTransitionHandle {}
        }
        val motion = specProvider().motionFor(transition.command)
        val hostWidth = sessionStore.hostView.width
        if (
            motion.isDisabled ||
            !sessionStore.hostView.isLaidOut ||
            !sessionStore.hostView.isAttachedToWindow ||
            hostWidth <= 0
        ) {
            (animatedViews + redirectedViews).distinct().forEach(::resetProperties)
            onCompleted()
            return NavHostTransitionHandle {}
        }

        outgoing
            .filterNot(redirectedViews::contains)
            .forEach(::resetProperties)
        incoming
            .filterNot(redirectedViews::contains)
            .forEach(::resetProperties)

        val direction = navTransitionDirection(
            command = transition.command,
            layoutDirection = sessionStore.hostView.layoutDirection,
        )
        val paneCount = maxOf(
            transition.beforeScene.panes.size,
            transition.afterScene.panes.size,
        )
        val paneWidth = hostWidth / paneCount.toFloat()
        incoming.forEach { view ->
            view.applyTransform(
                transform = motion.incomingStart,
                translationX = direction * motion.incomingStart.resolveTravelPx(
                    paneWidth = paneWidth,
                    density = view.resources.displayMetrics.density,
                ),
            )
        }

        return CommittedViewTransitionRun(
            outgoing = outgoing,
            incoming = incoming,
            motion = motion,
            paneWidth = paneWidth,
            density = sessionStore.hostView.resources.displayMetrics.density,
            direction = direction,
            resetView = { view ->
                interruptedViews -= view
                resetProperties(view)
            },
            preserveView = ::preserveForNextTransition,
            onCompleted = onCompleted,
        ).start()
    }

    override fun startBackPreview(
        preview: NavHostBackPreview,
        initialEvent: NavHostBackEvent,
    ): NavHostBackPreviewHandle {
        backSettleController.cancelActive(preserveVisualState = false)
        val outgoing = destinationViews(
            preview.beforeScene.visibleEntryIds -
                preview.afterScene.visibleEntryIds,
        )
        val incoming = destinationViews(
            preview.afterScene.visibleEntryIds -
                preview.beforeScene.visibleEntryIds,
        )
        (outgoing + incoming).forEach { view ->
            view.animate().cancel()
            resetProperties(view)
        }
        val predictiveSpec = specProvider().predictiveBack
        return AndroidBackPreviewHandle(
            preview = preview,
            outgoing = outgoing,
            incoming = incoming,
            spec = predictiveSpec,
            travelWidth = sessionStore.hostView.width / maxOf(
                preview.beforeScene.panes.size,
                preview.afterScene.panes.size,
            ).toFloat(),
            travelHeight = sessionStore.hostView.height.toFloat(),
            density = sessionStore.hostView.resources.displayMetrics.density,
            layoutDirection = sessionStore.hostView.layoutDirection,
            canAnimate = sessionStore.hostView.isLaidOut &&
                sessionStore.hostView.isAttachedToWindow &&
                sessionStore.hostView.width > 0,
            interruptedViews = interruptedViews,
            preserveForNextTransition = { views ->
                views.forEach(::preserveForNextTransition)
            },
            resetView = ::resetProperties,
            settleController = backSettleController,
        ).also { handle ->
            handle.update(initialEvent)
        }
    }

    override fun destroy() {
        backSettleController.cancelActive(preserveVisualState = false)
    }

    private fun preserveForNextTransition(view: View) {
        interruptedViews += view
        view.postOnAnimation {
            if (interruptedViews.remove(view)) {
                resetProperties(view)
            }
        }
    }

    private fun resetProperties(view: View) {
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun destinationViews(
        entryIds: Set<com.viewcompose.navigation.core.NavEntryId>,
    ): List<View> {
        return entryIds.map { entryId ->
            checkNotNull(sessionStore.sessionOrNull(entryId)) {
                "Animated destination $entryId has no committed View."
            }.container
        }
    }
}

private fun View.applyTransform(
    transform: NavDestinationTransform,
    translationX: Float,
    translationY: Float = 0f,
) {
    this.translationX = if (translationX == 0f) 0f else translationX
    this.translationY = if (translationY == 0f) 0f else translationY
    alpha = transform.alpha
    scaleX = transform.scale
    scaleY = transform.scale
}

private fun NavDestinationTransform.resolveTravelPx(
    paneWidth: Float,
    density: Float,
): Float {
    return paneWidth * travelFraction + density * travelDp
}

private fun NavMotionEasing.toInterpolator(): Interpolator {
    val path = Path().apply {
        moveTo(0f, 0f)
        segments.forEach { segment ->
            cubicTo(
                segment.control1X,
                segment.control1Y,
                segment.control2X,
                segment.control2Y,
                segment.endX,
                segment.endY,
            )
        }
    }
    return PathInterpolator(path)
}

private class AndroidBackPreviewHandle(
    private val preview: NavHostBackPreview,
    private val outgoing: List<View>,
    private val incoming: List<View>,
    private val spec: NavPredictiveBackSpec,
    private val travelWidth: Float,
    private val travelHeight: Float,
    private val density: Float,
    private val layoutDirection: Int,
    private val canAnimate: Boolean,
    private val interruptedViews: MutableSet<View>,
    private val preserveForNextTransition: (List<View>) -> Unit,
    private val resetView: (View) -> Unit,
    private val settleController: BackProgressSpringController,
) : NavHostBackPreviewHandle {
    private val progressInterpolator = spec.progressEasing.toInterpolator()
    private val velocityTracker = NavProgressVelocityTracker(
        sampleWindowMillis = spec.velocitySampleWindowMillis,
        maxAbsoluteVelocity = spec.maxProgressVelocity,
    )
    private var latestEvent = NavHostBackEvent(
        touchX = 0f,
        touchY = 0f,
        progress = 0f,
        swipeEdge = NavHostBackSwipeEdge.None,
        frameTimeMillis = 0L,
    )
    private var latestVisualProgress = 0f
    private var latestProgressVelocity = 0f
    private var terminal = false
    private var committing = false
    private var initialTouchY: Float? = null
    private val scrim = PredictiveBackScrim(incoming).also { scrim ->
        if (canAnimate && !spec.isDisabled) {
            scrim.attach()
        }
    }
    private val layerLease = TransitionHardwareLayerLease(outgoing + incoming).also { lease ->
        if (canAnimate && !spec.isDisabled) {
            lease.acquire()
        }
    }

    override fun update(event: NavHostBackEvent) {
        if (terminal || committing) {
            return
        }
        if (initialTouchY == null) {
            initialTouchY = event.touchY
        }
        latestEvent = event
        latestVisualProgress = progressInterpolator.getInterpolation(event.progress)
            .coerceIn(0f, 1f)
        latestProgressVelocity = velocityTracker.add(
            frameTimeMillis = event.frameTimeMillis,
            progress = latestVisualProgress,
        )
        if (!canAnimate) {
            return
        }
        applyProgress(
            event = event,
            visualProgress = latestVisualProgress,
        )
    }

    override fun cancel() {
        if (terminal) {
            return
        }
        terminal = true
        (outgoing + incoming).forEach { view -> view.animate().cancel() }
        incoming.forEach { view ->
            interruptedViews -= view
            resetView(view)
        }
        scrim.clear()
        if (
            !canAnimate ||
            spec.isDisabled ||
            latestVisualProgress <= 0f ||
            outgoing.isEmpty()
        ) {
            reset()
            return
        }
        settleController.start(
            views = outgoing,
            initialProgress = latestVisualProgress,
            targetProgress = 0f,
            initialVelocity = latestProgressVelocity,
            springSpec = spec.cancelSpring,
            onUpdate = { visualProgress ->
                applyOutgoingProgress(
                    event = latestEvent,
                    visualProgress = visualProgress,
                )
            },
            onCompleted = {
                outgoing.forEach(resetView)
            },
            onTerminated = layerLease::release,
        )
    }

    override fun redirect() {
        if (terminal) {
            return
        }
        terminal = true
        val animatedViews = outgoing + incoming
        animatedViews.forEach { view -> view.animate().cancel() }
        scrim.clear()
        preserveForNextTransition(animatedViews)
        layerLease.release()
    }

    override fun dispose() {
        if (terminal) {
            return
        }
        terminal = true
        (outgoing + incoming).forEach { view -> view.animate().cancel() }
        reset()
    }

    override fun commit(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        check(!terminal && !committing) {
            "A predictive-back preview can be committed only once."
        }
        check(
            transition.command == preview.command &&
                transition.before == preview.snapshot &&
                transition.outgoingEntry == preview.outgoingEntry &&
                transition.incomingEntry == preview.incomingEntry &&
                transition.beforeScene == preview.beforeScene &&
                transition.afterScene == preview.afterScene,
        ) {
            "Predictive-back preview does not match the committed pop transition."
        }
        committing = true
        if (
            !canAnimate ||
            spec.isDisabled ||
            spec.commitMotion.isDisabled
        ) {
            terminal = true
            reset()
            onCompleted()
            return NavHostTransitionHandle {}
        }

        val animatedViews = outgoing + incoming
        if (animatedViews.isEmpty()) {
            terminal = true
            reset()
            onCompleted()
            return NavHostTransitionHandle {}
        }
        try {
            layerLease.release()
            val motionDirection = -backPreviewOutgoingDirection(
                swipeEdge = latestEvent.swipeEdge,
                layoutDirection = layoutDirection,
            )
            return CommittedViewTransitionRun(
                outgoing = outgoing,
                incoming = incoming,
                motion = spec.commitMotion,
                paneWidth = travelWidth,
                density = density,
                direction = motionDirection,
                resetView = { view ->
                    interruptedViews -= view
                    resetView(view)
                },
                preserveView = { view ->
                    preserveForNextTransition(listOf(view))
                },
                onFrame = scrim::applyCommitProgress,
                onTerminated = scrim::clear,
                onCompleted = {
                    if (!terminal) {
                        terminal = true
                        onCompleted()
                    }
                },
            ).start()
        } catch (throwable: Throwable) {
            terminal = true
            animatedViews.forEach { view -> view.animate().cancel() }
            reset()
            throw throwable
        }
    }

    private fun applyProgress(
        event: NavHostBackEvent,
        visualProgress: Float,
    ) {
        val direction = backPreviewOutgoingDirection(
            swipeEdge = event.swipeEdge,
            layoutDirection = layoutDirection,
        )
        outgoing.forEach { view ->
            val transform = spec.outgoingEnd.interpolateFromIdentity(visualProgress)
            view.applyTransform(
                transform = transform,
                translationX = direction *
                    transform.resolveTravelPx(
                        paneWidth = travelWidth,
                        density = density,
                    ),
                translationY = verticalOffset(
                    event = event,
                    scale = transform.scale,
                ),
            )
        }
        incoming.forEach { view ->
            val transform = spec.incomingStart.interpolateTo(
                end = spec.incomingEnd,
                fraction = visualProgress,
            )
            view.applyTransform(
                transform = transform,
                translationX = -direction *
                    transform.resolveTravelPx(
                        paneWidth = travelWidth,
                        density = density,
                    ),
                translationY = verticalOffset(
                    event = event,
                    scale = transform.scale,
                ),
            )
        }
    }

    private fun applyOutgoingProgress(
        event: NavHostBackEvent,
        visualProgress: Float,
    ) {
        val direction = backPreviewOutgoingDirection(
            swipeEdge = event.swipeEdge,
            layoutDirection = layoutDirection,
        )
        outgoing.forEach { view ->
            val transform = spec.outgoingEnd.interpolateFromIdentity(visualProgress)
            view.applyTransform(
                transform = transform,
                translationX = direction *
                    transform.resolveTravelPx(
                        paneWidth = travelWidth,
                        density = density,
                    ),
                translationY = verticalOffset(
                    event = event,
                    scale = transform.scale,
                ),
            )
        }
    }

    private fun verticalOffset(
        event: NavHostBackEvent,
        scale: Float,
    ): Float {
        val startTouchY = initialTouchY ?: return 0f
        val height = travelHeight
        if (height <= 0f) {
            return 0f
        }
        val rawDelta = event.touchY - startTouchY
        val halfHeight = height / 2f
        val deltaRatio = min(halfHeight, abs(rawDelta)) / halfHeight
        val deceleratedRatio = 1f - (1f - deltaRatio) * (1f - deltaRatio)
        val availableShift = max(
            0f,
            (height - height * scale) / 2f - SYSTEM_BACK_EDGE_MARGIN_DP * density,
        )
        return availableShift * deceleratedRatio * if (rawDelta < 0f) -1f else 1f
    }

    private fun reset() {
        scrim.clear()
        (outgoing + incoming).forEach { view ->
            interruptedViews -= view
            resetView(view)
        }
        layerLease.release()
    }
}

private class CommittedViewTransitionRun(
    outgoing: List<View>,
    incoming: List<View>,
    private val motion: NavDestinationMotionSpec,
    paneWidth: Float,
    density: Float,
    direction: Float,
    private val resetView: (View) -> Unit,
    private val preserveView: (View) -> Unit,
    private val onFrame: (Float) -> Unit = {},
    private val onTerminated: () -> Unit = {},
    private val onCompleted: () -> Unit,
) : NavHostTransitionHandle {
    private val outgoingViews = outgoing.distinct()
    private val incomingViews = incoming.distinct()
    private val animatedViews = (outgoingViews + incomingViews).distinct()
    private val layerLease = TransitionHardwareLayerLease(animatedViews)
    private val startStates = animatedViews.associateWith(ViewTransformState::capture)
    private val outgoingTarget = ViewTransformState(
        translationX = -direction * motion.outgoingEnd.resolveTravelPx(paneWidth, density),
        translationY = 0f,
        alpha = motion.outgoingEnd.alpha,
        scaleX = motion.outgoingEnd.scale,
        scaleY = motion.outgoingEnd.scale,
    )
    private val incomingTarget = ViewTransformState.Identity
    private val startAnchor = incomingViews.firstOrNull() ?: animatedViews.first()
    private val surfaceWarmupDelayMillis = startAnchor.display
        ?.refreshRate
        ?.takeIf { refreshRate -> refreshRate.isFinite() && refreshRate > 0f }
        ?.let { refreshRate -> (1_000f / refreshRate).toLong() + 1L }
        ?: DEFAULT_SURFACE_WARMUP_DELAY_MILLIS
    private val startAnimator = Runnable {
        if (!terminal) {
            try {
                animator.start()
            } catch (_: Throwable) {
                finish()
            }
        }
    }
    private val finishSurfaceWarmup = Runnable {
        if (!terminal) {
            startAnchor.postOnAnimationDelayed(
                startAnimator,
                surfaceWarmupDelayMillis,
            )
        }
    }
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = motion.totalDurationMillis
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            if (!terminal) {
                Trace.beginSection(NAV_MOTION_FRAME_TRACE_SECTION)
                try {
                    applyFrame(
                        linearProgress = animation.animatedFraction,
                        playTimeMillis = animation.currentPlayTime,
                    )
                } finally {
                    Trace.endSection()
                }
            }
        }
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                }
            },
        )
    }
    private var terminal = false

    fun start(): NavHostTransitionHandle {
        try {
            layerLease.acquire()
            applyFrame(linearProgress = 0f, playTimeMillis = 0L)
            startAfterSurfaceWarmup()
        } catch (throwable: Throwable) {
            terminal = true
            cancelScheduledStart()
            animatedViews.forEach(resetView)
            layerLease.release()
            throw throwable
        }
        return this
    }

    override fun cancel() {
        terminate(preserveVisualState = false)
    }

    override fun redirect() {
        terminate(preserveVisualState = true)
    }

    private fun applyFrame(
        linearProgress: Float,
        playTimeMillis: Long,
    ) {
        onFrame(linearProgress)
        val geometryProgress = motion.easing.transform(linearProgress)
        val outgoingAlphaProgress = motion.outgoingAlphaTiming.progressAt(playTimeMillis)
        val incomingAlphaProgress = motion.incomingAlphaTiming.progressAt(playTimeMillis)
        outgoingViews.forEach { view ->
            view.applyState(
                start = checkNotNull(startStates[view]),
                end = outgoingTarget,
                geometryProgress = geometryProgress,
                alphaProgress = outgoingAlphaProgress,
            )
        }
        incomingViews.forEach { view ->
            view.applyState(
                start = checkNotNull(startStates[view]),
                end = incomingTarget,
                geometryProgress = geometryProgress,
                alphaProgress = incomingAlphaProgress,
            )
        }
    }

    private fun finish() {
        if (terminal) {
            return
        }
        terminal = true
        cancelScheduledStart()
        animatedViews.forEach(resetView)
        layerLease.release()
        onTerminated()
        onCompleted()
    }

    private fun terminate(preserveVisualState: Boolean) {
        if (terminal) {
            return
        }
        terminal = true
        cancelScheduledStart()
        animator.cancel()
        onTerminated()
        if (preserveVisualState) {
            animatedViews.forEach(preserveView)
        } else {
            animatedViews.forEach(resetView)
        }
        layerLease.release()
    }

    /**
     * Draws the fully laid-out start state into its hardware layer before motion begins.
     *
     * Without this barrier, the first animated frame also measures the newly visible destination
     * and records its entire View hierarchy. Android window transitions avoid that collision by
     * waiting for the destination window's first frame before SurfaceControl starts moving it.
     */
    private fun startAfterSurfaceWarmup() {
        val observer = startAnchor.viewTreeObserver
        if (!observer.isAlive) {
            startAnchor.postOnAnimation(startAnimator)
            return
        }
        val listener = ViewTreeObserver.OnPreDrawListener {
            removePreDrawListener()
            startAnchor.postOnAnimation(finishSurfaceWarmup)
            true
        }
        preDrawListener = listener
        observer.addOnPreDrawListener(listener)
        startAnchor.invalidate()
    }

    private fun cancelScheduledStart() {
        removePreDrawListener()
        startAnchor.removeCallbacks(finishSurfaceWarmup)
        startAnchor.removeCallbacks(startAnimator)
    }

    private fun removePreDrawListener() {
        val listener = preDrawListener ?: return
        preDrawListener = null
        val observer = startAnchor.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnPreDrawListener(listener)
        }
    }
}

/**
 * Keeps a destination's expensive View hierarchy in a texture while only transform/alpha
 * properties change. Activity transitions get this behavior from Window/SurfaceControl; a
 * temporary hardware View layer is the public View-system equivalent.
 */
private class TransitionHardwareLayerLease(
    views: List<View>,
) {
    private val originalLayerTypes = views
        .distinct()
        .associateWith(View::getLayerType)
    private var acquired = false

    fun acquire() {
        if (acquired) {
            return
        }
        acquired = true
        originalLayerTypes.forEach { (view, originalLayerType) ->
            if (originalLayerType != View.LAYER_TYPE_SOFTWARE) {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }
    }

    fun release() {
        if (!acquired) {
            return
        }
        acquired = false
        originalLayerTypes.forEach { (view, originalLayerType) ->
            if (
                originalLayerType != View.LAYER_TYPE_SOFTWARE &&
                view.layerType != originalLayerType
            ) {
                view.setLayerType(originalLayerType, null)
            }
        }
    }
}

private data class ViewTransformState(
    val translationX: Float,
    val translationY: Float,
    val alpha: Float,
    val scaleX: Float,
    val scaleY: Float,
) {
    companion object {
        val Identity = ViewTransformState(
            translationX = 0f,
            translationY = 0f,
            alpha = 1f,
            scaleX = 1f,
            scaleY = 1f,
        )

        fun capture(view: View): ViewTransformState {
            return ViewTransformState(
                translationX = view.translationX,
                translationY = view.translationY,
                alpha = view.alpha,
                scaleX = view.scaleX,
                scaleY = view.scaleY,
            )
        }
    }
}

private fun View.applyState(
    start: ViewTransformState,
    end: ViewTransformState,
    geometryProgress: Float,
    alphaProgress: Float,
) {
    translationX = lerp(start.translationX, end.translationX, geometryProgress)
    translationY = lerp(start.translationY, end.translationY, geometryProgress)
    scaleX = lerp(start.scaleX, end.scaleX, geometryProgress)
    scaleY = lerp(start.scaleY, end.scaleY, geometryProgress)
    alpha = lerp(start.alpha, end.alpha, alphaProgress)
}

private class PredictiveBackScrim(
    private val views: List<View>,
) {
    private val states = mutableListOf<ScrimState>()
    private var attached = false

    fun attach() {
        if (attached) {
            return
        }
        attached = true
        views.distinct().forEach { view ->
            val drawable = ColorDrawable(Color.BLACK)
            val maxAlpha = if (
                view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            ) {
                SYSTEM_BACK_SCRIM_ALPHA_DARK
            } else {
                SYSTEM_BACK_SCRIM_ALPHA_LIGHT
            }
            states += ScrimState(
                view = view,
                originalForeground = view.foreground,
                drawable = drawable,
                maxAlpha = maxAlpha,
            )
            drawable.alpha = (maxAlpha * 255f).toInt()
            view.foreground = drawable
        }
    }

    fun applyCommitProgress(linearProgress: Float) {
        states.forEach { state ->
            state.drawable.alpha = (
                state.maxAlpha *
                    (1f - linearProgress.coerceIn(0f, 1f)) *
                    255f
                ).toInt()
        }
    }

    fun clear() {
        if (!attached) {
            return
        }
        attached = false
        states.forEach { state ->
            if (state.view.foreground === state.drawable) {
                state.view.foreground = state.originalForeground
            }
        }
        states.clear()
    }

    private data class ScrimState(
        val view: View,
        val originalForeground: Drawable?,
        val drawable: ColorDrawable,
        val maxAlpha: Float,
    )
}

private fun interface BackProgressSpringHandle {
    fun cancel(preserveVisualState: Boolean)
}

private class BackProgressSpringController(
    private val interruptedViews: MutableSet<View>,
    private val preserveForNextTransition: (View) -> Unit,
    private val resetView: (View) -> Unit,
) {
    private var active: ActiveSpring? = null

    fun start(
        views: List<View>,
        initialProgress: Float,
        targetProgress: Float,
        initialVelocity: Float,
        springSpec: NavSpringSpec,
        onUpdate: (Float) -> Unit,
        onCompleted: () -> Unit,
        onTerminated: () -> Unit = {},
    ): BackProgressSpringHandle {
        cancelActive(preserveVisualState = false)
        val animatedViews = views.distinct()
        if (
            animatedViews.isEmpty() ||
            abs(targetProgress - initialProgress) <= MIN_PROGRESS_CHANGE
        ) {
            onUpdate(targetProgress)
            try {
                onCompleted()
            } finally {
                onTerminated()
            }
            return BackProgressSpringHandle {}
        }

        interruptedViews += animatedViews
        lateinit var run: BackProgressSpringRun
        run = BackProgressSpringRun(
            initialProgress = initialProgress,
            targetProgress = targetProgress,
            initialVelocity = initialVelocity,
            springSpec = springSpec,
            onUpdate = onUpdate,
            onCompleted = {
                val current = active
                if (current?.run === run) {
                    active = null
                    interruptedViews.removeAll(current.views.toSet())
                    try {
                        onCompleted()
                    } finally {
                        onTerminated()
                    }
                }
            },
        )
        active = ActiveSpring(
            run = run,
            views = animatedViews,
            onTerminated = onTerminated,
        )
        try {
            run.start()
        } catch (throwable: Throwable) {
            active = null
            interruptedViews.removeAll(animatedViews.toSet())
            animatedViews.forEach(resetView)
            onTerminated()
            throw throwable
        }
        return BackProgressSpringHandle { preserveVisualState ->
            cancel(
                expectedRun = run,
                preserveVisualState = preserveVisualState,
            )
        }
    }

    fun cancelActive(preserveVisualState: Boolean) {
        val current = active ?: return
        cancel(
            expectedRun = current.run,
            preserveVisualState = preserveVisualState,
        )
    }

    private fun cancel(
        expectedRun: BackProgressSpringRun,
        preserveVisualState: Boolean,
    ) {
        val current = active?.takeIf { active -> active.run === expectedRun } ?: return
        active = null
        current.run.cancel()
        interruptedViews.removeAll(current.views.toSet())
        if (preserveVisualState) {
            current.views.forEach(preserveForNextTransition)
        } else {
            current.views.forEach(resetView)
        }
        current.onTerminated()
    }

    private data class ActiveSpring(
        val run: BackProgressSpringRun,
        val views: List<View>,
        val onTerminated: () -> Unit,
    )

    private companion object {
        const val MIN_PROGRESS_CHANGE = 0.0001f
    }
}

private class BackProgressSpringRun(
    initialProgress: Float,
    private val targetProgress: Float,
    initialVelocity: Float,
    springSpec: NavSpringSpec,
    private val onUpdate: (Float) -> Unit,
    private val onCompleted: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val valueHolder = FloatValueHolder(initialProgress)
    private val animation = SpringAnimation(valueHolder).apply {
        spring = SpringForce(targetProgress).apply {
            stiffness = springSpec.stiffness
            dampingRatio = springSpec.dampingRatio
        }
        setStartVelocity(initialVelocity)
        setMinValue(0f)
        setMaxValue(1f)
        setMinimumVisibleChange(MIN_VISIBLE_PROGRESS_CHANGE)
        addUpdateListener { _, value, _ ->
            if (!terminal) {
                onUpdate(value.coerceIn(0f, 1f))
            }
        }
        addEndListener { _, _, _, _ ->
            finish()
        }
    }
    private val timeout = Runnable {
        if (!terminal) {
            terminal = true
            animation.cancel()
            onUpdate(targetProgress)
            onCompleted()
        }
    }
    private val maxDurationMillis = springSpec.maxDurationMillis
    private var terminal = false

    fun start() {
        onUpdate(valueHolder.value.coerceIn(0f, 1f))
        animation.start()
        handler.postDelayed(timeout, maxDurationMillis)
    }

    fun cancel() {
        if (terminal) {
            return
        }
        terminal = true
        handler.removeCallbacks(timeout)
        animation.cancel()
    }

    private fun finish() {
        if (terminal) {
            return
        }
        terminal = true
        handler.removeCallbacks(timeout)
        onUpdate(targetProgress)
        onCompleted()
    }

    private companion object {
        const val MIN_VISIBLE_PROGRESS_CHANGE = 0.001f
    }
}

private fun NavDestinationTransform.interpolateFromIdentity(
    fraction: Float,
): NavDestinationTransform {
    return NavDestinationTransform(
        travelFraction = travelFraction * fraction,
        travelDp = travelDp * fraction,
        alpha = lerp(1f, alpha, fraction),
        scale = lerp(1f, scale, fraction),
    )
}

private fun NavDestinationTransform.interpolateTo(
    end: NavDestinationTransform,
    fraction: Float,
): NavDestinationTransform {
    return NavDestinationTransform(
        travelFraction = lerp(travelFraction, end.travelFraction, fraction),
        travelDp = lerp(travelDp, end.travelDp, fraction),
        alpha = lerp(alpha, end.alpha, fraction),
        scale = lerp(scale, end.scale, fraction),
    )
}

private fun lerp(
    start: Float,
    end: Float,
    fraction: Float,
): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

private const val SYSTEM_BACK_EDGE_MARGIN_DP = 8f
private const val SYSTEM_BACK_SCRIM_ALPHA_DARK = 0.8f
private const val SYSTEM_BACK_SCRIM_ALPHA_LIGHT = 0.2f
private const val DEFAULT_SURFACE_WARMUP_DELAY_MILLIS = 17L
private const val NAV_MOTION_FRAME_TRACE_SECTION = "VC.Nav.MotionFrame"

internal fun navTransitionDirection(
    command: NavCommand,
    layoutDirection: Int,
): Float {
    val layoutMultiplier = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
    val commandMultiplier = when (command) {
        NavCommand.Pop,
        NavCommand.PopStackHistory,
        -> -1f

        is NavCommand.Push,
        is NavCommand.ReplaceTop,
        is NavCommand.Reset,
        is NavCommand.SelectStack,
        is NavCommand.OpenDeepLink,
        -> 1f
    }
    return layoutMultiplier * commandMultiplier
}

internal fun backPreviewOutgoingDirection(
    swipeEdge: NavHostBackSwipeEdge,
    layoutDirection: Int,
): Float {
    return when (swipeEdge) {
        NavHostBackSwipeEdge.Left -> 1f
        NavHostBackSwipeEdge.Right -> -1f
        NavHostBackSwipeEdge.None -> {
            -navTransitionDirection(
                command = NavCommand.Pop,
                layoutDirection = layoutDirection,
            )
        }
    }
}
