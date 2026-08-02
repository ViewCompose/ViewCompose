package com.viewcompose.publishing.smoke.core

import com.viewcompose.animation.core.TweenSpec
import com.viewcompose.gesture.core.LockedAxis
import com.viewcompose.graphics.core.PathModel
import com.viewcompose.navigation.core.NavGraph

/**
 * Platform-neutral core artifacts must also remain independently consumable.
 */
val independentlyAvailableCoreTypes = listOf(
    NavGraph::class,
    TweenSpec::class,
    LockedAxis::class,
    PathModel::class,
)
