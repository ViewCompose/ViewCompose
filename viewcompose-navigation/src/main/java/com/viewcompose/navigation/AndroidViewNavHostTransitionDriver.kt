package com.viewcompose.navigation

import android.view.View
import android.view.animation.DecelerateInterpolator
import com.viewcompose.navigation.core.NavCommand

internal class AndroidViewNavHostTransitionDriver(
    private val sessionStore: NavDestinationSessionStore,
    private val specProvider: () -> NavTransitionSpec,
) : NavHostTransitionDriver {
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
        if (animatedViews.isEmpty()) {
            onCompleted()
            return NavHostTransitionHandle {}
        }
        val spec = specProvider()
        val hostWidth = sessionStore.hostView.width
        if (
            spec.durationMillis == 0L ||
            (spec.travelFraction == 0f && !spec.fadeEnabled) ||
            !sessionStore.hostView.isLaidOut ||
            !sessionStore.hostView.isAttachedToWindow ||
            hostWidth <= 0
        ) {
            animatedViews.forEach(::resetProperties)
            onCompleted()
            return NavHostTransitionHandle {}
        }

        animatedViews.forEach { view ->
            view.animate().cancel()
            resetProperties(view)
        }

        val direction = navTransitionDirection(
            command = transition.command,
            layoutDirection = sessionStore.hostView.layoutDirection,
        )
        val paneCount = maxOf(
            transition.beforeScene.panes.size,
            transition.afterScene.panes.size,
        )
        val travel = (hostWidth / paneCount.toFloat()) * spec.travelFraction
        incoming.forEach { view -> view.translationX = direction * travel }
        if (spec.fadeEnabled) {
            incoming.forEach { view -> view.alpha = 0f }
        }

        var terminal = false
        val finish = Runnable {
            if (!terminal) {
                terminal = true
                animatedViews.forEach(::resetProperties)
                onCompleted()
            }
        }
        val interpolator = DecelerateInterpolator()
        val completionView = incoming.lastOrNull() ?: outgoing.last()
        try {
            outgoing.forEach { view ->
                val animator = view.animate()
                    .translationX(-direction * travel)
                    .alpha(if (spec.fadeEnabled) 0f else 1f)
                    .setDuration(spec.durationMillis)
                    .setInterpolator(interpolator)
                if (view === completionView) {
                    animator.withEndAction(finish)
                }
                animator.start()
            }
            incoming.forEach { view ->
                val animator = view.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(spec.durationMillis)
                    .setInterpolator(interpolator)
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
            throw throwable
        }

        return NavHostTransitionHandle {
            if (!terminal) {
                terminal = true
                animatedViews.forEach { view ->
                    view.animate().cancel()
                    resetProperties(view)
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
        return AndroidBackPreviewHandle(
            preview = preview,
            outgoing = outgoing,
            incoming = incoming,
            spec = specProvider(),
            travelWidth = sessionStore.hostView.width / maxOf(
                preview.beforeScene.panes.size,
                preview.afterScene.panes.size,
            ).toFloat(),
            layoutDirection = sessionStore.hostView.layoutDirection,
            canAnimate = sessionStore.hostView.isLaidOut &&
                sessionStore.hostView.isAttachedToWindow &&
                sessionStore.hostView.width > 0,
        ).also { handle ->
            handle.update(initialEvent)
        }
    }

    private fun resetProperties(view: View) {
        view.alpha = 1f
        view.translationX = 0f
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

private class AndroidBackPreviewHandle(
    private val preview: NavHostBackPreview,
    private val outgoing: List<View>,
    private val incoming: List<View>,
    private val spec: NavTransitionSpec,
    private val travelWidth: Float,
    private val layoutDirection: Int,
    private val canAnimate: Boolean,
) : NavHostBackPreviewHandle {
    private var latestEvent = NavHostBackEvent(
        touchX = 0f,
        touchY = 0f,
        progress = 0f,
        swipeEdge = NavHostBackSwipeEdge.None,
        frameTimeMillis = 0L,
    )
    private var terminal = false
    private var committing = false

    override fun update(event: NavHostBackEvent) {
        if (terminal || committing) {
            return
        }
        latestEvent = event
        if (!canAnimate) {
            return
        }
        applyProgress(event)
    }

    override fun cancel() {
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
        val remainingFraction = 1f - latestEvent.progress
        if (
            !canAnimate ||
            spec.durationMillis == 0L ||
            (spec.travelFraction == 0f && !spec.fadeEnabled) ||
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
        val travel = travelWidth * spec.travelFraction
        val duration = (spec.durationMillis * remainingFraction)
            .toLong()
            .coerceAtLeast(1L)
        val interpolator = DecelerateInterpolator()
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
                    .translationX(direction * travel)
                    .alpha(if (spec.fadeEnabled) 0f else 1f)
                    .setDuration(duration)
                    .setInterpolator(interpolator)
                if (view === completionView) {
                    animator.withEndAction(finish)
                }
                animator.start()
            }
            incoming.forEach { view ->
                val animator = view.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(duration)
                    .setInterpolator(interpolator)
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
        return NavHostTransitionHandle {
            if (!terminal) {
                terminal = true
                (outgoing + incoming).forEach { view -> view.animate().cancel() }
                reset()
            }
        }
    }

    private fun applyProgress(event: NavHostBackEvent) {
        val direction = backPreviewOutgoingDirection(
            swipeEdge = event.swipeEdge,
            layoutDirection = layoutDirection,
        )
        val travel = travelWidth * spec.travelFraction
        outgoing.forEach { view ->
            view.translationX = direction * travel * event.progress
        }
        incoming.forEach { view ->
            view.translationX = -direction * travel * (1f - event.progress)
        }
        if (spec.fadeEnabled) {
            outgoing.forEach { view -> view.alpha = 1f - event.progress }
            incoming.forEach { view -> view.alpha = event.progress }
        }
    }

    private fun reset() {
        (outgoing + incoming).forEach { view ->
            view.alpha = 1f
            view.translationX = 0f
        }
    }
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
