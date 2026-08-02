package com.viewcompose.ui.samples

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.RemoteImageLoader
import com.viewcompose.ui.node.RemoteImageRequest
import com.viewcompose.ui.node.RemoteImageTarget
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec

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

fun remoteImageLoaderSample() {
    val target = object : RemoteImageTarget {}
    var receivedTarget: RemoteImageTarget? = null
    var receivedRequest: RemoteImageRequest? = null
    val loader = RemoteImageLoader { nextTarget, request ->
        receivedTarget = nextTarget
        receivedRequest = request
    }

    loader.load(
        target = target,
        request = RemoteImageRequest(
            url = "https://example.com/avatar.png",
            placeholderResId = 1,
            errorResId = 2,
        ),
    )

    check(receivedTarget === target)
    check(receivedRequest?.url == "https://example.com/avatar.png")
}
