package com.viewcompose

import android.graphics.Typeface
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonSize
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.CircularProgressIndicator
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.LinearProgressIndicator
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeOverride
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

private val DemoDelayedImageLoader = UiImageLoader { target, request ->
    val imageView = (target as? PlatformUiImageTarget)?.target as? ImageView
        ?: error("DemoDelayedImageLoader requires an ImageView target")
    val resultResource = when (val source = request.source) {
        is ImageSource.Resource -> source.resId
        is ImageSource.Model -> source.value as? Int
            ?: error("Demo model must contain a drawable resource ID")
        else -> error("Demo loader supports only Resource and Model sources")
    }
    val delayMillis = if (request.source is ImageSource.Model) 320L else 120L
    val completion = Runnable { imageView.setImageResource(resultResource) }
    imageView.postDelayed(completion, delayMillis)
    UiImageLoadHandle { imageView.removeCallbacks(completion) }
}

@ViewComposePreview(name = "Foundations · Progress", group = "Demo/Sections")
internal fun UiTreeBuilder.FoundationsProgressSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "进度指示器",
        subtitle = "线性和圆形进度指示器，通过框架默认值设置样式。",
    ) {
        Text(
            text = "线性指示器跟随当前组件 token，圆形可运行确定/不确定模式。",
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
        LinearProgressIndicator(
            progress = 0.68f,
            modifier = Modifier
                .fillMaxWidth()
                .margin(top = 12.dp, bottom = 12.dp),
        )
        Row(
            spacing = 16.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CircularProgressIndicator(progress = 0.42f)
            CircularProgressIndicator()
            Text(
                text = "进度指示器已包含在 P1 控件面中。",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@ViewComposePreview(name = "Foundations · Media", group = "Demo/Sections")
internal fun UiTreeBuilder.FoundationsMediaSection() {
    val pipelineMode = remember { mutableStateOf(0) }
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "Image + Icon",
        subtitle = "按 source、回退、请求替换和图标样式分组验证媒体管线。",
    ) {
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "1. 本地 Resource 也经过 Loader")
                Text(
                    text = "64dp 目标显式使用 64×64dp 解码请求，并由 contentScale 完成裁剪。",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Row(
                    spacing = 16.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        source = ImageSource.Resource(R.drawable.demo_media_image),
                        contentDescription = "Loader 加载的本地图片",
                        contentScale = ImageContentScale.Crop,
                        requestOptions = UiImageRequestOptions(
                            decodeSize = UiImageDecodeSize.Fixed(width = 64.dp, height = 64.dp),
                        ),
                        modifier = Modifier
                            .size(64.dp, 64.dp)
                            .shape(Theme.shapes.medium),
                    )
                    Text(
                        text = "安装 Coil 时复用解码、裁剪和缓存；未安装 Loader 时仍可直接显示。",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "2. URL 与空 source")
                Text(
                    text = "左侧发起网络请求；右侧 source = null，不启动 Loader，直接显示 fallback。",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Row(
                    spacing = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        spacing = 6.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "远程 URL", style = UiTextStyle(fontSizeSp = 13.sp))
                        Image(
                            source = ImageSource.Url("https://picsum.photos/seed/viewcompose-demo/640/360"),
                            contentDescription = "远程 URL 图片",
                            contentScale = ImageContentScale.Crop,
                            placeholder = ImageSource.Resource(R.drawable.demo_media_image),
                            error = ImageSource.Resource(R.drawable.demo_media_image),
                            fallback = ImageSource.Resource(R.drawable.demo_media_image),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .shape(Theme.shapes.medium)
                                .testTag(DemoTestTags.FOUNDATIONS_REMOTE_IMAGE),
                        )
                    }
                    Column(
                        spacing = 6.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "直接 fallback", style = UiTextStyle(fontSizeSp = 13.sp))
                        Image(
                            source = null,
                            contentDescription = "空 source 的回退图片",
                            contentScale = ImageContentScale.Crop,
                            fallback = ImageSource.Resource(R.drawable.demo_media_image),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .shape(Theme.shapes.medium)
                                .testTag(DemoTestTags.FOUNDATIONS_FALLBACK_IMAGE),
                        )
                    }
                }
            }
        }
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "3. 请求替换与取消")
                Text(
                    text = "点击按钮复用同一个 ImageView，依次验证 Resource、null fallback 和延迟 Model。",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
                ProvideImageLoader(DemoDelayedImageLoader) {
                    val source = when (pipelineMode.value) {
                        0 -> ImageSource.Resource(R.drawable.demo_media_image)
                        1 -> null
                        else -> ImageSource.Model(
                            value = R.drawable.demo_media_icon,
                            stableKey = "demo-delayed-model-v1",
                        )
                    }
                    Image(
                        source = source,
                        contentDescription = "图片管线切换结果",
                        contentScale = ImageContentScale.Crop,
                        placeholder = ImageSource.Resource(R.drawable.demo_media_icon),
                        fallback = ImageSource.Resource(R.drawable.demo_media_image),
                        requestOptions = UiImageRequestOptions(
                            decodeSize = UiImageDecodeSize.Fixed(width = 320.dp, height = 180.dp),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                            .shape(Theme.shapes.medium),
                    )
                    Button(
                        text = when (pipelineMode.value) {
                            0 -> "当前：Loader 加载本地 Resource"
                            1 -> "当前：空 source 直接 fallback"
                            else -> "当前：Loader 加载延迟 Model"
                        },
                        onClick = { pipelineMode.value = (pipelineMode.value + 1) % 3 },
                        variant = ButtonVariant.Outlined,
                        size = ButtonSize.Compact,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "4. Icon 颜色与 IconButton 变体")
                Text(
                    text = "Icon 跟随 ContentColor.current；按钮变体分行排列，避免窄屏拥挤。",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Row(
                    spacing = 12.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "默认 ContentColor 图标",
                        )
                    }
                    UiThemeOverride(colors = { copy(onSurface = secondary) }) {
                        Surface(modifier = Modifier.padding(8.dp)) {
                            Icon(
                                source = ImageSource.Resource(R.drawable.demo_media_icon),
                                contentDescription = "Secondary ContentColor 图标",
                            )
                        }
                    }
                    Text(
                        text = "默认 / Secondary",
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "默认 / Primary / Tonal",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                )
                Row(
                    spacing = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "默认图标按钮",
                    )
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "Primary 图标按钮",
                        variant = ButtonVariant.Primary,
                        modifier = Modifier.testTag(DemoTestTags.FOUNDATIONS_PRIMARY_ICON_BUTTON),
                    )
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "Tonal 图标按钮",
                        variant = ButtonVariant.Tonal,
                    )
                }
                Text(
                    text = "Outlined / Disabled",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                )
                Row(
                    spacing = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "Outlined 图标按钮",
                        variant = ButtonVariant.Outlined,
                    )
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "禁用图标按钮",
                        enabled = false,
                    )
                }
            }
        }
    }
}

