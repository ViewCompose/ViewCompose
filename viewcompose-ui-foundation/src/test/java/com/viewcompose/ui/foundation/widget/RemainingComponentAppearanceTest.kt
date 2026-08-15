package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.BackgroundColorModifierElement
import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.modifier.MinWidthModifierElement
import com.viewcompose.ui.modifier.PaddingModifierElement
import com.viewcompose.ui.modifier.ShapeModifierElement
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RemainingComponentAppearanceTest {
    @Test
    fun `fab scopes merge by field and instance retains final precedence`() {
        val regularShape = UiShape.rounded(7.dp)
        val extendedShape = UiShape.rounded(11.dp)
        val tree = buildVNodeTree {
            Column {
                ProvideFloatingActionButtonOverrides(
                    FloatingActionButtonOverrides(
                        containerColor = 0xFF112233.toInt(),
                        shape = regularShape,
                    ),
                ) {
                    ProvideFloatingActionButtonOverrides(
                        FloatingActionButtonOverrides(contentColor = 0xFF445566.toInt()),
                    ) {
                        FloatingActionButton(
                            onClick = {},
                            overrides = FloatingActionButtonOverrides(
                                contentColor = 0xFF778899.toInt(),
                            ),
                        ) {
                            Text("Regular")
                        }
                    }
                }
                ProvideExtendedFloatingActionButtonOverrides(
                    ExtendedFloatingActionButtonOverrides(
                        containerColor = 0xFF223344.toInt(),
                        shape = extendedShape,
                        height = 61.dp,
                    ),
                ) {
                    ExtendedFloatingActionButton(
                        text = "Extended",
                        onClick = {},
                        overrides = ExtendedFloatingActionButtonOverrides(
                            contentColor = 0xFF99AABB.toInt(),
                        ),
                    )
                }
            }
        }.single()

        val regular = tree.children[0]
        assertEquals(
            0xFF112233.toInt(),
            regular.modifier.elements.filterIsInstance<BackgroundColorModifierElement>().single().color,
        )
        assertEquals(
            regularShape,
            regular.modifier.elements.filterIsInstance<ShapeModifierElement>().single().shape,
        )
        assertEquals(0xFF778899.toInt(), regular.collectText().single().textColor)

        val extended = tree.children[1]
        assertEquals(
            0xFF223344.toInt(),
            extended.modifier.elements.filterIsInstance<BackgroundColorModifierElement>().single().color,
        )
        assertEquals(
            UiDimension.Exact(61.dp),
            extended.modifier.elements.filterIsInstance<HeightModifierElement>().single().height,
        )
        assertEquals(extendedShape, extended.modifier.elements.filterIsInstance<ShapeModifierElement>().single().shape)
        assertEquals(0xFF99AABB.toInt(), extended.collectText().single().textColor)
    }

    @Test
    fun `app bars own slot colors while child icon button overrides remain final`() {
        val tree = buildVNodeTree {
            Column {
                ProvideTopAppBarOverrides(
                    TopAppBarOverrides(
                        containerColor = 0xFF010203.toInt(),
                        titleColor = 0xFF111213.toInt(),
                        navigationIconColor = 0xFF212223.toInt(),
                        actionIconColor = 0xFF313233.toInt(),
                    ),
                ) {
                    TopAppBar(
                        title = "Title",
                        navigationIcon = {
                            IconButton(
                                icon = ImageSource.Resource(1),
                                contentDescription = "Back",
                            )
                        },
                        actions = {
                            IconButton(
                                icon = ImageSource.Resource(2),
                                contentDescription = "Own",
                                overrides = IconButtonOverrides(contentColor = 0xFF414243.toInt()),
                            )
                            IconButton(
                                icon = ImageSource.Resource(3),
                                contentDescription = "Inherited",
                            )
                        },
                    )
                }
                ProvideBottomAppBarOverrides(
                    BottomAppBarOverrides(
                        containerColor = 0xFF515253.toInt(),
                        contentColor = 0xFF616263.toInt(),
                    ),
                ) {
                    BottomAppBar {
                        IconButton(
                            icon = ImageSource.Resource(4),
                            contentDescription = "Bottom",
                        )
                    }
                }
            }
        }.single()

        val topBar = tree.children[0]
        assertEquals(
            0xFF010203.toInt(),
            topBar.modifier.elements.filterIsInstance<BackgroundColorModifierElement>().single().color,
        )
        assertEquals(0xFF111213.toInt(), topBar.collectText().single().textColor)
        val topIcons = topBar.collect(NodeType.IconButton).map { it.spec as IconButtonNodeProps }
        assertEquals(0xFF212223.toInt(), topIcons[0].tint)
        assertEquals(0xFF414243.toInt(), topIcons[1].tint)
        assertEquals(0xFF313233.toInt(), topIcons[2].tint)

        val bottomBar = tree.children[1]
        assertEquals(
            0xFF515253.toInt(),
            bottomBar.modifier.elements.filterIsInstance<BackgroundColorModifierElement>().single().color,
        )
        assertEquals(
            0xFF616263.toInt(),
            (bottomBar.collect(NodeType.IconButton).single().spec as IconButtonNodeProps).tint,
        )
    }

    @Test
    fun `badge scope supplies geometry while instance supplies content role`() {
        val shape = UiShape.rounded(5.dp)
        val tree = buildVNodeTree {
            ProvideBadgeOverrides(
                BadgeOverrides(
                    containerColor = 0xFF710203.toInt(),
                    shape = shape,
                    pillHeight = 29.dp,
                    pillMinWidth = 37.dp,
                    pillHorizontalPadding = 9.dp,
                ),
            ) {
                Badge(
                    count = 120,
                    overrides = BadgeOverrides(contentColor = 0xFF81A2B3.toInt()),
                )
            }
        }.single()

        assertEquals(
            0xFF710203.toInt(),
            tree.modifier.elements.filterIsInstance<BackgroundColorModifierElement>().single().color,
        )
        assertEquals(shape, tree.modifier.elements.filterIsInstance<ShapeModifierElement>().single().shape)
        assertEquals(
            UiDimension.Exact(29.dp),
            tree.modifier.elements.filterIsInstance<HeightModifierElement>().single().height,
        )
        assertEquals(37.dp, tree.modifier.elements.filterIsInstance<MinWidthModifierElement>().single().minWidth)
        assertEquals(
            PaddingModifierElement(9.dp, 0.dp, 9.dp, 0.dp),
            tree.modifier.elements.filterIsInstance<PaddingModifierElement>().single(),
        )
        assertEquals("99+", tree.collectText().single().document.text)
        assertEquals(0xFF81A2B3.toInt(), tree.collectText().single().textColor)
    }

    @Test
    fun `alert dialog resolves appearance before captured overlay content is built`() {
        val store = OverlayRequestStore()
        val shape = UiShape.rounded(13.dp)
        OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                ProvideAlertDialogOverrides(
                    AlertDialogOverrides(
                        containerColor = 0xFF102030.toInt(),
                        textColor = 0xFF405060.toInt(),
                        shape = shape,
                        contentPadding = 19.dp,
                        minWidth = 301.dp,
                    ),
                ) {
                    AlertDialog(
                        visible = true,
                        title = "Title",
                        text = "Body",
                        confirmButtonText = "Confirm",
                        onConfirm = {},
                        overrides = AlertDialogOverrides(titleColor = 0xFF708090.toInt()),
                    )
                }
            }
        }

        val content = store.currentRequests().single().contentToken as DialogOverlayContent
        val surface = content.surface.buildNodes().single()
        assertEquals(
            0xFF102030.toInt(),
            surface.modifier.elements.filterIsInstance<BackgroundColorModifierElement>().single().color,
        )
        assertEquals(shape, surface.modifier.elements.filterIsInstance<ShapeModifierElement>().single().shape)
        assertEquals(301.dp, surface.modifier.elements.filterIsInstance<MinWidthModifierElement>().single().minWidth)
        assertEquals(
            PaddingModifierElement(19.dp, 19.dp, 19.dp, 19.dp),
            surface.modifier.elements.filterIsInstance<PaddingModifierElement>().single(),
        )
        val textByValue = surface.collectText().associateBy { it.document.text }
        assertEquals(0xFF708090.toInt(), textByValue.getValue("Title").textColor)
        assertEquals(0xFF405060.toInt(), textByValue.getValue("Body").textColor)
    }

    @Test
    fun `modal bottom sheet carries one complete resolved appearance and content color`() {
        val store = OverlayRequestStore()
        val shape = UiShape.rounded(23.dp)
        OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                ProvideModalBottomSheetOverrides(
                    ModalBottomSheetOverrides(
                        containerColor = 0xFF123456.toInt(),
                        contentColor = 0xFFABCDEF.toInt(),
                        shape = shape,
                        navigationBarColor = ModalBottomSheetNavigationBarColor.PlatformDefault,
                    ),
                ) {
                    ModalBottomSheet(
                        visible = true,
                        requestKey = "appearance",
                        overrides = ModalBottomSheetOverrides(scrimOpacity = 0.48f),
                    ) {
                        Text("Sheet")
                    }
                }
            }
        }

        val request = store.currentRequests().single()
        val spec = request.payload as ModalBottomSheetOverlaySpec
        assertEquals(0xFF123456.toInt(), spec.appearance.containerColor)
        assertEquals(0xFFABCDEF.toInt(), spec.appearance.contentColor)
        assertEquals(shape, spec.appearance.shape)
        assertEquals(0.48f, spec.appearance.scrimOpacity)
        assertEquals(ModalBottomSheetNavigationBarColor.PlatformDefault, spec.appearance.navigationBarColor)
        val content = request.contentToken as ModalBottomSheetOverlayContent
        assertEquals(0xFFABCDEF.toInt(), content.surface.buildNodes().single().collectText().single().textColor)

        val exactNavigation = spec.appearance.copy(
            navigationBarColor = ModalBottomSheetNavigationBarColor.Exact(0xFF000000.toInt()),
        )
        assertNotEquals(
            ModalBottomSheetOverlaySpec(appearance = spec.appearance),
            ModalBottomSheetOverlaySpec(appearance = exactNavigation),
        )
    }

    private fun VNode.collect(type: NodeType): List<VNode> = buildList {
        if (this@collect.type == type) add(this@collect)
        children.forEach { addAll(it.collect(type)) }
    }

    private fun VNode.collectText(): List<TextNodeProps> =
        collect(NodeType.Text).map { it.spec as TextNodeProps }
}
