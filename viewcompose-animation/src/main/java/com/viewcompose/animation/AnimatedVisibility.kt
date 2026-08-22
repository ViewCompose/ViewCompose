package com.viewcompose.animation

import com.viewcompose.animation.core.FiniteAnimationSpec
import com.viewcompose.animation.core.snap
import com.viewcompose.animation.core.tween
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.BoxScope
import com.viewcompose.ui.foundation.ColumnScope
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.RowScope
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.UiDslMarker
import com.viewcompose.ui.foundation.remember
import kotlin.math.abs

/**
 * Selects the measured edge used by slide enter and exit transitions.
 *
 * [Start] and [End] resolve from the layout direction captured when a visibility segment starts;
 * [Up] and [Down] are physical edges. A running segment therefore does not reverse if the ambient
 * direction changes before it settles.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 */
enum class SlideDirection {
    /** Logical start edge, resolved from the segment's layout direction. */
    Start,

    /** Logical end edge, resolved from the segment's layout direction. */
    End,

    /** Physical top edge. */
    Up,

    /** Physical bottom edge. */
    Down,
}

/** Selects which measured axes participate in an expand or shrink transition. */
enum class SizeTransformAxis {
    /** Scales both measured width and height. */
    Both,

    /** Scales measured width while height remains at its full size. */
    Horizontal,

    /** Scales measured height while width remains at its full size. */
    Vertical,
}

/**
 * Defines one primitive that can contribute to an [EnterTransition].
 *
 * Consumers normally compose [fadeIn], [expandIn], [slideIn], and [scaleIn] helpers instead of
 * constructing elements directly. Duplicate channel ownership follows ordered last-element-wins
 * semantics.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 */
sealed interface EnterTransitionElement {
    /**
     * Animates host alpha from [initialAlpha] to fully opaque.
     *
     * @property animationSpec timing policy for the alpha channel
     * @property initialAlpha requested alpha at the start of a fully hidden enter; rendered alpha is
     * clamped to `0f..1f`
     */
    data class Fade(
        val animationSpec: FiniteAnimationSpec = tween(),
        val initialAlpha: Float = 0f,
    ) : EnterTransitionElement {
        init {
            require(initialAlpha.isFinite()) { "Enter fade alpha must be finite." }
        }
    }

    /**
     * Animates measured size from [initialScale] to full size on [axis].
     *
     * The renderer clips content to the animated bounds. Negative scales render as zero.
     *
     * @property animationSpec timing policy shared by each affected size channel
     * @property initialScale fraction of full measured size at the start of a fully hidden enter
     * @property axis measured axes affected by the element
     * @property alignment edge that remains stable while the measured bounds expand
     */
    data class Expand(
        val animationSpec: FiniteAnimationSpec = tween(),
        val initialScale: Float = 0f,
        val axis: SizeTransformAxis = SizeTransformAxis.Both,
        val alignment: BoxAlignment = BoxAlignment.TopStart,
    ) : EnterTransitionElement {
        init {
            require(initialScale.isFinite()) { "Enter expand scale must be finite." }
        }
    }

    /**
     * Animates translation from one logical measured edge to the settled position.
     *
     * @property animationSpec timing policy for the translation channel
     * @property direction logical or physical edge from which content enters
     * @property distanceFraction non-negative distance as a fraction of measured content size
     */
    data class Slide(
        val animationSpec: FiniteAnimationSpec = tween(),
        val direction: SlideDirection = SlideDirection.Start,
        val distanceFraction: Float = 1f,
    ) : EnterTransitionElement {
        init {
            require(distanceFraction.isFinite() && distanceFraction >= 0f) {
                "Enter slide distance fraction must be finite and >= 0."
            }
        }
    }

    /**
     * Animates visual scale from [initialScale] to one around [transformOrigin].
     *
     * @property animationSpec timing policy for both visual scale axes
     * @property initialScale finite visual scale at the hidden endpoint
     * @property transformOrigin fractional scale pivot
     */
    data class Scale(
        val animationSpec: FiniteAnimationSpec = tween(),
        val initialScale: Float = 0.92f,
        val transformOrigin: TransformOrigin = TransformOrigin.Center,
    ) : EnterTransitionElement {
        init {
            requireVisibilityScale(initialScale, transformOrigin, "Enter scale")
        }
    }
}

/**
 * Defines one primitive that can contribute to an [ExitTransition].
 *
 * Consumers normally compose [fadeOut], [shrinkOut], [slideOut], and [scaleOut] helpers instead of
 * constructing elements directly. Duplicate channel ownership follows ordered last-element-wins
 * semantics.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 */
