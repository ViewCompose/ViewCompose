package com.viewcompose

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Text
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.rememberLazyListState

/**
 * rememberLazyListState 恢复测试的 debug-only 宿主。
 * Debug-only host for rememberLazyListState restoration tests.
 */
class SaveableLazyListStateTestActivity : AppCompatActivity() {
    lateinit var listState: LazyListState
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUiContent {
            listState = rememberLazyListState()
            LazyColumn(
                items = (0 until ITEM_COUNT).toList(),
                key = { item -> item },
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) { item ->
                Text(
                    text = item.toString(),
                    modifier = Modifier
                        .height(ITEM_HEIGHT_DP.dp)
                        .testTag(itemTag(item)),
                )
            }
        }
    }

    companion object {
        /**
         * 测试重建前滚动到的目标 item。
         * Target item scrolled to before recreation in tests.
         */
        const val RESTORE_TARGET = 24
        private const val ITEM_COUNT = 50
        private const val ITEM_HEIGHT_DP = 64

        /**
         * 为列表项生成稳定 testTag。
         * Builds a stable testTag for a list item.
         */
        fun itemTag(item: Int): String = "saveable-lazy-list-item-$item"
    }
}
