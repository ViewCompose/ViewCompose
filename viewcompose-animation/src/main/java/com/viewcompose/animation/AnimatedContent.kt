package com.viewcompose.animation

import com.viewcompose.animation.core.FiniteAnimationSpec
import com.viewcompose.animation.core.snap
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AnimatedContentHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedContentItemNodeProps
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.BoxScope
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.UiDslMarker
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.remember

/** Logical direction used by an [AnimatedContentTransitionScope] slide primitive. */
enum class ContentSlideDirection {
    /** Logical start edge, resolved from the current layout direction. */
    Start,

    /** Logical end edge, resolved from the current layout direction. */
    End,

    /** Physical top edge. */
    Up,

    /** Physical bottom edge. */
    Down,
}

/**
 * Defines how the replacement container changes from the outgoing size to the incoming size.
 *
 * The renderer measures both items under the same parent constraints. When this object is present,
 * [animationSpec] drives a normalized interpolation from the last committed container size to the
 * incoming measured size. Removing the transform makes the container use the maximum current item
 * size instead.
 *
 * @sample com.viewcompose.animation.samples.animatedContentSample
 *
 * @property animationSpec timing policy for normalized container-size progress
 * @property clip whether content outside the interpolated container bounds is clipped
 */
data class SizeTransform(
    val animationSpec: FiniteAnimationSpec = tween(),
    val clip: Boolean = true,
)

/**
 * Combines the incoming, outgoing, size, and drawing-order policy for one content replacement.
 *
 * [targetContentZIndex] is applied only to the incoming item. The outgoing item retains the finite
 * z-index assigned when it entered; equal z values preserve declaration order, so incoming content
 * draws above outgoing content by default.
 *
 * @sample com.viewcompose.animation.samples.animatedContentSample
 *
 * @property targetContentEnter transition applied to the incoming content item
 * @property initialContentExit transition applied to the outgoing content item
 * @property sizeTransform optional container-size interpolation, or `null` for maximum child size
 * @property targetContentZIndex finite drawing-order contribution for the incoming item
 * @throws IllegalArgumentException if [targetContentZIndex] is non-finite
 */
data class ContentTransform(
    val targetContentEnter: EnterTransition = fadeIn(),
    val initialContentExit: ExitTransition = fadeOut(),
    val sizeTransform: SizeTransform? = SizeTransform(),
    val targetContentZIndex: Float = 0f,
) {
    init {
        require(targetContentZIndex.isFinite()) {
            "ContentTransform.targetContentZIndex must be finite."
        }
    }
}

/**
 * Creates a content transform from an incoming transition and [exit] policy.
 *
 * @sample com.viewcompose.animation.samples.animatedContentSample
 *
 * @receiver transition applied to incoming content
 * @param exit transition applied to outgoing content
 * @return content transform with the default size policy
 */
infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform {
    return ContentTransform(
        targetContentEnter = this,
        initialContentExit = exit,
    )
}

/**
 * Returns this content transform with [sizeTransform] replacing its size policy.
 *
 * @sample com.viewcompose.animation.samples.animatedContentSample
 *
 * @receiver content transform to copy
 * @param sizeTransform replacement size policy, or `null` for maximum current child size
 * @return copied content transform
 */
infix fun ContentTransform.using(sizeTransform: SizeTransform?): ContentTransform {
    return copy(sizeTransform = sizeTransform)
}

/**
 * Typed initial/target-pair scope used to choose one [ContentTransform].
 *
 * Slide offsets are fractions of the entering or exiting item's measured size. Start/end are
 * resolved against the layout direction captured for this replacement segment; a direction change
 * starts with the next logical segment rather than reversing an active frame midway.
 *
 * @param S logical content state type
 * @property initialState state represented by the outgoing item
 * @property targetState state represented by the incoming item
 */
