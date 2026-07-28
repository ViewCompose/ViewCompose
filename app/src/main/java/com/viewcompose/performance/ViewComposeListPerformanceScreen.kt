package com.viewcompose.performance

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.width
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.dp
import com.viewcompose.widget.core.remember

/**
 * ViewCompose 版本的列表性能场景。
 * ViewCompose implementation of the list performance scenario.
 */
internal fun UiTreeBuilder.ViewComposeListPerformanceScreen() {
    val revisionState = remember { mutableStateOf(0) }
    val revision = revisionState.value
    val rows = performanceListRows(revision)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(PERFORMANCE_BACKGROUND_COLOR),
    ) {
        ListPerformanceHeader(
            engineName = PerformanceEngine.ViewCompose.displayName,
            revision = revision,
            onMutate = {
                revisionState.value = revisionState.value + 1
            },
            onReset = {
                revisionState.value = 0
            },
        )
        LazyColumn(
            items = rows,
            key = PerformanceListRow::id,
            contentType = { "performance-list-row" },
            contentPadding = 8.dp,
            spacing = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { row ->
            PerformanceListRow(row)
        }
    }
}

private fun UiTreeBuilder.ListPerformanceHeader(
    engineName: String,
    revision: Int,
    onMutate: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        spacing = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(PERFORMANCE_SURFACE_COLOR)
            .padding(12.dp),
    ) {
        Text(
            text = "$engineName List Ready",
            style = TextDefaults.titleMediumStyle(),
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
        )
        Text(
            text = "List revision $revision",
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
        )
        Row(
            spacing = 8.dp,
            verticalAlignment = VerticalAlignment.Center,
        ) {
            PerformanceAction(
                text = "Mutate list",
                onClick = onMutate,
            )
            PerformanceAction(
                text = "Reset list",
                onClick = onReset,
            )
        }
    }
}

private fun UiTreeBuilder.PerformanceAction(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        contentColor = 0xFFFFFFFF.toInt(),
        modifier = Modifier
            .backgroundColor(PERFORMANCE_PRIMARY_COLOR)
            .cornerRadius(8.dp)
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp,
            ),
    ) {
        Text(
            text = text,
            color = 0xFFFFFFFF.toInt(),
        )
    }
}

/**
 * 单行使用稳定 key 的 Surface，便于测量列表重排时的节点复用。
 * Row surface uses a stable key so list reordering measures node reuse.
 */
private fun UiTreeBuilder.PerformanceListRow(row: PerformanceListRow) {
    Surface(
        key = row.id,
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(PERFORMANCE_SURFACE_COLOR)
            .cornerRadius(10.dp)
            .padding(10.dp),
    ) {
        Row(
            spacing = 10.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                contentColor = row.accentColor,
                modifier = Modifier
                    .width(6.dp)
                    .height(44.dp)
                    .backgroundColor(row.accentColor)
                    .cornerRadius(3.dp),
            ) {}
            Column(
                spacing = 3.dp,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = row.title,
                    color = PERFORMANCE_PRIMARY_TEXT_COLOR,
                    maxLines = 1,
                )
                Text(
                    text = row.subtitle,
                    style = TextDefaults.bodySmallStyle(),
                    color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                    maxLines = 1,
                )
            }
            Surface(
                contentColor = PERFORMANCE_PRIMARY_COLOR,
                modifier = Modifier
                    .backgroundColor(PERFORMANCE_BADGE_COLOR)
                    .cornerRadius(12.dp)
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp,
                    ),
            ) {
                Text(
                    text = row.badge,
                    style = TextDefaults.labelMediumStyle(),
                    color = PERFORMANCE_PRIMARY_COLOR,
                    maxLines = 1,
                )
            }
        }
    }
}
