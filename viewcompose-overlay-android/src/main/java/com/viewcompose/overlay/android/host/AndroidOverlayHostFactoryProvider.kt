package com.viewcompose.overlay.android.host

import android.view.View
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostFactoryProvider

/**
 * 通过 ServiceLoader 暴露给 widget-core 的 Android overlay host provider。
 * Android overlay host provider exposed to widget-core through ServiceLoader.
 */
class AndroidOverlayHostFactoryProvider : OverlayHostFactoryProvider {
    /**
     * 为当前渲染根 View 创建平台 overlay host。
     * Creates a platform overlay host for the current render root View.
     */
    override fun create(rootView: View): OverlayHost {
        return AndroidOverlayHost(rootView)
    }
}
