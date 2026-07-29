package com.viewcompose.preview.catalog.domain

import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.widget.core.CircularProgressIndicator
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Divider
import com.viewcompose.widget.core.LinearProgressIndicator
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.SurfaceVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.ui.unit.dp

internal object FeedbackPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "feedback-progress",
            title = "Linear/Circular Progress",
            domain = PreviewDomain.Feedback,
            content = {
                Column(
                    spacing = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(text = "加载进度")
                    LinearProgressIndicator(progress = 0.62f)
                    Row(
                        arrangement = MainAxisArrangement.Start,
                    ) {
                        CircularProgressIndicator(progress = 0.35f)
                    }
                }
            },
        ),
        PreviewSpec(
            id = "feedback-overlay-static",
            title = "Overlay Static Mock",
            domain = PreviewDomain.Feedback,
            content = {
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Column(spacing = 6.dp) {
                            Text(text = "Dialog (Static Mock)")
                            Divider()
                            Text(text = "Preview 中不走真实窗口，使用静态内容模拟。")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Column(spacing = 6.dp) {
                            Text(text = "Popup / BottomSheet (Static Mock)")
                            Divider()
                            Text(text = "真实弹窗行为继续由 qaFull instrumentation 覆盖。")
                        }
                    }
                }
            },
        ),
    )
}