@UiDslMarker
class AnimatedContentTransitionScope<S> internal constructor(
    val initialState: S,
    val targetState: S,
    private val layoutDirection: UiLayoutDirection,
) {
    /**
     * Returns whether this scope represents the exact [initialState]-to-[targetState] pair.
     *
     * @sample com.viewcompose.animation.samples.animatedContentSample
     *
     * @param initialState expected outgoing state
     * @param targetState expected incoming state
     * @return `true` only when both states equal this segment's typed endpoints
     */
    fun isTransitioningTo(initialState: S, targetState: S): Boolean {
        return this.initialState == initialState && this.targetState == targetState
    }

    /**
     * Slides incoming content from [from] by [distanceFraction] of its measured axis.
     *
     * @sample com.viewcompose.animation.samples.animatedContentSample
     *
     * @param from edge from which incoming content travels
     * @param animationSpec timing policy for both translation axes
     * @param distanceFraction non-negative finite fraction of measured width or height
     * @return one content-scoped enter transition
     * @throws IllegalArgumentException if [distanceFraction] is negative or non-finite
     */
    fun slideIntoContainer(
        from: ContentSlideDirection,
        animationSpec: FiniteAnimationSpec = tween(),
        distanceFraction: Float = 1f,
    ): EnterTransition {
        require(distanceFraction.isFinite() && distanceFraction >= 0f) {
            "slideIntoContainer distanceFraction must be finite and >= 0."
        }
        val offset = from.resolveOffset(layoutDirection, distanceFraction)
        return EnterTransition(
            elements = listOf(
                ContentEnterElement.Slide(
                    animationSpec = animationSpec,
                    initialOffsetXFraction = offset.first,
                    initialOffsetYFraction = offset.second,
                ),
            ),
        )
    }

    /**
     * Slides outgoing content toward [towards] by [distanceFraction] of its measured axis.
     *
     * @sample com.viewcompose.animation.samples.animatedContentSample
     *
     * @param towards edge toward which outgoing content travels
     * @param animationSpec timing policy for both translation axes
     * @param distanceFraction non-negative finite fraction of measured width or height
     * @return one content-scoped exit transition
     * @throws IllegalArgumentException if [distanceFraction] is negative or non-finite
     */
    fun slideOutOfContainer(
        towards: ContentSlideDirection,
        animationSpec: FiniteAnimationSpec = tween(),
        distanceFraction: Float = 1f,
    ): ExitTransition {
        require(distanceFraction.isFinite() && distanceFraction >= 0f) {
            "slideOutOfContainer distanceFraction must be finite and >= 0."
        }
        val offset = towards.resolveOffset(layoutDirection, distanceFraction)
        return ExitTransition(
            elements = listOf(
                ContentExitElement.Slide(
                    animationSpec = animationSpec,
                    targetOffsetXFraction = offset.first,
                    targetOffsetYFraction = offset.second,
                ),
            ),
        )
    }

    /**
     * Scales incoming content from [initialScale] around [transformOrigin].
     *
     * @sample com.viewcompose.animation.samples.animatedContentSample
     *
     * @param initialScale finite scale at the start of the enter segment
     * @param transformOrigin fractional scale pivot
     * @param animationSpec timing policy for both scale axes
     * @return one content-scoped enter transition
     * @throws IllegalArgumentException if the scale or either origin fraction is non-finite
     */
    fun scaleIn(
        initialScale: Float = 0.92f,
        transformOrigin: TransformOrigin = TransformOrigin.Center,
        animationSpec: FiniteAnimationSpec = tween(),
    ): EnterTransition {
        requireScaleValues(initialScale, transformOrigin, "scaleIn")
        return EnterTransition(
            elements = listOf(
                ContentEnterElement.Scale(
                    animationSpec = animationSpec,
                    initialScale = initialScale,
                    transformOrigin = transformOrigin,
                ),
            ),
        )
    }

    /**
     * Scales outgoing content to [targetScale] around [transformOrigin].
     *
     * @sample com.viewcompose.animation.samples.animatedContentSample
     *
     * @param targetScale finite scale at the end of the exit segment
     * @param transformOrigin fractional scale pivot
     * @param animationSpec timing policy for both scale axes
     * @return one content-scoped exit transition
     * @throws IllegalArgumentException if the scale or either origin fraction is non-finite
     */
    fun scaleOut(
        targetScale: Float = 0.92f,
        transformOrigin: TransformOrigin = TransformOrigin.Center,
        animationSpec: FiniteAnimationSpec = tween(),
    ): ExitTransition {
        requireScaleValues(targetScale, transformOrigin, "scaleOut")
        return ExitTransition(
            elements = listOf(
                ContentExitElement.Scale(
                    animationSpec = animationSpec,
                    targetScale = targetScale,
                    transformOrigin = transformOrigin,
                ),
            ),
        )
    }
}

