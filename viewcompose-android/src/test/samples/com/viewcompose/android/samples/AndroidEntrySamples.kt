package com.viewcompose.android.samples

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.viewcompose.android.setUiContent
import com.viewcompose.ui.foundation.Text

fun activityHostSample(activity: ComponentActivity) {
    activity.setUiContent {
        Text("Hello from ViewCompose")
    }
}

fun fragmentHostSample(fragment: Fragment): ViewGroup {
    return fragment.setUiContent {
        Text("Fragment content")
    }
}
