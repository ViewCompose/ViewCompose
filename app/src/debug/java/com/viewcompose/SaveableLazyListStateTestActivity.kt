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
import com.viewcompose.widget.core.dp
import com.viewcompose.widget.core.rememberLazyListState

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
        const val RESTORE_TARGET = 24
        private const val ITEM_COUNT = 50
        private const val ITEM_HEIGHT_DP = 64

        fun itemTag(item: Int): String = "saveable-lazy-list-item-$item"
    }
}