/**
 * Typed builder scope used to emit one outgoing or incoming content subtree.
 *
 * [isTargetContent] is `true` only for the input/focus/accessibility-owning incoming item. Both
 * scopes expose the same logical segment endpoints, including during interruption.
 *
 * @sample com.viewcompose.animation.samples.animatedContentSample
 *
 * @param S logical content state type
 * @property initialState state represented by the outgoing item for this segment
 * @property targetState state represented by the incoming item for this segment
 * @property isTargetContent whether this subtree is the active incoming owner
 */
@UiDslMarker
class AnimatedContentScope<S> internal constructor(
    val initialState: S,
    val targetState: S,
    val isTargetContent: Boolean,
) : UiTreeBuilder()

private sealed interface ContentEnterElement : EnterTransitionElement {
    data class Slide(
        val animationSpec: FiniteAnimationSpec,
        val initialOffsetXFraction: Float,
        val initialOffsetYFraction: Float,
    ) : ContentEnterElement

    data class Scale(
        val animationSpec: FiniteAnimationSpec,
        val initialScale: Float,
        val transformOrigin: TransformOrigin,
    ) : ContentEnterElement
}

private sealed interface ContentExitElement : ExitTransitionElement {
    data class Slide(
        val animationSpec: FiniteAnimationSpec,
        val targetOffsetXFraction: Float,
        val targetOffsetYFraction: Float,
    ) : ContentExitElement

    data class Scale(
        val animationSpec: FiniteAnimationSpec,
        val targetScale: Float,
        val transformOrigin: TransformOrigin,
    ) : ContentExitElement
}

private data class ContentIdentity(val value: Any?)

private data class ContentRequest<S>(
    val identity: ContentIdentity,
    val state: S,
)

private data class ContentEntry<S>(
    val identity: ContentIdentity,
    val state: S,
    val zIndex: Float,
)

private data class ContentPair<S>(
    val outgoing: ContentEntry<S>?,
    val incoming: ContentEntry<S>,
    val transform: ContentTransform,
)

private data class ContentVisual(
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationXFraction: Float = 0f,
    val translationYFraction: Float = 0f,
    val revealWidthFraction: Float = 1f,
    val revealHeightFraction: Float = 1f,
    val transformOrigin: TransformOrigin = TransformOrigin.Center,
)

private class CommittedAnimatedContentState<S>(
    var pair: ContentPair<S>,
    var incomingVisual: ContentVisual,
)

