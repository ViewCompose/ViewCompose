package com.viewcompose

import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.rememberSaveable

/**
 * rememberSaveable 基础状态恢复测试的 debug-only 宿主。
 * Debug-only host for basic rememberSaveable state restoration tests.
 */
class SaveableStateTestActivity : AppCompatActivity() {
    private var countState: MutableState<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val processDeathCertification = intent.getBooleanExtra(
            EXTRA_PROCESS_DEATH_CERTIFICATION,
            false,
        )
        setMaterial3UiContent {
            val count = rememberSaveable {
                mutableStateOf(0)
            }
            countState = count
            Column {
                Text(
                    text = count.value.toString(),
                    modifier = Modifier.testTag(COUNT_TAG),
                )
                Button(
                    text = "Increment",
                    onClick = {
                        count.value += 1
                    },
                    modifier = Modifier.testTag(INCREMENT_TAG),
                )
                if (processDeathCertification) {
                    Text(
                        text = "$PROCESS_DEATH_STATUS_PREFIX" +
                            "count=${count.value};pid=${Process.myPid()}",
                        modifier = Modifier.testTag(PROCESS_DEATH_STATUS_TAG),
                    )
                }
            }
        }
        if (savedInstanceState == null && processDeathCertification) {
            window.decorView.post {
                checkNotNull(countState) {
                    "The Activity-root saveable state was not composed before certification."
                }.value = PROCESS_DEATH_EXPECTED_COUNT
            }
        }
    }

    companion object {
        const val COUNT_TAG = "saveable-state-count"
        const val INCREMENT_TAG = "saveable-state-increment"
        const val EXTRA_PROCESS_DEATH_CERTIFICATION =
            "com.viewcompose.extra.ACTIVITY_ROOT_PROCESS_DEATH_CERTIFICATION"
        const val PROCESS_DEATH_STATUS_PREFIX = "ACTIVITY_ROOT_PROCESS_DEATH|"
        const val PROCESS_DEATH_STATUS_TAG = "activity-root-process-death-status"
        const val PROCESS_DEATH_EXPECTED_COUNT = 41
    }
}