sealed interface ExitTransitionElement {
    /**
     * Animates host alpha from its current value to [targetAlpha].
     *
     * @property animationSpec timing policy for the alpha channel
     * @property targetAlpha requested hidden alpha; rendered alpha is clamped to `0f..1f`
     */
    data class Fade(
        val animationSpec: FiniteAnimationSpec = tween(),
        val targetAlpha: Float = 0f,
    ) : ExitTransitionElement {
        init {
            require(targetAlpha.isFinite()) { "Exit fade alpha must be finite." }
        }
    }

    /**
     * Animates measured size from its current value to [targetScale] on [axis].
     *
     * The renderer clips content to the animated bounds. Negative scales render as zero.
     *
     * @property animationSpec timing policy shared by each affected size channel
     * @property targetScale fraction of full measured size at the hidden endpoint
     * @property axis measured axes affected by the element
     * @property alignment edge that remains stable while the measured bounds shrink
     */
    data class Shrink(
        val animationSpec: FiniteAnimationSpec = tween(),
        val targetScale: Float = 0f,
        val axis: SizeTransformAxis = SizeTransformAxis.Both,
        val alignment: BoxAlignment = BoxAlignment.TopStart,
    ) : ExitTransitionElement {
        init {
            require(targetScale.isFinite()) { "Exit shrink scale must be finite." }
        }
    }

    /**
     * Animates translation from the settled position toward one logical measured edge.
     *
     * @property animationSpec timing policy for the translation channel
     * @property direction logical or physical edge toward which content exits
     * @property distanceFraction non-negative distance as a fraction of measured content size
     */
    data class Slide(
        val animationSpec: FiniteAnimationSpec = tween(),
        val direction: SlideDirection = SlideDirection.End,
        val distanceFraction: Float = 1f,
    ) : ExitTransitionElement {
        init {
            require(distanceFraction.isFinite() && distanceFraction >= 0f) {
                "Exit slide distance fraction must be finite and >= 0."
            }
        }
    }

    /**
     * Animates visual scale from one to [targetScale] around [transformOrigin].
     *
     * @property animationSpec timing policy for both visual scale axes
     * @property targetScale finite visual scale at the hidden endpoint
     * @property transformOrigin fractional scale pivot
     */
    data class Scale(
        val animationSpec: FiniteAnimationSpec = tween(),
        val targetScale: Float = 0.92f,
        val transformOrigin: TransformOrigin = TransformOrigin.Center,
    ) : ExitTransitionElement {
        init {
            requireVisibilityScale(targetScale, transformOrigin, "Exit scale")
        }
    }
}

/**
 * Collects enter primitives applied by [AnimatedVisibility].
 *
 * Elements are interpreted by alpha, measured-size, translation, and visual-scale channel. The
 * last applicable element in [elements] wins when a channel appears more than once.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @property elements ordered primitives available to the visibility host
 */
data class EnterTransition(
    val elements: List<EnterTransitionElement>,
) {
    companion object {
        /** Transition with no parent-owned enter channel. */
        val None: EnterTransition = EnterTransition(emptyList())
    }

    /**
     * Returns a transition whose ordered elements are this transition followed by [other].
     *
     * Because the last applicable element wins, [other] overrides duplicate channels.
     *
     * @param other transition appended after this transition
     * @return a new transition containing both element lists
     */
    operator fun plus(other: EnterTransition): EnterTransition {
        return EnterTransition(elements + other.elements)
    }
}

/**
 * Collects exit primitives applied by [AnimatedVisibility].
 *
 * Elements are interpreted by alpha, measured-size, translation, and visual-scale channel. The
 * last applicable element in [elements] wins when a channel appears more than once.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @property elements ordered primitives available to the visibility host
 */
data class ExitTransition(
    val elements: List<ExitTransitionElement>,
) {
    companion object {
        /** Transition with no parent-owned exit channel. */
        val None: ExitTransition = ExitTransition(emptyList())
    }

    /**
     * Returns a transition whose ordered elements are this transition followed by [other].
     *
     * Because the last applicable element wins, [other] overrides duplicate channels.
     *
     * @param other transition appended after this transition
     * @return a new transition containing both element lists
     */
    operator fun plus(other: ExitTransition): ExitTransition {
        return ExitTransition(elements + other.elements)
    }
}

/**
 * Creates an enter transition that fades from [initialAlpha] to fully opaque.
 *
 * @sample com.viewcompose.animation.samples.visibilityTransitionsSample
 *
 * @param animationSpec timing policy for alpha
 * @param initialAlpha alpha used when entering from the fully hidden endpoint
 * @return a one-element enter transition
 */
fun fadeIn(
    animationSpec: FiniteAnimationSpec = tween(),
    initialAlpha: Float = 0f,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Fade(
            animationSpec = animationSpec,
            initialAlpha = initialAlpha,
        ),
    ),
)

/**
 * Creates an enter transition that expands both measured axes to full size.
 *
 * @sample com.viewcompose.animation.samples.visibilityTransitionsSample
 *
 * @param animationSpec timing policy for width and height scale
 * @param initialScale initial fraction of full measured width and height
 * @param alignment edge that remains stable while measured bounds expand
 * @return a one-element enter transition
 */