/**
 * Replaces keyed state content with pair-specific enter, exit, size, and drawing-order policies.
 *
 * [contentKey] is subtree identity. Equal keys patch one retained subtree without a replacement
 * transition, including for nullable states. A replacement retains at most an outgoing and
 * incoming subtree; if a new target arrives during A-to-B, B becomes the outgoing subtree from its
 * last committed visual sample and A is released. Incoming content owns pointer input, focus, and
 * accessibility as soon as the replacement tree commits, while outgoing content remains only
 * renderable until every participating channel settles.
 *
 * A changed target is admitted after the current candidate tree commits successfully. This keeps
 * an aborted renderer apply from mutating replacement identity, channel ownership, descendant
 * effects, or focus state; the accepted request invalidates composition and starts its segment on
 * the following frame.
 *
 * Both items are measured under the same parent constraints. A non-null [SizeTransform]
 * interpolates from the last committed container dimensions to the incoming dimensions; `null`
 * uses the maximum current item size. Candidate render failure leaves the previously committed
 * native pair and interaction owner authoritative, and leaving composition cancels the shared
 * frame loop and disposes both subtrees.
 * [transitionSpec], [contentKey], and [content] run synchronously on the composition thread and
 * must not perform blocking work. The transition selector runs once for each accepted unequal-key
 * segment; [content] runs for each currently retained subtree.
 *
 * @sample com.viewcompose.animation.samples.animatedContentSample
 *
 * @param S logical content state type
 * @receiver tree builder for the current composition
 * @param targetState latest logical state to render
 * @param modifier modifier applied to the replacement container
 * @param contentAlignment logical placement shared by outgoing and incoming items
 * @param contentKey stable identity selector; equal results suppress replacement
 * @param transitionSpec pair-specific transition evaluated once per replacement segment
 * @param content typed subtree builder invoked once while settled and twice during replacement
 */
fun <S> UiTreeBuilder.AnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    contentAlignment: BoxAlignment = BoxAlignment.TopStart,
    contentKey: (S) -> Any? = { it },
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        fadeIn() togetherWith fadeOut()
    },
    content: AnimatedContentScope<S>.(S) -> Unit,
) {
    val externalRequest = ContentRequest(
        identity = ContentIdentity(contentKey(targetState)),
        state = targetState,
    )
    val acceptedRequest = remember {
        mutableStateOf(externalRequest)
    }
    val requested = acceptedRequest.value
    val committed = remember {
        val initialEntry = ContentEntry(
            identity = requested.identity,
            state = requested.state,
            zIndex = 0f,
        )
        CommittedAnimatedContentState(
            pair = ContentPair(
                outgoing = null,
                incoming = initialEntry,
                transform = ContentTransform(),
            ),
            incomingVisual = ContentVisual(),
        )
    }
    val replacingIdentity = committed.pair.incoming.identity != requested.identity
    val transition = updateTransition(
        targetState = requested.identity,
        label = "animated_content",
    )
    val segmentVersion = transition.runtimeSegmentVersion()
    val runtimeRunning = transition.runtimeIsRunning()
    val previousIncoming = committed.pair.incoming
    val transform = remember(segmentVersion) {
        val layoutDirection = Environment.layoutDirection
        if (replacingIdentity) {
            AnimatedContentTransitionScope(
                initialState = previousIncoming.state,
                targetState = requested.state,
                layoutDirection = layoutDirection,
            ).transitionSpec()
        } else {
            committed.pair.transform
        }
    }
    val incoming = ContentEntry(
        identity = requested.identity,
        state = requested.state,
        zIndex = if (replacingIdentity) transform.targetContentZIndex else previousIncoming.zIndex,
    )
    val pair = if (replacingIdentity) {
        ContentPair(
            outgoing = previousIncoming,
            incoming = incoming,
            transform = transform,
        )
    } else {
        committed.pair.copy(
            incoming = incoming,
        )
    }
    val outgoingStart = if (replacingIdentity) committed.incomingVisual else ContentVisual()
    val sampled = transition.sampleContentVisuals(
        transform = transform,
        outgoingStart = outgoingStart,
    )
    val renderedOutgoing = pair.outgoing.takeIf { runtimeRunning }
    emit(
        type = NodeType.AnimatedContentHost,
        spec = AnimatedContentHostNodeProps(
            segmentId = segmentVersion,
            sizeProgress = sampled.sizeProgress,
            sizeTransformEnabled = runtimeRunning && transform.sizeTransform != null,
            clipToBounds = runtimeRunning && transform.sizeTransform?.clip == true,
            contentAlignment = contentAlignment,
        ),
        modifier = modifier,
    ) {
        renderedOutgoing?.let { outgoing ->
            emitAnimatedContentItem(
                entry = outgoing,
                visual = sampled.outgoing,
                active = false,
                initialState = outgoing.state,
                targetState = incoming.state,
                content = content,
            )
        }
        emitAnimatedContentItem(
            entry = incoming,
            visual = if (runtimeRunning) sampled.incoming else ContentVisual(),
            active = true,
            initialState = renderedOutgoing?.state ?: incoming.state,
            targetState = incoming.state,
            content = content,
        )
    }
    SideEffect {
        committed.pair = if (runtimeRunning) {
            pair
        } else {
            pair.copy(outgoing = null)
        }
        committed.incomingVisual = if (runtimeRunning) sampled.incoming else ContentVisual()
        if (acceptedRequest.value != externalRequest) {
            acceptedRequest.value = externalRequest
        }
    }
}

