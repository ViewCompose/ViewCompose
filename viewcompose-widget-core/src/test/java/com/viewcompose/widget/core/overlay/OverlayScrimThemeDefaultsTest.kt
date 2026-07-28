package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core overlay 中的 Overlay Scrim Theme Defaults 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Overlay Scrim Theme Defaults behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.VNode
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayScrimThemeDefaultsTest {
    @Test
    fun `dialog default scrim opacity follows theme overlays`() {
        val store = OverlayRequestStore()
        val customTheme = UiThemeDefaults.light().copy(
            overlays = UiOverlays(scrimOpacity = 0.56f),
        )

        val tree = OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                UiTheme(tokens = customTheme) {
                    Dialog(
                        visible = true,
                        requestKey = "dialog_theme_scrim",
                    ) {
                        Text(text = "Dialog body")
                    }
                }
            }
        }

        assertEquals(emptyList<VNode>(), tree)
        val request = store.currentRequests().single()
        val spec = request.payload as DialogOverlaySpec
        assertEquals(0.56f, spec.scrimOpacity)
    }

    @Test
    fun `bottom sheet default scrim opacity follows theme overlays`() {
        val store = OverlayRequestStore()
        val customTheme = UiThemeDefaults.light().copy(
            overlays = UiOverlays(scrimOpacity = 0.61f),
        )

        val tree = OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                UiTheme(tokens = customTheme) {
                    ModalBottomSheet(
                        visible = true,
                        requestKey = "sheet_theme_scrim",
                    ) {
                        Text(text = "Sheet body")
                    }
                }
            }
        }

        assertEquals(emptyList<VNode>(), tree)
        val request = store.currentRequests().single()
        val spec = request.payload as ModalBottomSheetOverlaySpec
        assertEquals(0.61f, spec.scrimOpacity)
    }
}