fun expandIn(
    animationSpec: FiniteAnimationSpec = tween(),
    initialScale: Float = 0f,
    alignment: BoxAlignment = BoxAlignment.TopStart,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Expand(
            animationSpec = animationSpec,
            initialScale = initialScale,
            axis = SizeTransformAxis.Both,
            alignment = alignment,
        ),
    ),
)

/**
 * Creates an enter transition that expands measured width to full size.
 *
 * @param animationSpec timing policy for width scale
 * @param initialScale initial fraction of full measured width
 * @param alignment edge that remains stable while measured bounds expand
 * @return a one-element horizontal enter transition
 */
fun expandHorizontally(
    animationSpec: FiniteAnimationSpec = tween(),
    initialScale: Float = 0f,
    alignment: BoxAlignment = BoxAlignment.TopStart,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Expand(
            animationSpec = animationSpec,
            initialScale = initialScale,
            axis = SizeTransformAxis.Horizontal,
            alignment = alignment,
        ),
    ),
)

/**
 * Creates an enter transition that expands measured height to full size.
 *
 * @param animationSpec timing policy for height scale
 * @param initialScale initial fraction of full measured height
 * @param alignment edge that remains stable while measured bounds expand
 * @return a one-element vertical enter transition
 */
fun expandVertically(
    animationSpec: FiniteAnimationSpec = tween(),
    initialScale: Float = 0f,
    alignment: BoxAlignment = BoxAlignment.TopStart,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Expand(
            animationSpec = animationSpec,
            initialScale = initialScale,
            axis = SizeTransformAxis.Vertical,
            alignment = alignment,
        ),
    ),
)

/**
 * Creates an exit transition that fades from the current alpha to [targetAlpha].
 *
 * @sample com.viewcompose.animation.samples.visibilityTransitionsSample
 *
 * @param animationSpec timing policy for alpha
 * @param targetAlpha alpha at the hidden endpoint
 * @return a one-element exit transition
 */
fun fadeOut(
    animationSpec: FiniteAnimationSpec = tween(),
    targetAlpha: Float = 0f,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Fade(
            animationSpec = animationSpec,
            targetAlpha = targetAlpha,
        ),
    ),
)

/**
 * Creates an exit transition that shrinks both measured axes to [targetScale].
 *
 * @sample com.viewcompose.animation.samples.visibilityTransitionsSample
 *
 * @param animationSpec timing policy for width and height scale
 * @param targetScale terminal fraction of full measured width and height
 * @param alignment edge that remains stable while measured bounds shrink
 * @return a one-element exit transition
 */
fun shrinkOut(
    animationSpec: FiniteAnimationSpec = tween(),
    targetScale: Float = 0f,
    alignment: BoxAlignment = BoxAlignment.TopStart,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Shrink(
            animationSpec = animationSpec,
            targetScale = targetScale,
            axis = SizeTransformAxis.Both,
            alignment = alignment,
        ),
    ),
)

/**
 * Creates an exit transition that shrinks measured width to [targetScale].
 *
 * @param animationSpec timing policy for width scale
 * @param targetScale terminal fraction of full measured width
 * @param alignment edge that remains stable while measured bounds shrink
 * @return a one-element horizontal exit transition
 */
fun shrinkHorizontally(
    animationSpec: FiniteAnimationSpec = tween(),
    targetScale: Float = 0f,
    alignment: BoxAlignment = BoxAlignment.TopStart,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Shrink(
            animationSpec = animationSpec,
            targetScale = targetScale,
            axis = SizeTransformAxis.Horizontal,
            alignment = alignment,
        ),
    ),
)

/**
 * Creates an exit transition that shrinks measured height to [targetScale].
 *
 * @param animationSpec timing policy for height scale
 * @param targetScale terminal fraction of full measured height
 * @param alignment edge that remains stable while measured bounds shrink
 * @return a one-element vertical exit transition
 */
fun shrinkVertically(
    animationSpec: FiniteAnimationSpec = tween(),
    targetScale: Float = 0f,
    alignment: BoxAlignment = BoxAlignment.TopStart,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Shrink(
            animationSpec = animationSpec,
            targetScale = targetScale,
            axis = SizeTransformAxis.Vertical,
            alignment = alignment,
        ),
    ),
)

/**
 * Creates an enter transition that slides from [from] by [distanceFraction] of measured size.
 *
 * Start and end resolve from the layout direction captured when the owning visibility segment
 * starts. Up and down remain physical. The offset does not participate in parent layout.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @param from measured edge from which content enters
 * @param animationSpec timing policy for translation
 * @param distanceFraction non-negative finite fraction of the selected measured axis
 * @return a one-element slide enter transition
 * @throws IllegalArgumentException if [distanceFraction] is negative or non-finite
 */
