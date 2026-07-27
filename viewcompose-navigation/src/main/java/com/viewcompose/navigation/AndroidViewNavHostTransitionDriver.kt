package com.viewcompose.navigation

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.viewcompose.navigation.core.NavCommand
import kotlin.math.abs

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
                translationX = direction * paneWidth * motion.incomingStart.travelFraction,
            )
        }

        var terminal = false
        val finish = Runnable {
            if (!terminal) {
                terminal = true
                animatedViews.forEach(::resetProperties)
                interruptedViews.removeAll(animatedViews.toSet())
                onCompleted()
            }
        }
        val interpolator = motion.easing.toInterpolator()
        val completionView = incoming.lastOrNull() ?: outgoing.last()
        try {
            outgoing.forEach { view ->
                val animator = view.animate()
                    .translationX(
                        -direction * paneWidth * motion.outgoingEnd.travelFraction,
                    )
                    .alpha(motion.outgoingEnd.alpha)
                    .scaleX(motion.outgoingEnd.scale)
                    .scaleY(motion.outgoingEnd.scale)
                    .setDuration(motion.durationMillis)
                    .setInterpolator(interpolator)
                    .withLayer()
                if (view === completionView) {
                    animator.withEndAction(finish)
                }
                animator.start()
            }
            incoming.forEach { view ->
                val animator = view.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(motion.durationMillis)
                    .setInterpolator(interpolator)
                    .withLayer()
                if (view === completionView) {
                    animator.withEndAction(finish)
                }
                animator.start()
            }
        } catch (throwable: Throwable) {
            terminal = true
            animatedViews.forEach { view ->
                view.animate().cancel()
                resetProperties(view)
            }
            interruptedViews.removeAll(animatedViews.toSet())
            throw throwable
        }

        return object : NavHostTransitionHandle {
            override fun cancel() {
                terminate(preserveVisualState = false)
            }

            override fun redirect() {
                terminate(preserveVisualState = true)
            }

            private fun terminate(preserveVisualState: Boolean) {
                if (!terminal) {
                    terminal = true
                    animatedViews.forEach { view ->
                        view.animate().cancel()
                        if (preserveVisualState) {
                            preserveForNextTransition(view)
                        } else {
                            interruptedViews -= view
                            resetProperties(view)
                        }
                    }
                }
            }
        }
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
) {
    this.translationX = if (translationX == 0f) 0f else translationX
    alpha = transform.alpha
    scaleX = transform.scale
    scaleY = transform.scale
}

private fun NavMotionEasing.toInterpolator(): Interpolator {
    return PathInterpolator(x1, y1, x2, y2)
}

private class AndroidBackPreviewHandle(
    private val preview: NavHostBackPreview,
    private val outgoing: List<View>,
    private val incoming: List<View>,
    private val spec: NavPredictiveBackSpec,
    private val travelWidth: Float,
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

    override fun update(event: NavHostBackEvent) {
        if (terminal || committing) {
            return
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
        )
    }

    override fun redirect() {
        if (terminal) {
            return
        }
        terminal = true
        val animatedViews = outgoing + incoming
        animatedViews.forEach { view -> view.animate().cancel() }
        preserveForNextTransition(animatedViews)
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
        val remainingFraction = 1f - latestVisualProgress
        if (
            !canAnimate ||
            spec.isDisabled ||
            remainingFraction <= 0f
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
        lateinit var settleHandle: BackProgressSpringHandle
        try {
            settleHandle = settleController.start(
                views = animatedViews,
                initialProgress = latestVisualProgress,
                targetProgress = 1f,
                initialVelocity = latestProgressVelocity,
                springSpec = spec.commitSpring,
                onUpdate = { visualProgress ->
                    applyProgress(
                        event = latestEvent,
                        visualProgress = visualProgress,
                    )
                },
                onCompleted = {
                    if (!terminal) {
                        terminal = true
                        reset()
                        onCompleted()
                    }
                }
            )
        } catch (throwable: Throwable) {
            terminal = true
            animatedViews.forEach { view -> view.animate().cancel() }
            reset()
            throw throwable
        }
        return object : NavHostTransitionHandle {
            override fun cancel() {
                terminate(preserveVisualState = false)
            }

            override fun redirect() {
                terminate(preserveVisualState = true)
            }

            private fun terminate(preserveVisualState: Boolean) {
                if (!terminal) {
                    terminal = true
                    animatedViews.forEach { view -> view.animate().cancel() }
                    settleHandle.cancel(preserveVisualState)
                    if (!preserveVisualState) {
                        reset()
                    }
                }
            }
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
            view.applyTransform(
                transform = spec.outgoingEnd.interpolateFromIdentity(visualProgress),
                translationX = direction *
                    travelWidth *
                    spec.outgoingEnd.travelFraction *
                    visualProgress,
            )
        }
        incoming.forEach { view ->
            view.applyTransform(
                transform = spec.incomingStart.interpolateToIdentity(visualProgress),
                translationX = -direction *
                    travelWidth *
                    spec.incomingStart.travelFraction *
                    (1f - visualProgress),
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
            view.applyTransform(
                transform = spec.outgoingEnd.interpolateFromIdentity(visualProgress),
                translationX = direction *
                    travelWidth *
                    spec.outgoingEnd.travelFraction *
                    visualProgress,
            )
        }
    }

    private fun reset() {
        (outgoing + incoming).forEach { view ->
            interruptedViews -= view
            resetView(view)
        }
    }
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
    ): BackProgressSpringHandle {
        cancelActive(preserveVisualState = false)
        val animatedViews = views.distinct()
        if (
            animatedViews.isEmpty() ||
            abs(targetProgress - initialProgress) <= MIN_PROGRESS_CHANGE
        ) {
            onUpdate(targetProgress)
            onCompleted()
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
                    onCompleted()
                }
            },
        )
        active = ActiveSpring(
            run = run,
            views = animatedViews,
        )
        try {
            run.start()
        } catch (throwable: Throwable) {
            active = null
            interruptedViews.removeAll(animatedViews.toSet())
            animatedViews.forEach(resetView)
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
    }

    private data class ActiveSpring(
        val run: BackProgressSpringRun,
        val views: List<View>,
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
        alpha = lerp(1f, alpha, fraction),
        scale = lerp(1f, scale, fraction),
    )
}

private fun NavDestinationTransform.interpolateToIdentity(
    fraction: Float,
): NavDestinationTransform {
    return NavDestinationTransform(
        travelFraction = travelFraction * (1f - fraction),
        alpha = lerp(alpha, 1f, fraction),
        scale = lerp(scale, 1f, fraction),
    )
}

private fun lerp(
    start: Float,
    end: Float,
    fraction: Float,
): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

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
