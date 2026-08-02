package com.viewcompose.publishing.smoke.feature

import com.viewcompose.animation.core.TweenSpec
import com.viewcompose.gesture.core.LockedAxis
import com.viewcompose.graphics.core.PathModel
import com.viewcompose.navigation.core.NavGraph

/**
 * These core types must remain visible when a consumer declares only their feature artifacts.
 */
val transitivelyAvailableCoreTypes = listOf(
    NavGraph::class,
    TweenSpec::class,
    LockedAxis::class,
    PathModel::class,
)
