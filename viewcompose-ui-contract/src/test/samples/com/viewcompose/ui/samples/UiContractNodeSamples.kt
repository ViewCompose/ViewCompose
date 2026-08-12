package com.viewcompose.ui.samples

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.SemanticsCollectionInfo
import com.viewcompose.ui.modifier.SemanticsCollectionItemInfo
import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.NodeType
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
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.tooling.UiSourceCallSite
import com.viewcompose.ui.tooling.UiSourceSessionContainerHandle
import com.viewcompose.ui.tooling.UiSourceSessionRole
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import java.io.File

fun lazyListItemSessionUpdateSample() {
    val session = object : LazyListItemSession {
        var installedLabel = ""
        var renderCount = 0

        override fun render() {
            renderCount += 1
        }

        override fun dispose() = Unit
    }
    val initial = LazyListItem(
        key = "account",
        contentToken = "row-v1",
        sessionFactory = { session },
        sessionUpdater = { retained ->
            check(retained === session)
            session.installedLabel = "Initial"
        },
    )
    val refreshed = LazyListItem(
        key = "account",
        contentToken = "row-v1",
        sessionFactory = { session },
        sessionUpdater = { session.installedLabel = "Updated" },
    )

    check(initial == refreshed)
    refreshed.sessionUpdater?.invoke(session)
    session.render()
    check(session.installedLabel == "Updated")
    check(session.renderCount == 1)
}

fun vNodeModelSample() {
    val spacer = VNode(
        type = NodeType.Spacer,
        key = "content-gap",
        spec = EmptyNodeSpec,
        modifier = Modifier.testTag("content-gap"),
    )

    check(spacer.type == NodeType.Spacer)
    check(spacer.key == "content-gap")
    check(spacer.children.isEmpty())
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

fun sourceSessionContainerHandleSample() {
    val pageContainer = object :
        PlatformRenderContainerHandle,
        UiSourceSessionContainerHandle {
        override val container: Any = Any()
        override val sourceSessionRole: UiSourceSessionRole = UiSourceSessionRole.Page
    }

    check(pageContainer.sourceSessionRole == UiSourceSessionRole.Page)
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
