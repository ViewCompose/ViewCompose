package com.viewcompose

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Card
import com.viewcompose.ui.foundation.CardVariant
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.Chip
import com.viewcompose.ui.foundation.ChipVariant
import com.viewcompose.ui.foundation.CircularProgressIndicator
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.FabSize
import com.viewcompose.ui.foundation.FloatingActionButton
import com.viewcompose.ui.foundation.FlowRow
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.LinearProgressIndicator
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.OutlinedCard
import com.viewcompose.ui.foundation.RadioButton
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldVariant
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp

/** Emits the static component boards captured by [Material3VisualBaselineUiTest]. */
internal fun UiTreeBuilder.Material3VisualBaselinePage(page: Int) {
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background)
            .systemBarsInsetsPadding()
            .padding(16.dp)
            .testTag(DemoTestTags.MATERIAL3_BASELINE_ROOT),
    ) {
        when (page) {
            Material3VisualBaselineActivity.PAGE_ACTIONS -> Material3ActionsBaseline()
            Material3VisualBaselineActivity.PAGE_INPUTS -> Material3InputsBaseline()
            else -> Material3SurfacesBaseline()
        }
    }
}

private fun UiTreeBuilder.Material3BaselineHeader(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = Theme.typography.headlineSmall,
        color = Theme.colors.onBackground,
    )
    Text(
        text = subtitle,
        style = Theme.typography.bodyMedium,
        color = Theme.colors.onSurfaceVariant,
    )
}

private fun UiTreeBuilder.Material3ActionsBaseline() {
    Material3BaselineHeader(
        title = "Material 3 · Actions",
        subtitle = "Buttons, selection, cards, shapes, and FAB defaults",
    )
    Row(
        spacing = 8.dp,
        verticalAlignment = VerticalAlignment.Center,
    ) {
        Button(
            text = "Primary",
            onClick = {},
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_ACTION_PRIMARY),
        )
        Button(text = "Outlined", variant = ButtonVariant.Outlined, onClick = {})
        Button(text = "Disabled", enabled = false, onClick = {})
    }
    Row(
        spacing = 8.dp,
        verticalAlignment = VerticalAlignment.Center,
    ) {
        IconButton(
            icon = ImageSource.Resource(R.drawable.demo_media_icon),
            contentDescription = "Standard icon button",
            onClick = {},
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_ACTION_ICON),
        )
        IconButton(
            icon = ImageSource.Resource(R.drawable.demo_media_icon),
            contentDescription = "Tonal icon button",
            variant = ButtonVariant.Tonal,
            onClick = {},
        )
        FlowRow(
            horizontalSpacing = 8.dp,
            verticalSpacing = 4.dp,
        ) {
            Chip(label = "Assist", onClick = {})
            Chip(
                label = "Selected",
                variant = ChipVariant.Filter,
                selected = true,
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_ACTION_CHIP),
            )
        }
    }
    SegmentedControl(
        items = listOf("Day", "Week", "Month"),
        selectedIndex = 1,
        onSelectionChange = {},
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        spacing = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        listOf(
            CardVariant.Filled to "Filled",
            CardVariant.Elevated to "Elevated",
            CardVariant.Outlined to "Outlined",
        ).forEachIndexed { index, (variant, label) ->
            Card(
                variant = variant,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (index == 0) {
                            Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_ACTION_CARD)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    text = label,
                    style = Theme.typography.labelMedium,
                    color = Theme.colors.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
        }
    }
    Row(
        spacing = 16.dp,
        verticalAlignment = VerticalAlignment.Center,
    ) {
        FabSize.entries.forEachIndexed { index, size ->
            FloatingActionButton(
                onClick = {},
                size = size,
                modifier = if (index == 1) {
                    Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_ACTION_FAB)
                } else {
                    Modifier
                },
            ) {
                Icon(source = ImageSource.Resource(R.drawable.demo_media_icon))
            }
        }
    }
}

private fun UiTreeBuilder.Material3InputsBaseline() {
    val searchState = rememberTextFieldState()
    val filledState = rememberTextFieldState("Ada Lovelace")
    val errorState = rememberTextFieldState("invalid@")
    val selectedState = remember { mutableStateOf(true) }
    val sliderState = remember { mutableStateOf(64) }

    Material3BaselineHeader(
        title = "Material 3 · Inputs",
        subtitle = "Fields, selection controls, slider, and progress colors",
    )
    SearchBar(
        state = searchState,
        placeholder = "Search components",
        leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DemoTestTags.MATERIAL3_BASELINE_INPUT_SEARCH),
    )
    TextField(
        state = filledState,
        label = "Name",
        supportingText = "Filled field · body large",
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DemoTestTags.MATERIAL3_BASELINE_INPUT_FIELD),
    )
    TextField(
        state = errorState,
        label = "Email",
        supportingText = "Use a complete email address",
        variant = TextFieldVariant.Outlined,
        isError = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        spacing = 8.dp,
        verticalAlignment = VerticalAlignment.Center,
    ) {
        Checkbox(
            text = "Checked",
            checked = selectedState.value,
            onCheckedChange = { selectedState.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_INPUT_CHECKBOX),
        )
        Switch(
            text = "On",
            checked = selectedState.value,
            onCheckedChange = { selectedState.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_INPUT_SWITCH),
        )
    }
    Row(
        spacing = 8.dp,
        verticalAlignment = VerticalAlignment.Center,
    ) {
        RadioButton(text = "Selected", checked = true, onCheckedChange = {})
        RadioButton(text = "Disabled", checked = false, enabled = false, onCheckedChange = {})
    }
    Slider(
        value = sliderState.value,
        onValueChange = { sliderState.value = it },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DemoTestTags.MATERIAL3_BASELINE_INPUT_SLIDER),
    )
}

private fun UiTreeBuilder.Material3SurfacesBaseline() {
    Material3BaselineHeader(
        title = "Material 3 · Surfaces",
        subtitle = "Typography, surface hierarchy, progress, and navigation",
    )
    Column(
        spacing = 2.dp,
        modifier = Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_SURFACE_TYPOGRAPHY),
    ) {
        Text(text = "Headline small", style = Theme.typography.headlineSmall)
        Text(text = "Title medium", style = Theme.typography.titleMedium)
        Text(text = "Body medium uses the complete type scale", style = Theme.typography.bodyMedium)
        Text(text = "LABEL LARGE", style = Theme.typography.labelLarge)
    }
    Row(
        spacing = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Highest",
                style = Theme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
            )
        }
        OutlinedCard(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Outline variant",
                style = Theme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
            )
        }
    }
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DemoTestTags.MATERIAL3_BASELINE_SURFACE_PROGRESS),
    ) {
        LinearProgressIndicator(
            progress = 0.64f,
            modifier = Modifier.fillMaxWidth(),
        )
        CircularProgressIndicator(progress = 0.64f)
    }
    NavigationBar(
        selectedIndex = 1,
        onItemSelected = {},
        modifier = Modifier.testTag(DemoTestTags.MATERIAL3_BASELINE_SURFACE_NAVIGATION),
    ) {
        Item(label = "Home", icon = ImageSource.Resource(R.drawable.demo_media_icon))
        Item(label = "Library", icon = ImageSource.Resource(R.drawable.demo_media_icon), badgeCount = 3)
        Item(label = "Profile", icon = ImageSource.Resource(R.drawable.demo_media_icon))
    }
}
