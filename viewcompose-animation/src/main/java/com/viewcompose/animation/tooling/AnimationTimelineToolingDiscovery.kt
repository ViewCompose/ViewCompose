package com.viewcompose.animation.tooling

import java.util.ServiceLoader

internal object AnimationTimelineToolingDiscovery {
    val tooling: AnimationTimelineTooling? by lazy {
        runCatching {
            selectSingleAnimationTimelineTooling(
                ServiceLoader.load(
                    AnimationTimelineTooling::class.java,
                    AnimationTimelineTooling::class.java.classLoader,
                ).toList(),
            )
        }.getOrNull()
    }
}

internal fun selectSingleAnimationTimelineTooling(
    providers: List<AnimationTimelineTooling>,
): AnimationTimelineTooling? = providers.singleOrNull()
