package com.viewcompose.ui.foundation

import org.junit.Assert.assertThrows
import org.junit.Test

class ComponentOverrideValidationTest {
    @Test
    fun `appearance overrides reject negative dimensions at construction`() {
        val invalidConstructors = listOf<() -> Any>(
            { IconButtonOverrides(size = (-1).dp) },
            { SegmentedControlOverrides(minimumHeight = (-1).dp) },
            { TextFieldOverrides(horizontalPadding = (-1).dp) },
            { CheckboxOverrides(minimumHeight = (-1).dp) },
            { SwitchOverrides(minimumHeight = (-1).dp) },
            { RadioButtonOverrides(minimumHeight = (-1).dp) },
            { SliderOverrides(minimumHeight = (-1).dp) },
            { LinearProgressIndicatorOverrides(trackThickness = (-1).dp) },
            { CircularProgressIndicatorOverrides(size = (-1).dp) },
            { TabRowOverrides(itemSpacing = (-1).dp) },
            { NavigationBarOverrides(height = (-1).dp) },
            { FloatingActionButtonOverrides(elevation = (-1).dp) },
            { ExtendedFloatingActionButtonOverrides(iconSpacing = (-1).dp) },
            { TopAppBarOverrides(titleStartPadding = (-1).dp) },
            { BottomAppBarOverrides(elevation = (-1).dp) },
            { BadgeOverrides(dotSize = (-1).dp) },
            { AlertDialogOverrides(contentPadding = (-1).dp) },
            { ModalBottomSheetOverrides(scrimOpacity = -0.01f) },
            { ModalBottomSheetOverrides(scrimOpacity = Float.NaN) },
        )

        invalidConstructors.forEach { construct ->
            assertThrows(IllegalArgumentException::class.java) { construct() }
        }
    }

    @Test
    fun `resolved bottom sheet appearance rejects invalid opacity`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModalBottomSheetAppearance(
                containerColor = 0,
                contentColor = 0,
                shape = com.viewcompose.ui.shape.UiShape.rounded(0.dp),
                scrimOpacity = 1.01f,
                navigationBarColor = ModalBottomSheetNavigationBarColor.PlatformDefault,
            )
        }
    }
}