private data class SampledContentVisuals(
    val outgoing: ContentVisual,
    val incoming: ContentVisual,
    val sizeProgress: Float,
)

private fun Transition<ContentIdentity>.sampleContentVisuals(
    transform: ContentTransform,
    outgoingStart: ContentVisual,
): SampledContentVisuals {
    val enter = transform.targetContentEnter
    val exit = transform.initialContentExit
    val enterFade = enter.elements.filterIsInstance<EnterTransitionElement.Fade>().lastOrNull()
    val exitFade = exit.elements.filterIsInstance<ExitTransitionElement.Fade>().lastOrNull()
    val enterSlide = enter.elements.filterIsInstance<ContentEnterElement.Slide>().lastOrNull()
    val exitSlide = exit.elements.filterIsInstance<ContentExitElement.Slide>().lastOrNull()
    val enterScale = enter.elements.filterIsInstance<ContentEnterElement.Scale>().lastOrNull()
    val exitScale = exit.elements.filterIsInstance<ContentExitElement.Scale>().lastOrNull()
    val enterWidthExpand = enter.findExpandForWidthAxis()
    val enterHeightExpand = enter.findExpandForHeightAxis()
    val exitWidthShrink = exit.findShrinkForWidthAxis()
    val exitHeightShrink = exit.findShrinkForHeightAxis()

    fun incomingChannel(
        initialValue: Float,
        animationSpec: FiniteAnimationSpec?,
        settledValue: Float,
    ) = sampleFloatBySegment(
        transitionSpec = { _, _ -> animationSpec ?: snap() },
        segmentEndpoints = { _, _, _ -> initialValue to settledValue },
        valueForSettledState = { settledValue },
    )

    fun outgoingChannel(
        initialValue: Float,
        targetValue: Float,
        animationSpec: FiniteAnimationSpec?,
    ) = sampleFloatBySegment(
        transitionSpec = { _, _ -> animationSpec ?: snap() },
        segmentEndpoints = { _, _, _ -> initialValue to targetValue },
        valueForSettledState = { targetValue },
    )

    val incoming = ContentVisual(
        alpha = incomingChannel(enterFade?.initialAlpha ?: 1f, enterFade?.animationSpec, 1f),
        scaleX = incomingChannel(enterScale?.initialScale ?: 1f, enterScale?.animationSpec, 1f),
        scaleY = incomingChannel(enterScale?.initialScale ?: 1f, enterScale?.animationSpec, 1f),
        translationXFraction = incomingChannel(
            enterSlide?.initialOffsetXFraction ?: 0f,
            enterSlide?.animationSpec,
            0f,
        ),
        translationYFraction = incomingChannel(
            enterSlide?.initialOffsetYFraction ?: 0f,
            enterSlide?.animationSpec,
            0f,
        ),
        revealWidthFraction = incomingChannel(
            enterWidthExpand?.initialScale ?: 1f,
            enterWidthExpand?.animationSpec,
            1f,
        ),
        revealHeightFraction = incomingChannel(
            enterHeightExpand?.initialScale ?: 1f,
            enterHeightExpand?.animationSpec,
            1f,
        ),
        transformOrigin = enterScale?.transformOrigin ?: TransformOrigin.Center,
    )
    val outgoing = ContentVisual(
        alpha = outgoingChannel(
            outgoingStart.alpha,
            exitFade?.targetAlpha ?: outgoingStart.alpha,
            exitFade?.animationSpec,
        ),
        scaleX = outgoingChannel(
            outgoingStart.scaleX,
            exitScale?.targetScale ?: outgoingStart.scaleX,
            exitScale?.animationSpec,
        ),
        scaleY = outgoingChannel(
            outgoingStart.scaleY,
            exitScale?.targetScale ?: outgoingStart.scaleY,
            exitScale?.animationSpec,
        ),
        translationXFraction = outgoingChannel(
            outgoingStart.translationXFraction,
            exitSlide?.targetOffsetXFraction ?: outgoingStart.translationXFraction,
            exitSlide?.animationSpec,
        ),
        translationYFraction = outgoingChannel(
            outgoingStart.translationYFraction,
            exitSlide?.targetOffsetYFraction ?: outgoingStart.translationYFraction,
            exitSlide?.animationSpec,
        ),
        revealWidthFraction = outgoingChannel(
            outgoingStart.revealWidthFraction,
            exitWidthShrink?.targetScale ?: outgoingStart.revealWidthFraction,
            exitWidthShrink?.animationSpec,
        ),
        revealHeightFraction = outgoingChannel(
            outgoingStart.revealHeightFraction,
            exitHeightShrink?.targetScale ?: outgoingStart.revealHeightFraction,
            exitHeightShrink?.animationSpec,
        ),
        transformOrigin = exitScale?.transformOrigin ?: outgoingStart.transformOrigin,
    )
    val sizeProgress = incomingChannel(
        initialValue = 0f,
        animationSpec = transform.sizeTransform?.animationSpec,
        settledValue = 1f,
    )
    return SampledContentVisuals(
        outgoing = outgoing,
        incoming = incoming,
        sizeProgress = sizeProgress,
    )
}

