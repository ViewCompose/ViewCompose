package com.viewcompose

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal debug-only host for device capability tests.
 *
 * Showing over the keyguard keeps connected-device automation deterministic without changing the
 * device's lock settings.
 */
class P1CoreCapabilitiesTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
    }
}
