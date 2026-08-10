package com.viewcompose.oneui7.samples

import com.viewcompose.oneui7.OneUi7Button
import com.viewcompose.oneui7.OneUi7ButtonVariant
import com.viewcompose.oneui7.OneUi7NavigationBar
import com.viewcompose.oneui7.OneUi7NavigationItem
import com.viewcompose.oneui7.OneUi7Surface
import com.viewcompose.oneui7.OneUi7Switch
import com.viewcompose.oneui7.OneUi7TextField
import com.viewcompose.oneui7.OneUi7Theme
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.unit.dp

/** Builds the complete public One UI 7 five-component alpha slice with caller-owned state. */
fun UiTreeBuilder.oneUi7ComponentsSample() {
    val checked = rememberSaveable(key = "one-ui-switch") { mutableStateOf(true) }
    val selected = rememberSaveable(key = "one-ui-navigation") { mutableStateOf(0) }
    val accountName = rememberTextFieldState("Galaxy")
    val destinations = listOf(
        OneUi7NavigationItem(key = "home", label = "Home"),
        OneUi7NavigationItem(key = "search", label = "Search"),
        OneUi7NavigationItem(key = "profile", label = "Profile"),
    )

    OneUi7Theme {
        Column(spacing = 16.dp) {
            OneUi7Button(text = "Continue", onClick = {})
            OneUi7Button(
                text = "Later",
                onClick = {},
                variant = OneUi7ButtonVariant.Neutral,
            )
            OneUi7Button(
                text = "Flat action",
                onClick = {},
                variant = OneUi7ButtonVariant.Flat,
            )
            OneUi7Surface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "One UI 7 alpha surface",
                    color = Theme.colors.onSurface,
                    style = Theme.typography.bodyMedium,
                )
            }
            OneUi7Switch(
                text = "Sync devices",
                checked = checked.value,
                onCheckedChange = { checked.value = it },
                modifier = Modifier.fillMaxWidth(),
            )
            OneUi7TextField(
                state = accountName,
                label = "Account name",
                placeholder = "Name",
                supportingText = "Uses the native Android editing core.",
                modifier = Modifier.fillMaxWidth(),
            )
            OneUi7NavigationBar(
                items = destinations,
                selectedIndex = selected.value,
                onItemSelected = { selected.value = it },
            )
        }
    }
}