private fun <S> UiTreeBuilder.emitAnimatedContentItem(
    entry: ContentEntry<S>,
    visual: ContentVisual,
    active: Boolean,
    initialState: S,
    targetState: S,
    content: AnimatedContentScope<S>.(S) -> Unit,
) {
    key(entry.identity) {
        emitScoped(
            type = NodeType.AnimatedContentItemHost,
            key = entry.identity,
            inputs = listOf(visual, active, initialState, targetState, entry.state),
            modifier = Modifier.zIndex(entry.zIndex),
            scopeFactory = {
                AnimatedContentScope(
                    initialState = initialState,
                    targetState = targetState,
                    isTargetContent = active,
                )
            },
            spec = {
                AnimatedContentItemNodeProps(
                    alpha = visual.alpha,
                    scaleX = visual.scaleX,
                    scaleY = visual.scaleY,
                    translationXFraction = visual.translationXFraction,
                    translationYFraction = visual.translationYFraction,
                    revealWidthFraction = visual.revealWidthFraction,
                    revealHeightFraction = visual.revealHeightFraction,
                    transformOrigin = visual.transformOrigin,
                    active = active,
                )
            },
            content = {
                content(entry.state)
            },
        )
    }
}

private fun ContentSlideDirection.resolveOffset(
    layoutDirection: UiLayoutDirection,
    distanceFraction: Float,
): Pair<Float, Float> {
    return when (this) {
        ContentSlideDirection.Start -> {
            val sign = if (layoutDirection == UiLayoutDirection.Ltr) -1f else 1f
            Pair(sign * distanceFraction, 0f)
        }

        ContentSlideDirection.End -> {
            val sign = if (layoutDirection == UiLayoutDirection.Ltr) 1f else -1f
            Pair(sign * distanceFraction, 0f)
        }

        ContentSlideDirection.Up -> Pair(0f, -distanceFraction)
        ContentSlideDirection.Down -> Pair(0f, distanceFraction)
    }
}