fun slideIn(
    from: SlideDirection = SlideDirection.Start,
    animationSpec: FiniteAnimationSpec = tween(),
    distanceFraction: Float = 1f,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Slide(
            animationSpec = animationSpec,
            direction = from,
            distanceFraction = distanceFraction,
        ),
    ),
)

/**
 * Creates a logical horizontal slide enter transition.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @param from logical start or end edge
 * @param animationSpec timing policy for translation
 * @param distanceFraction non-negative finite fraction of measured width
 * @return a one-element horizontal slide enter transition
 * @throws IllegalArgumentException if [from] is vertical or the fraction is invalid
 */
fun slideInHorizontally(
    from: SlideDirection = SlideDirection.Start,
    animationSpec: FiniteAnimationSpec = tween(),
    distanceFraction: Float = 1f,
): EnterTransition {
    require(from == SlideDirection.Start || from == SlideDirection.End) {
        "slideInHorizontally requires Start or End."
    }
    return slideIn(from, animationSpec, distanceFraction)
}

/**
 * Creates a physical vertical slide enter transition.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @param from physical top or bottom edge
 * @param animationSpec timing policy for translation
 * @param distanceFraction non-negative finite fraction of measured height
 * @return a one-element vertical slide enter transition
 * @throws IllegalArgumentException if [from] is horizontal or the fraction is invalid
 */
fun slideInVertically(
    from: SlideDirection = SlideDirection.Up,
    animationSpec: FiniteAnimationSpec = tween(),
    distanceFraction: Float = 1f,
): EnterTransition {
    require(from == SlideDirection.Up || from == SlideDirection.Down) {
        "slideInVertically requires Up or Down."
    }
    return slideIn(from, animationSpec, distanceFraction)
}

/**
 * Creates an exit transition that slides toward [towards] by [distanceFraction] of measured size.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @param towards measured edge toward which content exits
 * @param animationSpec timing policy for translation
 * @param distanceFraction non-negative finite fraction of the selected measured axis
 * @return a one-element slide exit transition
 * @throws IllegalArgumentException if [distanceFraction] is negative or non-finite
 */
fun slideOut(
    towards: SlideDirection = SlideDirection.End,
    animationSpec: FiniteAnimationSpec = tween(),
    distanceFraction: Float = 1f,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Slide(
            animationSpec = animationSpec,
            direction = towards,
            distanceFraction = distanceFraction,
        ),
    ),
)

/**
 * Creates a logical horizontal slide exit transition.
 *
 * Start and end resolve from the layout direction captured when the owning segment starts.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @param towards logical start or end edge
 * @param animationSpec timing policy for translation
 * @param distanceFraction non-negative finite fraction of measured width
 * @return a one-element horizontal slide exit transition
 * @throws IllegalArgumentException if [towards] is vertical or the fraction is invalid
 */
fun slideOutHorizontally(
    towards: SlideDirection = SlideDirection.End,
    animationSpec: FiniteAnimationSpec = tween(),
    distanceFraction: Float = 1f,
): ExitTransition {
    require(towards == SlideDirection.Start || towards == SlideDirection.End) {
        "slideOutHorizontally requires Start or End."
    }
    return slideOut(towards, animationSpec, distanceFraction)
}

/**
 * Creates a physical vertical slide exit transition.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @param towards physical top or bottom edge
 * @param animationSpec timing policy for translation
 * @param distanceFraction non-negative finite fraction of measured height
 * @return a one-element vertical slide exit transition
 * @throws IllegalArgumentException if [towards] is horizontal or the fraction is invalid
 */
fun slideOutVertically(
    towards: SlideDirection = SlideDirection.Down,
    animationSpec: FiniteAnimationSpec = tween(),
    distanceFraction: Float = 1f,
): ExitTransition {
    require(towards == SlideDirection.Up || towards == SlideDirection.Down) {
        "slideOutVertically requires Up or Down."
    }
    return slideOut(towards, animationSpec, distanceFraction)
}

/**
 * Creates an enter transition that visually scales from [initialScale].
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @param animationSpec timing policy for both visual scale axes
 * @param initialScale finite scale at the hidden endpoint
 * @param transformOrigin fractional pivot used by visual scale
 * @return a one-element visual scale enter transition
 * @throws IllegalArgumentException if the scale or pivot is non-finite
 */
fun scaleIn(
    animationSpec: FiniteAnimationSpec = tween(),
    initialScale: Float = 0.92f,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Scale(
            animationSpec = animationSpec,
            initialScale = initialScale,
            transformOrigin = transformOrigin,
        ),
    ),
)

