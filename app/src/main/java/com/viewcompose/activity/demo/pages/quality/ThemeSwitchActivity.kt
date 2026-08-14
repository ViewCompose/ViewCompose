package com.viewcompose

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SegmentedControlSize
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.unit.dp

/** Demonstrates one observable application theme choice across two independent Activity sessions. */
class ThemeSwitchActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_theme_switch_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.ThemeSwitchPage(root)
    }

    companion object {
        internal fun newIntent(context: Context): Intent = Intent(context, ThemeSwitchActivity::class.java)
    }
}

private fun UiTreeBuilder.ThemeSwitchPage(root: ViewGroup) {
    val themeModeState = DemoThemeSession.modeState
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = "当前二级 Activity 主题: ${themeModeState.value.name}",
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.THEME_SWITCH_SECONDARY_STATUS),
        )
        Text(
            text = "这里与首页持有不同 RenderSession，但共同观察应用级 DemoThemeSession。切换后返回，首页应立即显示相同主题。",
            color = TextDefaults.secondaryColor(),
        )
        SegmentedControl(
            items = listOf("System", "Light", "Dark"),
            selectedIndex = themeModeState.value.ordinal,
            onSelectionChange = { index ->
                themeModeState.value = DemoThemeMode.entries[index]
            },
            size = SegmentedControlSize.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.THEME_SWITCH_SECONDARY_CONTROL),
        )
        Button(
            text = "切换后返回",
            onClick = { root.context.findAppCompatActivity()?.finish() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.THEME_SWITCH_SECONDARY_RETURN),
        )
    }
}
