package com.viewcompose.widget.core

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RenderSessionFailureTest {
    private lateinit var session: RenderSession

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        engine.renderBlock = { previous, _ ->
            CoreRenderFrame(mountedNodes = previous)
        }
        engine.disposeFailures = emptyList()
    }

    @After
    fun tearDown() {
        if (::session.isInitialized) {
            session.dispose()
        }
    }

    @Test
    fun `composition failure reports rollback and a later frame can commit`() {
        val failures = mutableListOf<RenderFailure>()
        var failComposition = true
        session = createSession(
            failures = failures,
            content = {
                if (failComposition) {
                    error("composition failed")
                }
            },
        )

        session.render()

        val failedReport = session.lastFrameReport
        assertEquals(RenderFrameStatus.RolledBack, failedReport?.status)
        assertEquals(RenderFailurePhase.CompositionPrepare, failures.single().phase)
        assertEquals(RenderFailureRecovery.PreviousFrameRestored, failures.single().recovery)

        failComposition = false
        session.render()

        assertEquals(RenderFrameStatus.Committed, session.lastFrameReport?.status)
        assertTrue(session.lastFrameReport?.failures.orEmpty().isEmpty())
    }

    @Test
    fun `android view render failure carries operation and node key`() {
        val failures = mutableListOf<RenderFailure>()
        val cause = IllegalStateException("update failed")
        engine.renderBlock = { _, _ ->
            throw AndroidViewOperationException(
                operation = AndroidViewOperation.Update,
                nodeKey = "map",
                cause = cause,
            )
        }
        session = createSession(failures = failures)

        session.render()

        val failure = failures.single()
        assertEquals(RenderFailurePhase.ViewTreeRender, failure.phase)
        assertEquals(RenderFailureOperation.AndroidViewUpdate, failure.operation)
        assertEquals("map", failure.nodeKey)
        assertSame(cause, failure.cause.cause)
        assertEquals(RenderFrameStatus.RolledBack, session.lastFrameReport?.status)
    }

    @Test
    fun `committed frame reports side effect and native commit failures independently`() {
        val failures = mutableListOf<RenderFailure>()
        val events = mutableListOf<String>()
        engine.renderBlock = { previous, _ ->
            CoreRenderFrame(
                mountedNodes = previous,
                commitEffects = listOf(
                    CoreRenderCommitEffect(
                        operation = RenderFailureOperation.AndroidViewCommit,
                        nodeKey = "player",
                        commit = {
                            events += "native"
                            error("native commit failed")
                        },
                    ),
                ),
                commitFailures = listOf(
                    CoreRenderCommitFailure(
                        operation = RenderFailureOperation.AndroidViewRelease,
                        nodeKey = "old-player",
                        cause = IllegalStateException("release failed"),
                    ),
                ),
            )
        }
        session = createSession(
            failures = failures,
            content = {
                SideEffect {
                    events += "side-effect"
                    error("side effect failed")
                }
            },
        )

        session.render()

        assertEquals(listOf("side-effect", "native"), events)
        assertEquals(RenderFrameStatus.Committed, session.lastFrameReport?.status)
        assertEquals(
            listOf(
                RenderFailurePhase.ViewTreeCommit,
                RenderFailurePhase.CompositionSideEffect,
                RenderFailurePhase.NativeViewCommit,
            ),
            failures.map(RenderFailure::phase),
        )
        assertEquals("old-player", failures[0].nodeKey)
        assertEquals("player", failures[2].nodeKey)
        assertEquals(
            failures,
            session.lastFrameReport?.failures,
        )
    }

    @Test
    fun `dispose failures are observable and disposal continues`() {
        val failures = mutableListOf<RenderFailure>()
        engine.disposeFailures = listOf(
            CoreRenderCommitFailure(
                operation = RenderFailureOperation.AndroidViewRelease,
                nodeKey = "camera",
                cause = IllegalStateException("release failed"),
            ),
        )
        session = createSession(failures = failures)
        session.render()

        session.dispose()

        val failure = failures.single()
        assertEquals(RenderFailurePhase.SessionDispose, failure.phase)
        assertEquals(RenderFailureRecovery.SessionDisposed, failure.recovery)
        assertEquals(RenderFailureOperation.AndroidViewRelease, failure.operation)
        assertEquals("camera", failure.nodeKey)
    }

    @Test
    fun `render result includes recomposition reasons and named local values`() {
        val results = mutableListOf<RenderTreeResult>()
        val local = uiLocalOf(
            debugName = "BusinessToken",
            debugValueFormatter = { value: String -> value },
        ) { "default" }
        engine.renderBlock = { previous, _ ->
            CoreRenderFrame(
                mountedNodes = previous,
                renderResult = RenderTreeResult(),
            )
        }
        session = RenderSession(
            container = FrameLayout(context),
            content = {
                ProvideLocal(local, "scoped") {
                    RecomposeBoundary(key = "diagnostic") {}
                }
            },
            onRenderResult = results::add,
        )

        session.render()

        val composition = results.single().composition
        assertTrue(composition.recomposedScopeCount > 0)
        assertTrue(
            composition.scopes.any { scope ->
                scope.locals.any { localValue ->
                    localValue.name == "BusinessToken" && localValue.value == "scoped"
                }
            },
        )
    }

    private fun createSession(
        failures: MutableList<RenderFailure>,
        content: UiTreeBuilder.() -> Unit = {},
    ): RenderSession {
        return RenderSession(
            container = FrameLayout(context),
            content = content,
            onRenderFailure = failures::add,
        )
    }

    private companion object {
        lateinit var engine: FakeRenderEngine

        @JvmStatic
        @BeforeClass
        fun installPlatform() {
            engine = FakeRenderEngine()
            installRenderSessionPlatform(
                renderEngine = engine,
                coroutineContext = EmptyCoroutineContext,
                runtimeFactory = RenderSessionRuntimeFactory { onRenderNow, onDisposeNow ->
                    ImmediateRuntime(
                        onRenderNow = onRenderNow,
                        onDisposeNow = onDisposeNow,
                    )
                },
            )
        }
    }

    private class FakeRenderEngine : CoreRenderEngine {
        var renderBlock: (List<Any>, List<VNode>) -> CoreRenderFrame = { previous, _ ->
            CoreRenderFrame(mountedNodes = previous)
        }
        var disposeFailures: List<CoreRenderCommitFailure> = emptyList()

        override fun renderInto(
            container: ViewGroup,
            previousMountedNodes: List<Any>,
            nodes: List<VNode>,
            collectDiagnostics: Boolean,
        ): CoreRenderFrame {
            return renderBlock(previousMountedNodes, nodes)
        }

        override fun disposeMounted(
            container: ViewGroup,
            mountedNodes: List<Any>,
        ): List<CoreRenderCommitFailure> = disposeFailures
    }

    private class ImmediateRuntime(
        private val onRenderNow: () -> Unit,
        private val onDisposeNow: () -> Unit,
    ) : RenderSessionRuntime {
        private var disposed = false

        override fun requestRender() = Unit

        override fun render() {
            if (!disposed) onRenderNow()
        }

        override fun dispose() {
            if (disposed) return
            disposed = true
            onDisposeNow()
        }
    }
}