/**
 * Creates an exit transition that visually scales to [targetScale].
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @param animationSpec timing policy for both visual scale axes
 * @param targetScale finite scale at the hidden endpoint
 * @param transformOrigin fractional pivot used by visual scale
 * @return a one-element visual scale exit transition
 * @throws IllegalArgumentException if the scale or pivot is non-finite
 */
fun scaleOut(
    animationSpec: FiniteAnimationSpec = tween(),
    targetScale: Float = 0.92f,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Scale(
            animationSpec = animationSpec,
            targetScale = targetScale,
            transformOrigin = transformOrigin,
        ),
    ),
)

/**
 * Builder scope for content retained by one [AnimatedVisibility] host.
 *
 * [transition] is the same coordinator used by the parent host. [AnimatedEnterExit] registers its
 * channels on that coordinator, so parent and descendant motion share one frame loop and content is
 * removed only after their longest channel settles. Nested transforms apply descendant-local
 * translation, scale, alpha, and reveal first, followed by the parent host transform.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @property transition owning Boolean visibility transition
 */
@UiDslMarker
class AnimatedVisibilityScope internal constructor(
    val transition: Transition<Boolean>,
    private val targetVisible: Boolean,
    private val layoutDirection: UiLayoutDirection,
) : UiTreeBuilder() {
    /**
     * Emits a descendant whose enter and exit channels join the owning visibility transition.
     *
     * This is the scoped equivalent of Compose's descendant `animateEnterExit` modifier. A host is
     * emitted because ViewCompose keeps measured bounds, focus, accessibility, and renderer
     * rollback explicit at the native View boundary. Duplicate channels inside [enter] or [exit]
     * use the same last-applicable-element precedence as the parent.
     *
     * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
     *
     * @param modifier modifier applied to the descendant transition host
     * @param enter descendant channels used by a hidden-to-visible segment
     * @param exit descendant channels used by a visible-to-hidden segment
     * @param content box-scoped subtree retained through the shared exit segment
     */
    fun AnimatedEnterExit(
        modifier: Modifier = Modifier,
        enter: EnterTransition = EnterTransition.None,
        exit: ExitTransition = ExitTransition.None,
        content: BoxScope.() -> Unit,
    ) {
        val visual = transition.sampleVisibilityVisuals(
            enter = enter,
            exit = exit,
            layoutDirection = layoutDirection,
        )
        emitVisibilityHost(
            visual = visual,
            active = targetVisible,
            modifier = modifier,
        ) {
            Box(content = content)
        }
    }
}

/**
 * Mounts or removes [content] with one coordinated visibility transition when [visible] changes.
 *
 * The first composition is settled at [visible] and does not play an enter animation. A later
 * `false` target immediately removes the subtree from input, focus, and accessibility ownership,
 * keeps it mounted for drawing through the exit segment, and physically removes it after every
 * parent and descendant channel finishes. Retargeting resumes each channel from its current sample.
 * Size elements clip content to animated width and height; slide offsets are fractions of the full
 * measured axis and do not affect parent layout; visual scale uses its declared transform origin.
 * [modifier] applies to the animated visibility host.
 *
 * The default transition fades and expands or shrinks both axes. Within a composed transition, the
 * last element applicable to a channel wins. [AnimatedVisibilityScope.AnimatedEnterExit] registers
 * descendant channels on the same [Transition] and frame loop. Descendant-local transforms apply
 * before the parent host transform. Logical slide direction is frozen for each segment.
 *
 * @sample com.viewcompose.animation.samples.animatedVisibilitySample
 *
 * @receiver tree builder for the current composition
 * @param visible whether content should be mounted after the transition settles
 * @param modifier modifier applied to the visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content visibility-scoped subtree retained until every shared exit channel completes
 */
fun UiTreeBuilder.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    content: AnimatedVisibilityScope.() -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(visible)
    }
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes [content] according to an externally controlled [visibleState].
 *
 * Callers update [MutableTransitionState.targetState]; this host mirrors committed state and idle
 * status back into the same object. Bind one state object to one active host to avoid competing
 * writers. The first composition is settled at the state's current target, so setting a different
 * target before the state is first consumed does not play an initial enter animation in this
 * release. Compose the hidden target once before changing it when an initial enter is required.
 *
 * Content remains mounted for drawing through exit and interrupted segments but stops owning input,
 * focus, and accessibility as soon as an exit target is accepted. Descendant channels registered
 * through [AnimatedVisibilityScope] share the same terminal lifetime. The default transition
 * affects alpha and both measured axes.
 *
 * @sample com.viewcompose.animation.samples.mutableTransitionStateSample
 *
 * @receiver tree builder for the current composition
 * @param visibleState externally retained target and observation state
 * @param modifier modifier applied to the visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content visibility-scoped subtree retained until every shared exit channel completes
 */
