package com.viewcompose.navigation

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import com.viewcompose.navigation.core.NavCommand

internal class AndroidViewNavHostTransitionDriver(
    private val sessionStore: NavDestinationSessionStore,
    private val specProvider: () -> NavTransitionSpec,
) : NavHostTransitionDriver {
    private val interruptedViews = linkedSetOf<View>()

    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
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
        ).also { handle ->
            handle.update(initialEvent)
        }
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
    this.translationX = translationX
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
) : NavHostBackPreviewHandle {
    private val motion = spec.motion
    private val progressInterpolator = spec.progressEasing.toInterpolator()
    private var latestEvent = NavHostBackEvent(
        touchX = 0f,
        touchY = 0f,
        progress = 0f,
        swipeEdge = NavHostBackSwipeEdge.None,
        frameTimeMillis = 0L,
    )
    private var latestVisualProgress = 0f
    private var terminal = false
    private var committing = false

    override fun update(event: NavHostBackEvent) {
        if (terminal || committing) {
            return
        }
        latestEvent = event
        latestVisualProgress = progressInterpolator.getInterpolation(event.progress)
            .coerceIn(0f, 1f)
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
            motion.isDisabled ||
            spec.cancelDurationMillis == 0L ||
            latestVisualProgress <= 0f ||
            outgoing.isEmpty()
        ) {
            reset()
            return
        }
        interruptedViews += outgoing
        val duration = (spec.cancelDurationMillis * latestVisualProgress)
            .toLong()
            .coerceAtLeast(1L)
        val interpolator = spec.cancelEasing.toInterpolator()
        val completionView = outgoing.last()
        val finish = Runnable {
            outgoing.forEach { view ->
                if (interruptedViews.remove(view)) {
                    resetView(view)
                }
            }
        }
        outgoing.forEach { view ->
            val animator = view.animate()
                .translationX(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withLayer()
            if (view === completionView) {
                animator.withEndAction(finish)
            }
            animator.start()
        }
        Handler(Looper.getMainLooper()).postDelayed(finish, duration + 32L)
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
            motion.isDisabled ||
            remainingFraction <= 0f
        ) {
            terminal = true
            reset()
            onCompleted()
            return NavHostTransitionHandle {}
        }

        val direction = backPreviewOutgoingDirection(
            swipeEdge = latestEvent.swipeEdge,
            layoutDirection = layoutDirection,
        )
        val duration = (motion.durationMillis * remainingFraction)
            .toLong()
            .coerceAtLeast(1L)
        val interpolator = motion.easing.toInterpolator()
        val completionView = incoming.lastOrNull() ?: outgoing.lastOrNull()
        val finish = Runnable {
            if (!terminal) {
                terminal = true
                reset()
                onCompleted()
            }
        }
        try {
            if (completionView == null) {
                terminal = true
                reset()
                onCompleted()
                return NavHostTransitionHandle {}
            }
            outgoing.forEach { view ->
                val animator = view.animate()
                    .translationX(
                        direction * travelWidth * motion.outgoingEnd.travelFraction,
                    )
                    .alpha(motion.outgoingEnd.alpha)
                    .scaleX(motion.outgoingEnd.scale)
                    .scaleY(motion.outgoingEnd.scale)
                    .setDuration(duration)
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
                    .setDuration(duration)
                    .setInterpolator(interpolator)
                    .withLayer()
                if (view === completionView) {
                    animator.withEndAction(finish)
                }
                animator.start()
            }
        } catch (throwable: Throwable) {
            terminal = true
            (outgoing + incoming).forEach { view -> view.animate().cancel() }
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
                    val animatedViews = outgoing + incoming
                    animatedViews.forEach { view -> view.animate().cancel() }
                    if (preserveVisualState) {
                        preserveForNextTransition(animatedViews)
                    } else {
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
                transform = motion.outgoingEnd.interpolateFromIdentity(visualProgress),
                translationX = direction *
                    travelWidth *
                    motion.outgoingEnd.travelFraction *
                    visualProgress,
            )
        }
        incoming.forEach { view ->
            view.applyTransform(
                transform = motion.incomingStart.interpolateToIdentity(visualProgress),
                translationX = -direction *
                    travelWidth *
                    motion.incomingStart.travelFraction *
                    (1f - visualProgress),
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
