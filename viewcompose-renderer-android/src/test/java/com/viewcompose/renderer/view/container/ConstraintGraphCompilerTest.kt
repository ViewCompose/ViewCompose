package com.viewcompose.renderer.view.container

import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintBarrierDirection
import com.viewcompose.ui.node.spec.ConstraintBarrierSpec
import com.viewcompose.ui.node.spec.ConstraintChainOrientation
import com.viewcompose.ui.node.spec.ConstraintChainSpec
import com.viewcompose.ui.node.spec.ConstraintCircleSpec
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintFlowSpec
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintGraphCompilerTest {
    @Test
    fun `valid graph resolves content flow and helper namespace`() {
        val result = compile(
            bindings = listOf(binding("card"), binding("marker")),
            decoupled = ConstraintSetSpec(
                constraints = mapOf(
                    "flow" to ConstraintItemSpec(
                        width = ConstraintDimension.MatchConstraints(),
                    ),
                    "marker" to ConstraintItemSpec(
                        start = linkTo("barrier", ConstraintAnchor.End),
                    ),
                ),
                helpers = ConstraintHelpersSpec(
                    barriers = listOf(
                        ConstraintBarrierSpec(
                            id = "barrier",
                            direction = ConstraintBarrierDirection.End,
                            referencedIds = listOf("card"),
                        ),
                    ),
                    flows = listOf(
                        ConstraintFlowSpec(
                            id = "flow",
                            referencedIds = listOf("card"),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result is ConstraintGraphCompilation.Accepted)
        val graph = (result as ConstraintGraphCompilation.Accepted).graph
        assertEquals(setOf("card", "marker", "flow"), graph.constrainableIds)
        assertEquals(NativeConstraintHelperKind.Barrier, graph.helperKinds["barrier"])
    }

    @Test
    fun `duplicate child id rejects the complete candidate`() {
        val rejection = compile(
            bindings = listOf(binding("same"), binding("same")),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.DuplicateId, rejection.reason)
        assertEquals("same", rejection.identity)
    }

    @Test
    fun `missing child id rejects even an unconstrained child`() {
        val rejection = compile(
            bindings = listOf(binding(null)),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.MissingReference, rejection.reason)
    }

    @Test
    fun `missing anchor target rejects without dropping only that link`() {
        val rejection = compile(
            bindings = listOf(binding("card")),
            decoupled = ConstraintSetSpec(
                constraints = mapOf(
                    "card" to ConstraintItemSpec(
                        start = linkTo("missing", ConstraintAnchor.End),
                    ),
                ),
            ),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.MissingReference, rejection.reason)
        assertEquals("card", rejection.identity)
    }

    @Test
    fun `baseline and vertical edge reject as competing positioning`() {
        val rejection = compile(
            bindings = listOf(binding("label"), binding("peer")),
            decoupled = ConstraintSetSpec(
                constraints = mapOf(
                    "label" to ConstraintItemSpec(
                        top = parentLink(ConstraintAnchor.Top),
                        baseline = linkTo("peer", ConstraintAnchor.Baseline),
                    ),
                ),
            ),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.InvalidAnchor, rejection.reason)
    }

    @Test
    fun `helper dependency cycle rejects before native mutation`() {
        val rejection = compile(
            bindings = listOf(binding("card")),
            decoupled = ConstraintSetSpec(
                helpers = ConstraintHelpersSpec(
                    barriers = listOf(
                        ConstraintBarrierSpec(
                            id = "first",
                            direction = ConstraintBarrierDirection.Start,
                            referencedIds = listOf("second"),
                        ),
                        ConstraintBarrierSpec(
                            id = "second",
                            direction = ConstraintBarrierDirection.End,
                            referencedIds = listOf("first"),
                        ),
                    ),
                ),
            ),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.InvalidHelper, rejection.reason)
    }

    @Test
    fun `negative helper geometry rejects before native mutation`() {
        val rejection = compile(
            bindings = listOf(binding("card")),
            decoupled = ConstraintSetSpec(
                helpers = ConstraintHelpersSpec(
                    flows = listOf(
                        ConstraintFlowSpec(
                            id = "flow",
                            referencedIds = listOf("card"),
                            paddingStart = (-1).dp,
                        ),
                    ),
                ),
            ),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.InvalidValue, rejection.reason)
        assertEquals("Flow 'flow'.paddingStart", rejection.identity)
    }

    @Test
    fun `inline item declaration replaces the decoupled declaration for the same child`() {
        val inline = ConstraintItemSpec(width = ConstraintDimension.Fixed(24.dp))
        val result = compile(
            bindings = listOf(binding("card", inline)),
            decoupled = ConstraintSetSpec(
                constraints = mapOf(
                    "card" to ConstraintItemSpec(width = ConstraintDimension.Fixed(12.dp)),
                ),
            ),
        ) as ConstraintGraphCompilation.Accepted

        assertEquals(inline, result.graph.constraints["card"])
    }

    @Test
    fun `inline helper declaration replaces the same decoupled helper kind and id`() {
        val inlineBarrier = ConstraintBarrierSpec(
            id = "edge",
            direction = ConstraintBarrierDirection.End,
            referencedIds = listOf("card"),
        )
        val result = compile(
            bindings = listOf(binding("card")),
            decoupled = ConstraintSetSpec(
                helpers = ConstraintHelpersSpec(
                    barriers = listOf(
                        inlineBarrier.copy(direction = ConstraintBarrierDirection.Start),
                    ),
                ),
            ),
            inlineHelpers = ConstraintHelpersSpec(barriers = listOf(inlineBarrier)),
        ) as ConstraintGraphCompilation.Accepted

        assertEquals(listOf(inlineBarrier), result.graph.helpers.barriers)
    }

    @Test
    fun `one helper id cannot change kind inside the same candidate`() {
        val rejection = compile(
            bindings = listOf(binding("card")),
            decoupled = ConstraintSetSpec(
                helpers = ConstraintHelpersSpec(
                    barriers = listOf(
                        ConstraintBarrierSpec(
                            id = "shared",
                            direction = ConstraintBarrierDirection.End,
                            referencedIds = listOf("card"),
                        ),
                    ),
                    flows = listOf(
                        ConstraintFlowSpec(
                            id = "shared",
                            referencedIds = listOf("card"),
                        ),
                    ),
                ),
            ),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.DuplicateId, rejection.reason)
        assertEquals("shared", rejection.identity)
    }

    @Test
    fun `chain owns its member anchors on the chain axis`() {
        val rejection = compile(
            bindings = listOf(binding("first"), binding("second")),
            decoupled = ConstraintSetSpec(
                constraints = mapOf(
                    "first" to ConstraintItemSpec(
                        start = parentLink(ConstraintAnchor.Start),
                    ),
                ),
                helpers = ConstraintHelpersSpec(
                    chains = listOf(
                        ConstraintChainSpec(
                            orientation = ConstraintChainOrientation.Horizontal,
                            referencedIds = listOf("first", "second"),
                        ),
                    ),
                ),
            ),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.InvalidAnchor, rejection.reason)
        assertEquals("first", rejection.identity)
    }

    @Test
    fun `circle and edge positioning reject as competing ownership`() {
        val rejection = compile(
            bindings = listOf(binding("orbit"), binding("center")),
            decoupled = ConstraintSetSpec(
                constraints = mapOf(
                    "orbit" to ConstraintItemSpec(
                        top = parentLink(ConstraintAnchor.Top),
                        circle = ConstraintCircleSpec(
                            targetId = "center",
                            radius = 20.dp,
                            angle = 45f,
                        ),
                    ),
                ),
            ),
        ).rejection()

        assertEquals(ConstraintGraphRejectionReason.InvalidAnchor, rejection.reason)
        assertEquals("orbit", rejection.identity)
    }

    private fun compile(
        bindings: List<ConstraintContentBinding>,
        decoupled: ConstraintSetSpec? = null,
        inlineHelpers: ConstraintHelpersSpec = ConstraintHelpersSpec(),
    ): ConstraintGraphCompilation = ConstraintGraphCompiler.compile(
        contentBindings = bindings,
        decoupled = decoupled,
        inlineHelpers = inlineHelpers,
    )

    private fun binding(
        id: String?,
        inlineSpec: ConstraintItemSpec? = null,
    ): ConstraintContentBinding = ConstraintContentBinding(
        referenceId = id,
        inlineSpec = inlineSpec,
        nativeIdentity = Any(),
    )

    private fun parentLink(anchor: ConstraintAnchor): ConstraintAnchorLink = ConstraintAnchorLink(
        target = ConstraintAnchorTarget.parent(anchor),
    )

    private fun linkTo(
        id: String,
        anchor: ConstraintAnchor,
    ): ConstraintAnchorLink = ConstraintAnchorLink(
        target = ConstraintAnchorTarget.ref(id, anchor),
    )

    private fun ConstraintGraphCompilation.rejection(): ConstraintGraphRejection {
        return (this as ConstraintGraphCompilation.Rejected).rejection
    }
}