private fun requireScaleValues(
    scale: Float,
    transformOrigin: TransformOrigin,
    operation: String,
) {
    require(
        scale.isFinite() &&
            transformOrigin.pivotFractionX.isFinite() &&
            transformOrigin.pivotFractionY.isFinite(),
    ) {
        "$operation scale and transform origin must be finite."
    }
}

/**
 * Internal cross-fade engine retained behind the single public [Crossfade] contract.
 *
 * A state change keeps the outgoing subtree mounted while the incoming subtree is rendered above it
 * in a fill-size [Box]. [content] is therefore invoked twice during a transition and once while
 * settled. The outgoing state is committed and removed in a [SideEffect] after its alpha reaches the
 * terminal threshold, so tree mutation does not occur during composition.
 *
 * Equality determines whether a transition is needed. If another target arrives mid-transition,
 * incoming content switches to the latest target at the existing progress rather than restarting
 * from zero; the last committed state remains the outgoing content. [DisplayedState] keeps the
 * mounted value distinct from transition absence, so nullable states retain the same lifecycle.
 *
 * This release animates alpha only; it does not provide size transforms, content keys, or per-pair
 * transition scopes. Descendant state must be keyed by [targetState] when separate state per content
 * identity is required.
 *
 * @param T logical state used to produce content
 * @receiver tree builder for the current composition
 * @param targetState state whose content should become fully visible
 * @param modifier modifier applied to the outer stacking container
 * @param animationSpec progress animation specification
 * @param content content rendered with either the outgoing or incoming state
 */
private fun <T> UiTreeBuilder.crossfadeContent(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec,
    content: BoxScope.(T) -> Unit,
) {
    val displayedState = remember {
        mutableStateOf(DisplayedState(targetState))
    }
    val hasPendingTransition = targetState != displayedState.value.value
    val outgoingState = displayedState.value.takeIf { hasPendingTransition }
    val progress = animateFloatAsState(
        targetValue = if (hasPendingTransition) 1f else 0f,
        animationSpec = animationSpec,
    )
    val incomingAlpha = if (hasPendingTransition) {
        progress.value
    } else {
        1f
    }.coerceIn(0f, 1f)
    val outgoingAlpha = 1f - incomingAlpha
    if (hasPendingTransition && outgoingAlpha <= 0.001f) {
        // Commit after rendering so the outgoing subtree remains mounted through the terminal frame.
        SideEffect {
            displayedState.value = DisplayedState(targetState)
        }
    }
    Box(
        modifier = modifier,
    ) {
        outgoingState?.let { previous ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(outgoingAlpha),
            ) {
                content(previous.value)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(incomingAlpha),
        ) {
            content(targetState)
        }
    }
}

private data class DisplayedState<T>(val value: T)

/**
 * Cross-fades state content using one fixed [animationSpec].
 *
 * A state change keeps the outgoing subtree mounted while the incoming subtree is rendered above
 * it in a fill-size Box. Equality controls transitions, nullable states are supported, and a new
 * target during a transition replaces the incoming content without discarding the last committed
 * outgoing state. This API intentionally promises alpha cross-fading only.
 *
 * @sample com.viewcompose.animation.samples.crossfadeSample
 *
 * @param T logical state used to produce content
 * @receiver tree builder for the current composition
 * @param targetState state whose content should become fully visible
 * @param modifier modifier applied to the outer stacking container
 * @param animationSpec fixed specification used for cross-fade progress
 * @param content content rendered with the outgoing or incoming state
 */
fun <T> UiTreeBuilder.Crossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec = tween(),
    content: BoxScope.(T) -> Unit,
) {
    crossfadeContent(
        targetState = targetState,
        modifier = modifier,
        animationSpec = animationSpec,
        content = content,
    )
}
