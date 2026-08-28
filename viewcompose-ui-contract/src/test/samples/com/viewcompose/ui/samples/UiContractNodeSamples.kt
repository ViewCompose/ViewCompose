package com.viewcompose.ui.samples

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import com.viewcompose.ui.modifier.imeInsetsPaddingRelative
import com.viewcompose.ui.modifier.marginRelative
import com.viewcompose.ui.modifier.offsetRelative
import com.viewcompose.ui.modifier.paddingRelative
import com.viewcompose.ui.modifier.SemanticsCollectionInfo
import com.viewcompose.ui.modifier.SemanticsCollectionItemInfo
import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.systemBarsInsetsPaddingRelative
import com.viewcompose.ui.modifier.sharedBounds
import com.viewcompose.ui.modifier.sharedElement
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.shared.SharedContentKey
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionStrategy
import com.viewcompose.ui.node.asLazyItemTable
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageCachePolicy
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageRequestExtension
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.UiImageTarget
import com.viewcompose.ui.node.UiImageTransition
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedContentHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedContentItemNodeProps
import com.viewcompose.ui.node.spec.AnimatedBoundsHostNodeProps
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintMatchMode
import com.viewcompose.ui.node.spec.ConstraintRatio
import com.viewcompose.ui.node.spec.ConstraintRatioSide
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.tooling.UiSourceCallSite
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import java.io.File

/** Builds and invokes the renderer-neutral contract for one typed Android View adapter binding. */
fun androidViewNodePropsSample(platformContext: Any, platformView: Any) {
    var boundEnvironment: UiEnvironmentValues? = null
    val spec = AndroidViewNodeProps(
        factory = { _, _ -> platformView },
        update = { _, environment -> boundEnvironment = environment },
        constructionIdentity = "adapter-family/default-style",
        adapterName = "sample-adapter",
    )

    val environment = UiEnvironmentValues.Default
    val created = spec.factory(platformContext, environment)
    spec.update?.invoke(created, environment)
    check(boundEnvironment === environment)
}

/** Declares typed shared endpoints for two destination-specific modifier chains. */
fun sharedContentModifierSample() {
    val heroKey = SharedContentKey("article-42-hero")
    val listEndpoint = Modifier.sharedElement(heroKey)
    val detailEndpoint = Modifier.sharedElement(heroKey)
    val cardBounds = Modifier.sharedBounds(SharedContentKey("article-42-card"))

    check(listEndpoint == detailEndpoint)
    check(cardBounds != listEndpoint)
}

/** Builds mutually exclusive ConstraintLayout dimensions and a typed ratio. */
fun constraintDimensionsSample() {
    val width = ConstraintDimension.MatchConstraints(
        mode = ConstraintMatchMode.Percent(0.6f),
        min = 120.dp,
        max = 360.dp,
    )
    val height = ConstraintDimension.MatchConstraints()
    val ratio = ConstraintRatio(
        width = 16f,
        height = 9f,
        constrainedSide = ConstraintRatioSide.Width,
    )

    check(width.min == 120.dp)
    check(height.mode == ConstraintMatchMode.Spread)
    check(ratio.width == 16f)
}

fun relativeLayoutModifierSample() {
    val modifier = Modifier
        .paddingRelative(start = 16.dp, end = 24.dp)
        .marginRelative(start = 8.dp, end = 12.dp)
        .offsetRelative(horizontal = 4.dp)
        .systemBarsInsetsPaddingRelative(start = true, top = false, end = false, bottom = false)
        .imeInsetsPaddingRelative(bottom = true)

    check(modifier.elements.size == 5)
}

fun lazyListItemSessionUpdateSample() {
    val session = object : LazyListItemSession {
        var installedKey: Any? = null
        var installedLabel = ""
        var prepared = false
        var activated = false
        var renderCount = 0

        override fun prepare() {
            prepared = true
        }

        override fun activate(): Boolean {
            check(prepared)
            activated = true
            return true
        }

        override fun render(): Boolean {
            check(activated)
            renderCount += 1
            return true
        }

        override fun dispose() = Unit
    }
    val strategy = object : LazyListItemSessionStrategy {
        override fun canReuseAcrossKeys(retained: LazyListItemSession): Boolean {
            // A real opt-in must also reset every key-owned state/effect/saveable owner from update.
            return retained === session
        }

        override fun create(
            container: RenderContainerHandle,
            item: LazyListItem,
        ): LazyListItemSession = session

        override fun update(
            retained: LazyListItemSession,
            item: LazyListItem,
        ) {
            check(retained === session)
            if (session.installedKey != item.key) {
                // A production opt-in also ends key-owned effects and saveable-state ownership.
                session.installedLabel = ""
                session.installedKey = item.key
            }
            session.installedLabel = item.sessionPayload as String
        }
    }
    val initial = LazyListItem(
        key = "account",
        contentRevision = "row-v1",
        sessionStrategy = strategy,
        sessionPayload = "Initial",
    )
    val equalSnapshot = LazyListItem(
        key = "account",
        contentRevision = "row-v1",
        sessionStrategy = strategy,
        sessionPayload = "Ignored until the revision changes",
    )
    val changedSnapshot = LazyListItem(
        key = "account",
        contentRevision = "row-v2",
        sessionStrategy = strategy,
        sessionPayload = "Updated",
    )

    check(initial == equalSnapshot)
    check(initial != changedSnapshot)
    initial.updateSession(session)
    session.prepare()
    check(session.renderCount == 0)
    session.activate()
    // A collection controller completely skips equalSnapshot. A changed revision installs the
    // latest payload through the shared strategy and renders only this retained logical session.
    changedSnapshot.updateSession(session)
    session.render()
    check(session.installedLabel == "Updated")
    check(session.renderCount == 1)

    val replacementKey = LazyListItem(
        key = "settings",
        contentRevision = "row-v1",
        sessionStrategy = strategy,
        sessionPayload = "Settings",
    )
    check(strategy.canReuseAcrossKeys(session))
    replacementKey.updateSession(session)
    session.render()
    check(session.installedKey == "settings")
    check(session.installedLabel == "Settings")
}

