package com.viewcompose.host.android

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * 校验 Android host 生命周期仍可接收新的 UI 内容。
 * Validates that an Android host lifecycle can still accept new UI content.
 */
internal fun requireActiveHost(
    owner: LifecycleOwner,
    hostName: String,
) {
    check(owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
        "$hostName.setUiContent cannot be called after the host lifecycle is destroyed."
    }
}
