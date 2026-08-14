package com.viewcompose

import androidx.annotation.StringRes
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.ProvideLocals
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.provides
import com.viewcompose.ui.foundation.uiLocalOf
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

private data class DemoBusinessTokens(
    val cardColor: Int,
    @StringRes val badgeLabelRes: Int,
)

private val LocalDemoBusinessTokens = uiLocalOf {
    DemoBusinessTokens(
        cardColor = 0xFF355E3B.toInt(),
        badgeLabelRes = R.string.demo_foundations_locals_default_token,
    )
}

private val LocalDemoBusinessFeatureEnabled = uiLocalOf { false }

internal fun UiTreeBuilder.FoundationsLocalsFixture(scenario: DemoScenarioSpec?) {
    FoundationsFixtureList(sections = listOf("locals")) {
        Column(spacing = 10.dp, modifier = Modifier.fillMaxWidth()) {
            FoundationsSummary(scenario)
            BusinessLocalCard(
                scopeLabel = stringResource(R.string.demo_foundations_locals_default_scope),
                tokens = UiLocals.current(LocalDemoBusinessTokens),
                featureEnabled = UiLocals.current(LocalDemoBusinessFeatureEnabled),
            )
            ProvideLocals(
                LocalDemoBusinessTokens provides DemoBusinessTokens(
                    cardColor = Theme.colors.secondary,
                    badgeLabelRes = R.string.demo_foundations_locals_campaign_token,
                ),
                LocalDemoBusinessFeatureEnabled provides true,
            ) {
                BusinessLocalCard(
                    scopeLabel = stringResource(R.string.demo_foundations_locals_override_scope),
                    tokens = UiLocals.current(LocalDemoBusinessTokens),
                    featureEnabled = UiLocals.current(LocalDemoBusinessFeatureEnabled),
                    modifier = Modifier.foundationsScenarioTarget(
                        scenario,
                        DemoAutomationRole.Target,
                    ),
                )
            }
            BusinessLocalCard(
                scopeLabel = stringResource(R.string.demo_foundations_locals_restored_scope),
                tokens = UiLocals.current(LocalDemoBusinessTokens),
                featureEnabled = UiLocals.current(LocalDemoBusinessFeatureEnabled),
            )
        }
    }
}

private fun UiTreeBuilder.BusinessLocalCard(
    scopeLabel: String,
    tokens: DemoBusinessTokens,
    featureEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        spacing = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .backgroundColor(SurfaceDefaults.backgroundColor())
            .shape(SurfaceDefaults.shape())
            .padding(12.dp),
    ) {
        Text(text = scopeLabel, style = Theme.typography.titleMedium)
        Row(
            spacing = 8.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .weight(1f)
                    .backgroundColor(tokens.cardColor)
                    .shape(Theme.shapes.small),
            ) {}
            Text(
                text = stringResource(tokens.badgeLabelRes),
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.weight(2f),
            )
        }
        Text(
            text = stringResource(
                R.string.demo_foundations_locals_feature_state,
                featureEnabled,
            ),
            style = UiTextStyle(fontSizeSp = 12.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}
