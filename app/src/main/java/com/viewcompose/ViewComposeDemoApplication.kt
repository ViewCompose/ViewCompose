package com.viewcompose

import android.app.Application
import com.viewcompose.shadow.android.ShadowDecorationLayer

/** Demo bootstrap for optional renderer capabilities. */
class ViewComposeDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShadowDecorationLayer.install()
    }
}