@ViewComposePreview(name = "Foundations · Typography", group = "Demo/Sections")
internal fun UiTreeBuilder.FoundationsTypographySection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "Text 排版属性",
        subtitle = "展示 fontWeight、fontFamily、letterSpacing、lineHeight、textDecoration、maxLines + overflow。",
    ) {
        Text(
            text = "fontWeight 字重",
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Column(
            spacing = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Text(text = "Normal (400)", style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 400))
            Text(text = "Medium (500)", style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 500))
            Text(text = "Bold (700)", style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 700))
            Text(text = "Black (900)", style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 900))
        }
        Divider(modifier = Modifier.margin(bottom = 12.dp))
        Text(
            text = "fontFamily 字体族",
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Column(
            spacing = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Text(text = "默认字体 (Default)", style = UiTextStyle(fontSizeSp = 15.sp))
            Text(text = "等宽字体 (Monospace)", style = UiTextStyle(fontSizeSp = 15.sp, fontFamily = Typeface.MONOSPACE))
            Text(text = "衬线字体 (Serif)", style = UiTextStyle(fontSizeSp = 15.sp, fontFamily = Typeface.SERIF))
            Text(text = "无衬线字体 (Sans-Serif)", style = UiTextStyle(fontSizeSp = 15.sp, fontFamily = Typeface.SANS_SERIF))
        }
        Divider(modifier = Modifier.margin(bottom = 12.dp))
        Text(
            text = "letterSpacing 字间距",
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Column(
            spacing = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Text(text = "默认字间距", style = UiTextStyle(fontSizeSp = 15.sp))
            Text(text = "字间距 0.05em", style = UiTextStyle(fontSizeSp = 15.sp, letterSpacingEm = 0.05f))
            Text(text = "字间距 0.15em", style = UiTextStyle(fontSizeSp = 15.sp, letterSpacingEm = 0.15f))
        }
        Divider(modifier = Modifier.margin(bottom = 12.dp))
        Text(
            text = "lineHeight 行高",
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Column(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Text(
                text = "默认行高的多行文本。这段文字的行高是框架默认值。可以对比下方设置了明确行高的文本。",
                style = UiTextStyle(fontSizeSp = 14.sp),
            )
            Text(
                text = "行高 24sp 的多行文本。这段文字明确设置了 lineHeightSp = 24，行间距更大更宽松。",
                style = UiTextStyle(fontSizeSp = 14.sp, lineHeightSp = 24.sp),
            )
        }
        Divider(modifier = Modifier.margin(bottom = 12.dp))
        Text(
            text = "textDecoration 文本装饰",
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Column(
            spacing = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Text(text = "无装饰 (None)", textDecoration = TextDecoration.None)
            Text(text = "下划线 (Underline)", textDecoration = TextDecoration.Underline)
            Text(text = "删除线 (LineThrough)", textDecoration = TextDecoration.LineThrough)
            Text(text = "下划线+删除线", textDecoration = TextDecoration.UnderlineLineThrough)
        }
        Divider(modifier = Modifier.margin(bottom = 12.dp))
        Text(
            text = "maxLines + overflow 截断",
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .margin(bottom = 8.dp),
        ) {
            Text(
                text = "这是一段很长的文本，设置了 maxLines=1 和 overflow=Ellipsis。当文本超出一行时会在末尾显示省略号…来表示内容被截断。",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = "这是一段很长的文本，设置了 maxLines=2 和 overflow=Ellipsis。当文本超出两行时会在末尾显示省略号。这段文字故意写得很长以触发截断效果，观察第二行末尾的省略号。",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun UiTreeBuilder.FoundationsJumpSection(
    onOpenCapability: (Class<out AppCompatActivity>) -> Unit,
) {
    ScenarioSection(
        kind = ScenarioKind.Benchmark,
        title = "跳转到其他章节",
        subtitle = "这些按钮在顶层 demo 章节间导航。",
    ) {
        BenchmarkRouteCallout(
            route = "Catalog -> Foundations -> 指南页 -> 跳转",
            stableTargets = listOf("打开布局", "打开输入", "打开状态", "打开集合", "打开互操作"),
        )
        Button(text = "打开布局", modifier = Modifier.margin(bottom = 8.dp), onClick = { onOpenCapability(LayoutsActivity::class.java) })
        Button(text = "打开输入", modifier = Modifier.margin(bottom = 8.dp), onClick = { onOpenCapability(InputActivity::class.java) })
        Button(text = "打开状态", modifier = Modifier.margin(bottom = 8.dp), onClick = { onOpenCapability(StateActivity::class.java) })
        Button(text = "打开集合", onClick = { onOpenCapability(CollectionsActivity::class.java) })
        Button(text = "打开互操作", modifier = Modifier.margin(top = 8.dp), onClick = { onOpenCapability(InteropActivity::class.java) })
    }
}

@ViewComposePreview(name = "Foundations · Surface", group = "Demo/Sections")
internal fun UiTreeBuilder.FoundationsSurfaceSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "当前控件面",
        subtitle = "第一个垂直切片包含以下框架控件。",
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth().margin(bottom = 8.dp),
        ) {
            Button(text = "Primary", variant = ButtonVariant.Primary, size = ButtonSize.Compact, modifier = Modifier.weight(1f))
            Button(text = "Tonal", variant = ButtonVariant.Tonal, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
            Button(text = "Outline", variant = ButtonVariant.Outlined, size = ButtonSize.Large, modifier = Modifier.weight(1f))
        }
        Text(text = "Text, TextField, EmailField, PasswordField, NumberField, TextArea")
        Text(text = "Row, Column, Box, Divider, Spacer, FlexibleSpacer, LazyColumn")
        Text(text = "进度指示器, AndroidView 互操作, TabRow + HorizontalPager, 状态运行时, 副作用运行时")
    }
}

@ViewComposePreview(name = "Foundations · Verification", group = "Demo/Sections")
internal fun UiTreeBuilder.FoundationsVerificationSection() {
    VerificationNotesSection(
        what = "基础组件应验证当前主题、媒体和按钮家族在章节导航和主题切换下的一致性渲染。",
        howToVerify = listOf(
            "切换顶部 theme mode，确认所有 section 颜色、圆角和点击态一起更新。",
            "观察远程图片、本地图标和 IconButton，确认不会出现大面积空白或错误布局。",
            "观察排版页面的 fontWeight/fontFamily/letterSpacing/lineHeight/textDecoration 效果。",
            "确认 maxLines + overflow=Ellipsis 截断效果正确显示省略号。",
        ),
        expected = listOf(
            "所有基础控件在亮色、暗色和系统模式下都保持可读。",
            "Image 的 placeholder / fallback 场景可见，Icon 跟随 ContentColor 变化。",
            "Text 排版属性（字重、字体族、字间距、行高、装饰线）均正确渲染。",
        ),
    )
}
