package com.viewcompose.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compose 对照版本的列表性能场景。
 * Compose control implementation of the list performance scenario.
 */
@Composable
internal fun ComposeListPerformanceScreen() {
    var revision by remember { mutableIntStateOf(0) }
    val rows = performanceListRows(revision)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(PERFORMANCE_BACKGROUND_COLOR)),
    ) {
        ComposeListPerformanceHeader(
            engineName = PerformanceEngine.Compose.displayName,
            revision = revision,
            onMutate = {
                revision += 1
            },
            onReset = {
                revision = 0
            },
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = rows,
                key = PerformanceListRow::id,
                contentType = { "performance-list-row" },
            ) { row ->
                ComposePerformanceListRow(row)
            }
        }
    }
}

@Composable
private fun ComposeListPerformanceHeader(
    engineName: String,
    revision: Int,
    onMutate: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(PERFORMANCE_SURFACE_COLOR))
            .padding(12.dp),
    ) {
        PerformanceText(
            text = "$engineName List Ready",
            sizeSp = 18,
            weight = FontWeight.SemiBold,
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
        )
        PerformanceText(
            text = "List revision $revision",
            sizeSp = 14,
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposePerformanceAction(
                text = "Mutate list",
                onClick = onMutate,
            )
            ComposePerformanceAction(
                text = "Reset list",
                onClick = onReset,
            )
        }
    }
}

@Composable
private fun ComposePerformanceAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(
                color = Color(PERFORMANCE_PRIMARY_COLOR),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp,
            ),
    ) {
        PerformanceText(
            text = text,
            sizeSp = 14,
            weight = FontWeight.Medium,
            color = 0xFFFFFFFF.toInt(),
        )
    }
}

@Composable
private fun ComposePerformanceListRow(row: PerformanceListRow) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(PERFORMANCE_SURFACE_COLOR),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
    ) {
        Spacer(
            modifier = Modifier
                .width(6.dp)
                .height(44.dp)
                .background(
                    color = Color(row.accentColor),
                    shape = RoundedCornerShape(3.dp),
                ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            PerformanceText(
                text = row.title,
                sizeSp = 14,
                weight = FontWeight.Medium,
                color = PERFORMANCE_PRIMARY_TEXT_COLOR,
            )
            PerformanceText(
                text = row.subtitle,
                sizeSp = 12,
                color = PERFORMANCE_SECONDARY_TEXT_COLOR,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(
                    color = Color(PERFORMANCE_BADGE_COLOR),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(
                    horizontal = 8.dp,
                    vertical = 4.dp,
                ),
        ) {
            PerformanceText(
                text = row.badge,
                sizeSp = 12,
                weight = FontWeight.Medium,
                color = PERFORMANCE_PRIMARY_COLOR,
            )
        }
    }
}

/**
 * Compose 侧复用的文本渲染函数，保持字号、粗细和省略规则集中管理。
 * Shared Compose text renderer that centralizes size, weight, and ellipsis rules.
 */
@Composable
internal fun PerformanceText(
    text: String,
    sizeSp: Int,
    color: Int,
    weight: FontWeight = FontWeight.Normal,
) {
    BasicText(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = Color(color),
            fontSize = sizeSp.sp,
            fontWeight = weight,
        ),
    )
}