/** Wraps an immutable finite submission with validated key and sticky-header lookup metadata. */
fun lazyItemTableSample() {
    val strategy = object : LazyListItemSessionStrategy {
        override fun create(
            container: RenderContainerHandle,
            item: LazyListItem,
        ): LazyListItemSession = object : LazyListItemSession {
            override fun render(): Boolean = true
            override fun dispose() = Unit
        }

        override fun update(
            session: LazyListItemSession,
            item: LazyListItem,
        ) = Unit
    }
    val table = listOf(
        LazyListItem(
            key = "status",
            contentRevision = 3,
            sessionStrategy = strategy,
        ),
    ).asLazyItemTable()

    check(table.size == 1)
    check(table.indexOfKey("status") == 0)
    check(table[0].contentRevision == 3)
}

fun collectionPolicySample() {
    val prefetch = LazyLayoutPrefetchPolicy(
        nestedInitialPrefetchItemCount = 4,
        itemViewCacheSize = 4,
    )
    val reuse = CollectionReusePolicy(
        sharePool = true,
        mountedTreeCacheSize = 2,
    )

    check(prefetch.nestedInitialPrefetchItemCount == 4)
    check(reuse.mountedTreeCacheSize == 2)
}

fun vNodeModelSample() {
    // DOCS_REGION_START(ui-contract-module-node)
val gap = VNode(
    type = NodeType.Spacer,
    key = "content-gap",
    spec = EmptyNodeSpec,
    modifier = Modifier
        .size(width = 24.dp, height = 24.dp)
        .testTag("content-gap"),
)
    // DOCS_REGION_END(ui-contract-module-node)

    check(gap.type == NodeType.Spacer)
    check(gap.key == "content-gap")
    check(gap.children.isEmpty())
}

/** Correlates a declarative node with one finite composition and renderer timing request. */
fun uiNodeTimingIdentitySample() {
    val composedNode = VNode(
        type = NodeType.Text,
        spec = EmptyNodeSpec,
    )
    UiNodeTooling.attachTimingIdentity(composedNode, identity = 7L)
    check(UiNodeTooling.ensureTimingIdentity(composedNode) == 7L)
    check(UiNodeTooling.timingIdentityOf(composedNode) == 7L)

    val rendererOnlyNode = VNode(
        type = NodeType.Spacer,
        spec = EmptyNodeSpec,
    )
    check(UiNodeTooling.ensureTimingIdentity(rendererOnlyNode) < 0L)
}

/** Builds one interactive visibility frame with measured reveal and visual transforms. */
fun animatedVisibilityHostNodeContractSample() {
    val host = VNode(
        type = NodeType.AnimatedVisibilityHost,
        spec = AnimatedVisibilityHostNodeProps(
            alpha = 0.7f,
            widthScale = 0.8f,
            heightScale = 0.6f,
            scaleX = 0.95f,
            scaleY = 0.95f,
            translationXFraction = -0.25f,
            translationYFraction = 0f,
            transformOrigin = TransformOrigin(0f, 1f),
            contentAlignment = BoxAlignment.BottomStart,
            clipToBounds = true,
            active = true,
        ),
    )

    check(host.type == NodeType.AnimatedVisibilityHost)
}

/** Builds the renderer-neutral transport for one real parent-local bounds animation. */
fun animatedBoundsHostNodeContractSample() {
    val host = VNode(
        type = NodeType.AnimatedBoundsHost,
        spec = AnimatedBoundsHostNodeProps(
            animationSpec = ContentSizeTweenSpecModel(
                durationMillis = 240,
                delayMillis = 0,
                easing = ContentSizeEasingModel.FastOutSlowIn,
            ),
        ),
    )

    check(host.type == NodeType.AnimatedBoundsHost)
}