fun UiTreeBuilder.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    content: AnimatedVisibilityScope.() -> Unit,
) {
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visibleState.targetState,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes row [content], defaulting size motion to the horizontal axis.
 *
 * The first composition is settled at [visible]. Later exits retain content until all alpha and
 * width channels finish. Additional slide, scale, and descendant channels use the same coordinator.
 * Retargeting resumes from current samples.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @receiver row scope that receives the animated visibility host as one child
 * @param visible whether content should remain after the transition settles
 * @param modifier modifier applied to the row child visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content visibility-scoped subtree inside the row child
 */
fun RowScope.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandHorizontally(),
    exit: ExitTransition = shrinkHorizontally() + fadeOut(),
    content: AnimatedVisibilityScope.() -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(visible)
    }
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes row [content] from an externally controlled [visibleState].
 *
 * This overload shares the state ownership and first-consumption behavior of the tree-builder state
 * overload, while its defaults expand and shrink only measured width. Bind one state object to one
 * active host.
 *
 * @sample com.viewcompose.animation.samples.mutableTransitionStateSample
 *
 * @receiver row scope that receives the animated visibility host as one child
 * @param visibleState externally retained target and observation state
 * @param modifier modifier applied to the row child visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content visibility-scoped subtree inside the row child
 */
fun RowScope.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandHorizontally(),
    exit: ExitTransition = shrinkHorizontally() + fadeOut(),
    content: AnimatedVisibilityScope.() -> Unit,
) {
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visibleState.targetState,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes column [content], defaulting size motion to the vertical axis.
 *
 * The first composition is settled at [visible]. Later exits retain content until all alpha and
 * height channels finish. Additional slide, scale, and descendant channels use the same coordinator.
 * Retargeting resumes from current samples.
 *
 * @sample com.viewcompose.animation.samples.richVisibilityTransitionsSample
 *
 * @receiver column scope that receives the animated visibility host as one child
 * @param visible whether content should remain after the transition settles
 * @param modifier modifier applied to the column child visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content visibility-scoped subtree inside the column child
 */
fun ColumnScope.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandVertically(),
    exit: ExitTransition = shrinkVertically() + fadeOut(),
    content: AnimatedVisibilityScope.() -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(visible)
    }
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes column [content] from an externally controlled [visibleState].
 *
 * This overload shares the state ownership and first-consumption behavior of the tree-builder state
 * overload, while its defaults expand and shrink only measured height. Bind one state object to one
 * active host.
 *
 * @sample com.viewcompose.animation.samples.mutableTransitionStateSample
 *
 * @receiver column scope that receives the animated visibility host as one child
 * @param visibleState externally retained target and observation state
 * @param modifier modifier applied to the column child visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content visibility-scoped subtree inside the column child
 */
