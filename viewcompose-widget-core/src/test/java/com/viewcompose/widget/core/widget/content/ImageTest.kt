package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core widget/content 中的 Image 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Image behavior in widget-core widget/content and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.spec.ImageNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTest {
    @Test
    fun `image emits semantic props`() {
        val tree = buildVNodeTree {
            Image(
                source = ImageSource.Resource(42),
                contentDescription = "Demo image",
                contentScale = ImageContentScale.Crop,
            )
        }

        val node = tree.single()
        val spec = node.spec as ImageNodeProps

        assertEquals(NodeType.Image, node.type)
        assertEquals(ImageSource.Resource(42), spec.source)
        assertEquals("Demo image", spec.contentDescription)
        assertEquals(ImageContentScale.Crop, spec.contentScale)
        assertTrue(node.spec is ImageNodeProps)
    }

    @Test
    fun `icon inherits local content color and default size`() {
        val tree = buildVNodeTree {
            ProvideLocal(LocalContentColor, 0xFF123456.toInt()) {
                Icon(
                    source = ImageSource.Resource(12),
                    contentDescription = "Local icon",
                )
            }
        }

        val node = tree.single()
        val spec = node.spec as ImageNodeProps
        val size = node.modifier.readModifierElements().last { it is SizeModifierElement } as SizeModifierElement

        assertEquals(NodeType.Image, node.type)
        assertEquals(0xFF123456.toInt(), spec.tint)
        assertEquals(ImageContentScale.Inside, spec.contentScale)
        assertEquals(UiDimension.Exact(24.dp), size.width)
        assertEquals(UiDimension.Exact(24.dp), size.height)
    }

    @Test
    fun `URL image inherits scoped loader`() {
        val loader = UiImageLoader { _, _: UiImageRequest -> UiImageLoadHandle {} }
        val tree = buildVNodeTree {
            ProvideImageLoader(loader) {
                Image(
                    source = ImageSource.Url("https://example.com/demo.png"),
                    placeholder = ImageSource.Resource(10),
                    error = ImageSource.Resource(11),
                    fallback = ImageSource.Resource(12),
                )
            }
        }

        val node = tree.single()
        val spec = node.spec as ImageNodeProps

        assertEquals(ImageSource.Url("https://example.com/demo.png"), spec.source)
        assertNotNull(spec.imageLoader)
        assertEquals(ImageSource.Resource(10), spec.placeholder)
        assertEquals(ImageSource.Resource(11), spec.error)
        assertEquals(ImageSource.Resource(12), spec.fallback)
    }

    @Test
    fun `image can emit null source for fallback handling`() {
        val loader = UiImageLoader { _, _: UiImageRequest -> UiImageLoadHandle {} }
        val tree = buildVNodeTree {
            ProvideImageLoader(loader) {
                Image(
                    source = null,
                    fallback = ImageSource.Resource(99),
                )
            }
        }

        val node = tree.single()
        val spec = node.spec as ImageNodeProps

        assertEquals(null, spec.source)
        assertEquals(ImageSource.Resource(99), spec.fallback)
    }

    private fun com.viewcompose.ui.modifier.Modifier.readModifierElements(): List<Any?> {
        val field = javaClass.getDeclaredField("elements")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as List<Any?>
    }
}