/** Builds the bounded renderer pair used by a keyed content replacement frame. */
fun animatedContentNodeContractSample() {
    val outgoing = VNode(
        type = NodeType.AnimatedContentItemHost,
        key = "details",
        spec = AnimatedContentItemNodeProps(
            alpha = 0.4f,
            scaleX = 1f,
            scaleY = 1f,
            translationXFraction = -0.25f,
            translationYFraction = 0f,
            revealWidthFraction = 1f,
            revealHeightFraction = 1f,
            transformOrigin = TransformOrigin.Center,
            active = false,
        ),
    )
    val incoming = VNode(
        type = NodeType.AnimatedContentItemHost,
        key = "confirmation",
        spec = (outgoing.spec as AnimatedContentItemNodeProps).copy(
            alpha = 0.6f,
            translationXFraction = 0.25f,
            active = true,
        ),
    )
    val host = VNode(
        type = NodeType.AnimatedContentHost,
        spec = AnimatedContentHostNodeProps(
            segmentId = 7L,
            sizeProgress = 0.6f,
            sizeTransformEnabled = true,
            clipToBounds = true,
            contentAlignment = BoxAlignment.Center,
        ),
        children = listOf(outgoing, incoming),
    )

    check(host.children.size == 2)
    check((host.children.last().spec as AnimatedContentItemNodeProps).active)
}

/** Captures one source chain for session-level tooling without annotating every node. */
fun firstSourceCaptureSample() {
    var capturedSource: List<UiSourceCallSite> = emptyList()

    UiNodeTooling.withFirstSourceCapture(
        onSourceCaptured = { source -> capturedSource = source },
    ) {
        UiNodeTooling.attach(
            VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
            ),
        )
    }

    check(capturedSource.isNotEmpty())
}

/** Captures bounded source candidates when reusable chrome emits nodes before page content. */
fun sourceCandidateCaptureSample() {
    var sourceCandidates: List<List<UiSourceCallSite>> = emptyList()

    UiNodeTooling.withSourceCandidateCapture(
        onSourceCandidatesCaptured = { candidates -> sourceCandidates = candidates },
    ) {
        UiNodeTooling.attach(
            VNode(
                type = NodeType.Column,
                spec = EmptyNodeSpec,
            ),
        )
        UiNodeTooling.attach(
            VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
            ),
        )
    }

    check(sourceCandidates.isNotEmpty())
    check(sourceCandidates.all(List<UiSourceCallSite>::isNotEmpty))
}

/** Demonstrates parent collection metadata and one selected logical child position. */
fun collectionSemanticsSample() {
    val parent = Modifier.semantics {
        collectionInfo = SemanticsCollectionInfo(
            rowCount = 1,
            columnCount = 3,
            selectionMode = SemanticsCollectionSelectionMode.Single,
        )
    }
    val selectedItem = Modifier.semantics(mergeDescendants = true) {
        collectionItemInfo = SemanticsCollectionItemInfo(
            rowIndex = 0,
            columnIndex = 1,
        )
        selected = true
    }

    val parentSemantics = (parent.elements.single() as SemanticsModifierElement).configuration
    val itemSemantics = (selectedItem.elements.single() as SemanticsModifierElement).configuration
    check(parentSemantics.collectionInfo?.columnCount == 3)
    check(itemSemantics.collectionItemInfo?.columnIndex == 1)
    check(itemSemantics.selected == true)
}

/**
 * Demonstrates a custom loader retaining the returned handle so the renderer can dispose it.
 */
fun uiImageLoaderSample() {
    val target = object : UiImageTarget {}
    var disposed = false
    val loader = UiImageLoader { receivedTarget, request ->
        check(receivedTarget === target)
        check(request.source == ImageSource.Resource(1))
        UiImageLoadHandle { disposed = true }
    }

    val handle = loader.load(
        target = target,
        request = UiImageRequest(source = ImageSource.Resource(1)),
    )
    handle.dispose()

    check(disposed)
}

/**
 * Demonstrates the source family, stable-key model identity, and common request options.
 */
fun uiImageRequestSample() {
    val localFile = File("/tmp/avatar.png")
    val sources = listOf<ImageSource>(
        ImageSource.Resource(1),
        ImageSource.Url("https://example.com/avatar.png"),
        ImageSource.Uri("content://com.example/avatar/1"),
        ImageSource.File(localFile),
        ImageSource.Model(
            value = ByteArray(0),
            stableKey = "avatar-v1",
        ),
    )
    val request = UiImageRequest(
        source = sources.last(),
        options = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Fixed(width = 320.dp, height = 180.dp),
            memoryCachePolicy = UiImageCachePolicy.Disabled,
            transition = UiImageTransition.Crossfade(durationMillis = 180),
            extensions = listOf(SampleImageExtension(stableKey = "decoder-v1")),
        ),
        contentScale = ImageContentScale.Crop,
        density = UiDensity(density = 2f, fontScale = 1f),
    )

    check(request.source == ImageSource.Model(ByteArray(3), "avatar-v1"))
    check(request.options.decodeSize == UiImageDecodeSize.Fixed(width = 320.dp, height = 180.dp))
    check(request.density.roundToPx(320.dp) == 640)
}

private data class SampleImageExtension(
    override val stableKey: Any,
) : UiImageRequestExtension