fun ColumnScope.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandVertically(),
    exit: ExitTransition = shrinkVertically() + fadeOut(),
    content: AnimatedVisibilityScope.() -> Unit,
) {
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visibleState.targetState,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

private fun UiTreeBuilder.animatedVisibilityCore(
    visibleState: MutableTransitionState<Boolean>,
    targetVisible: Boolean,
    modifier: Modifier,
    enter: EnterTransition,
    exit: ExitTransition,
    content: AnimatedVisibilityScope.() -> Unit,
) {
    val transition = updateTransition(
        targetState = targetVisible,
        label = "animated_visibility",
    )
    val segmentVersion = transition.runtimeSegmentVersion()
    val segmentLayoutDirection = remember(segmentVersion) {
        Environment.layoutDirection
    }
    val visual = transition.sampleVisibilityVisuals(
        enter = enter,
        exit = exit,
        layoutDirection = segmentLayoutDirection,
    )
    // Mirror the internal segment after sampling so external observers see committed and idle state.
    visibleState.syncFromTransition(
        currentState = transition.runtimeCurrentState(),
        targetState = transition.runtimeTargetState(),
        isIdle = !transition.runtimeIsRunning() &&
            transition.runtimeCurrentState() == transition.runtimeTargetState(),
    )
    val shouldRenderContent = transition.runtimeCurrentState() ||
        transition.runtimeTargetState() ||
        transition.runtimeIsRunning()
    // Keep the empty host as a zero-size identity anchor. Removing it would shift unkeyed
    // siblings during reconciliation and recreate native Views, truncating pressed/focus state.
    val renderedVisual = if (shouldRenderContent) {
        visual
    } else {
        visual.copy(
            alpha = 0f,
            widthScale = 0f,
            heightScale = 0f,
            translationXFraction = 0f,
            translationYFraction = 0f,
        )
    }
    emitScoped(
        type = NodeType.AnimatedVisibilityHost,
        inputs = listOf(renderedVisual, targetVisible, shouldRenderContent),
        modifier = modifier,
        scopeFactory = {
            AnimatedVisibilityScope(
                transition = transition,
                targetVisible = targetVisible,
                layoutDirection = segmentLayoutDirection,
            )
        },
        spec = {
            renderedVisual.toNodeProps(active = targetVisible && shouldRenderContent)
        },
        content = {
            if (shouldRenderContent) {
                content()
            }
        },
    )
}

private data class VisibilityVisuals(
    val alpha: Float = 1f,
    val widthScale: Float = 1f,
    val heightScale: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationXFraction: Float = 0f,
    val translationYFraction: Float = 0f,
    val transformOrigin: TransformOrigin = TransformOrigin.Center,
    val contentAlignment: BoxAlignment = BoxAlignment.TopStart,
    val clipToBounds: Boolean = false,
)

private fun Transition<Boolean>.sampleVisibilityVisuals(
    enter: EnterTransition,
    exit: ExitTransition,
    layoutDirection: UiLayoutDirection,
): VisibilityVisuals {
    val enterFade = enter.elements.filterIsInstance<EnterTransitionElement.Fade>().lastOrNull()
    val exitFade = exit.elements.filterIsInstance<ExitTransitionElement.Fade>().lastOrNull()
    val enterWidthExpand = enter.findExpandForWidthAxis()
    val enterHeightExpand = enter.findExpandForHeightAxis()
    val exitWidthShrink = exit.findShrinkForWidthAxis()
    val exitHeightShrink = exit.findShrinkForHeightAxis()
    val enterSlide = enter.elements.filterIsInstance<EnterTransitionElement.Slide>().lastOrNull()
    val exitSlide = exit.elements.filterIsInstance<ExitTransitionElement.Slide>().lastOrNull()
    val enterScale = enter.elements.filterIsInstance<EnterTransitionElement.Scale>().lastOrNull()
    val exitScale = exit.elements.filterIsInstance<ExitTransitionElement.Scale>().lastOrNull()
    val enterOffset = enterSlide?.resolveOffset(layoutDirection) ?: Pair(0f, 0f)
    val exitOffset = exitSlide?.resolveOffset(layoutDirection) ?: Pair(0f, 0f)

    fun channel(
        enterStart: Float,
        enterSpec: FiniteAnimationSpec?,
        visibleValue: Float,
        exitEnd: Float,
        exitSpec: FiniteAnimationSpec?,
    ): Float {
        return sampleFloat(
            transitionSpec = {
                when {
                    !initialState && targetState -> enterSpec ?: snap()
                    initialState && !targetState -> exitSpec ?: snap()
                    else -> snap()
                }
            },
            segmentEndpoints = { segment, current ->
                when {
                    !segment.initialState && segment.targetState -> {
                        val start = if (current.isApproximately(exitEnd)) enterStart else current
                        start to visibleValue
                    }

                    segment.initialState && !segment.targetState -> current to exitEnd
                    else -> current to if (segment.targetState) visibleValue else exitEnd
                }
            },
            valueForSettledState = { settledVisible ->
                if (settledVisible) visibleValue else exitEnd
            },
        )
    }

    val entering = runtimeTargetState()
    val sizeElementAlignment = if (entering) {
        enter.elements.asReversed().filterIsInstance<EnterTransitionElement.Expand>().firstOrNull()?.alignment
    } else {
        exit.elements.asReversed().filterIsInstance<ExitTransitionElement.Shrink>().firstOrNull()?.alignment
    }
    val scaleOrigin = if (entering) {
        enterScale?.transformOrigin
    } else {
        exitScale?.transformOrigin
    }
    return VisibilityVisuals(
        alpha = channel(
            enterStart = enterFade?.initialAlpha ?: 1f,
            enterSpec = enterFade?.animationSpec,
            visibleValue = 1f,
            exitEnd = exitFade?.targetAlpha ?: 1f,
            exitSpec = exitFade?.animationSpec,
        ).coerceIn(0f, 1f),
        widthScale = channel(
            enterStart = enterWidthExpand?.initialScale ?: 1f,
            enterSpec = enterWidthExpand?.animationSpec,
            visibleValue = 1f,
            exitEnd = exitWidthShrink?.targetScale ?: 1f,
            exitSpec = exitWidthShrink?.animationSpec,
        ).coerceAtLeast(0f),
        heightScale = channel(
            enterStart = enterHeightExpand?.initialScale ?: 1f,
            enterSpec = enterHeightExpand?.animationSpec,
            visibleValue = 1f,
            exitEnd = exitHeightShrink?.targetScale ?: 1f,
            exitSpec = exitHeightShrink?.animationSpec,
        ).coerceAtLeast(0f),
        scaleX = channel(
            enterStart = enterScale?.initialScale ?: 1f,
            enterSpec = enterScale?.animationSpec,
            visibleValue = 1f,
            exitEnd = exitScale?.targetScale ?: 1f,
            exitSpec = exitScale?.animationSpec,
        ),
        scaleY = channel(
            enterStart = enterScale?.initialScale ?: 1f,
            enterSpec = enterScale?.animationSpec,
            visibleValue = 1f,
            exitEnd = exitScale?.targetScale ?: 1f,
            exitSpec = exitScale?.animationSpec,
        ),
        translationXFraction = channel(
            enterStart = enterOffset.first,
            enterSpec = enterSlide?.animationSpec,
            visibleValue = 0f,
            exitEnd = exitOffset.first,
            exitSpec = exitSlide?.animationSpec,
        ),
        translationYFraction = channel(
            enterStart = enterOffset.second,
            enterSpec = enterSlide?.animationSpec,
            visibleValue = 0f,
            exitEnd = exitOffset.second,
            exitSpec = exitSlide?.animationSpec,
        ),
        transformOrigin = scaleOrigin ?: TransformOrigin.Center,
        contentAlignment = sizeElementAlignment ?: BoxAlignment.TopStart,
        clipToBounds = enterWidthExpand != null ||
            enterHeightExpand != null ||
            exitWidthShrink != null ||
            exitHeightShrink != null,
    )
}

private fun UiTreeBuilder.emitVisibilityHost(
    visual: VisibilityVisuals,
    active: Boolean,
    modifier: Modifier,
    content: UiTreeBuilder.() -> Unit,
) {
    emit(
        type = NodeType.AnimatedVisibilityHost,
        spec = visual.toNodeProps(active),
        modifier = modifier,
        content = content,
    )
}

private fun VisibilityVisuals.toNodeProps(active: Boolean): AnimatedVisibilityHostNodeProps {
    return AnimatedVisibilityHostNodeProps(
        alpha = alpha,
        widthScale = widthScale,
        heightScale = heightScale,
        scaleX = scaleX,
        scaleY = scaleY,
        translationXFraction = translationXFraction,
        translationYFraction = translationYFraction,
        transformOrigin = transformOrigin,
        contentAlignment = contentAlignment,
        clipToBounds = clipToBounds,
        active = active,
    )
}

internal fun EnterTransition.findExpandForWidthAxis(): EnterTransitionElement.Expand? {
    return elements
        .asReversed()
        .filterIsInstance<EnterTransitionElement.Expand>()
        .firstOrNull { it.axis == SizeTransformAxis.Both || it.axis == SizeTransformAxis.Horizontal }
}

internal fun EnterTransition.findExpandForHeightAxis(): EnterTransitionElement.Expand? {
    return elements
        .asReversed()
        .filterIsInstance<EnterTransitionElement.Expand>()
        .firstOrNull { it.axis == SizeTransformAxis.Both || it.axis == SizeTransformAxis.Vertical }
}

internal fun ExitTransition.findShrinkForWidthAxis(): ExitTransitionElement.Shrink? {
    return elements
        .asReversed()
        .filterIsInstance<ExitTransitionElement.Shrink>()
        .firstOrNull { it.axis == SizeTransformAxis.Both || it.axis == SizeTransformAxis.Horizontal }
}

internal fun ExitTransition.findShrinkForHeightAxis(): ExitTransitionElement.Shrink? {
    return elements
        .asReversed()
        .filterIsInstance<ExitTransitionElement.Shrink>()
        .firstOrNull { it.axis == SizeTransformAxis.Both || it.axis == SizeTransformAxis.Vertical }
}

private fun EnterTransitionElement.Slide.resolveOffset(
    layoutDirection: UiLayoutDirection,
): Pair<Float, Float> {
    return direction.resolveOffset(layoutDirection, distanceFraction)
}

private fun ExitTransitionElement.Slide.resolveOffset(
    layoutDirection: UiLayoutDirection,
): Pair<Float, Float> {
    return direction.resolveOffset(layoutDirection, distanceFraction)
}

private fun SlideDirection.resolveOffset(
    layoutDirection: UiLayoutDirection,
    distanceFraction: Float,
): Pair<Float, Float> {
    return when (this) {
        SlideDirection.Start -> {
            val sign = if (layoutDirection == UiLayoutDirection.Ltr) -1f else 1f
            Pair(sign * distanceFraction, 0f)
        }

        SlideDirection.End -> {
            val sign = if (layoutDirection == UiLayoutDirection.Ltr) 1f else -1f
            Pair(sign * distanceFraction, 0f)
        }

        SlideDirection.Up -> Pair(0f, -distanceFraction)
        SlideDirection.Down -> Pair(0f, distanceFraction)
    }
}

private fun requireVisibilityScale(
    scale: Float,
    transformOrigin: TransformOrigin,
    operation: String,
) {
    require(
        scale.isFinite() &&
            transformOrigin.pivotFractionX.isFinite() &&
            transformOrigin.pivotFractionY.isFinite(),
    ) {
        "$operation and transform origin must be finite."
    }
}

private fun Float.isApproximately(other: Float): Boolean {
    return abs(this - other) <= 0.001f
}
