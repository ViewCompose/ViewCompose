package com.viewcompose

import android.graphics.Typeface
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.FoundationsTypographyFixture(scenario: DemoScenarioSpec?) {
    FoundationsFixtureList(
        sections = listOf("overflow", "weight", "family", "spacing", "decoration"),
    ) { section ->
        when (section) {
            "overflow" -> FoundationsTypographyOverflow(scenario)
            "weight" -> FoundationsTypographyWeight()
            "family" -> FoundationsTypographyFamily()
            "spacing" -> FoundationsTypographySpacing()
            "decoration" -> FoundationsTypographyDecoration()
            else -> error("Unsupported foundations typography section: $section")
        }
    }
}

private fun UiTreeBuilder.FoundationsTypographyOverflow(scenario: DemoScenarioSpec?) {
    Column(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
        FoundationsSummary(scenario)
        Text(
            text = stringResource(R.string.demo_foundations_typography_overflow_title),
            style = Theme.typography.titleMedium,
        )
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.demo_foundations_typography_overflow_one_line),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.foundationsScenarioTarget(
                    scenario,
                    DemoAutomationRole.Target,
                ),
            )
        }
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.demo_foundations_typography_overflow_two_lines),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun UiTreeBuilder.FoundationsTypographyWeight() {
    TypographyGroup(R.string.demo_foundations_typography_weight_title) {
        Text(
            text = stringResource(R.string.demo_foundations_typography_weight_normal),
            style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 400),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_weight_medium),
            style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 500),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_weight_bold),
            style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 700),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_weight_black),
            style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 900),
        )
    }
}

private fun UiTreeBuilder.FoundationsTypographyFamily() {
    TypographyGroup(R.string.demo_foundations_typography_family_title) {
        Text(
            text = stringResource(R.string.demo_foundations_typography_family_default),
            style = UiTextStyle(fontSizeSp = 15.sp),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_family_monospace),
            style = UiTextStyle(fontSizeSp = 15.sp, fontFamily = Typeface.MONOSPACE),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_family_serif),
            style = UiTextStyle(fontSizeSp = 15.sp, fontFamily = Typeface.SERIF),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_family_sans),
            style = UiTextStyle(fontSizeSp = 15.sp, fontFamily = Typeface.SANS_SERIF),
        )
    }
}

private fun UiTreeBuilder.FoundationsTypographySpacing() {
    TypographyGroup(R.string.demo_foundations_typography_spacing_title) {
        Text(
            text = stringResource(R.string.demo_foundations_typography_spacing_default),
            style = UiTextStyle(fontSizeSp = 15.sp),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_spacing_wide),
            style = UiTextStyle(fontSizeSp = 15.sp, letterSpacingEm = 0.15f),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_line_height_default),
            style = UiTextStyle(fontSizeSp = 14.sp),
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_line_height_explicit),
            style = UiTextStyle(fontSizeSp = 14.sp, lineHeightSp = 24.sp),
        )
    }
}

private fun UiTreeBuilder.FoundationsTypographyDecoration() {
    TypographyGroup(R.string.demo_foundations_typography_decoration_title) {
        Text(
            text = stringResource(R.string.demo_foundations_typography_decoration_underline),
            textDecoration = TextDecoration.Underline,
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_decoration_line_through),
            textDecoration = TextDecoration.LineThrough,
        )
        Text(
            text = stringResource(R.string.demo_foundations_typography_decoration_combined),
            textDecoration = TextDecoration.UnderlineLineThrough,
        )
    }
}

private fun UiTreeBuilder.TypographyGroup(
    titleRes: Int,
    content: UiTreeBuilder.() -> Unit,
) {
    Column(
        spacing = 6.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 8.dp, bottom = 8.dp),
    ) {
        Divider(modifier = Modifier.margin(bottom = 8.dp))
        Text(text = stringResource(titleRes), style = Theme.typography.titleMedium)
        content()
    }
}
