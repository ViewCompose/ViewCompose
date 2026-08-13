package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Render Session Failure 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Render Session Failure behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import android.content.Context
import android.widget.FrameLayout
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.focus.FocusDirection
import com.viewcompose.ui.focus.FocusManager
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import com.viewcompose.ui.tooling.UiSourceCallSite
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
        NoOpRenderSessionDiagnostics.sourceTooling = null
        NoOpRenderSessionDiagnostics.errors.clear()
    }

    @After
    fun tearDown() {
        if (::session.isInitialized) {
            session.dispose()
        }
    }

    @Test
    fun `prepared frame defers all commit work until activation`() {
        val failures = mutableListOf<RenderFailure>()
        val events = mutableListOf<String>()
        var nativeRenderCount = 0
        val overlayHost = object : OverlayHost {
            override fun commit(
                sessionId: OverlaySessionId,
                requests: List<OverlayRequest>,
            ) {
                events += "overlay"
            }

            override fun clear(sessionId: OverlaySessionId) = Unit
        }
        engine.renderBlock = { previous, _ ->
            nativeRenderCount += 1
            CoreRenderFrame(
                mountedNodes = previous,
                commitEffects = listOf(
                    CoreRenderCommitEffect(
                        operation = RenderFailureOperation.AndroidViewCommit,
                        nodeKey = "native",
                        commit = { events += "native" },
                    ),
                ),
            )
        }
        session = createSession(
            failures = failures,
            overlayHost = overlayHost,
            content = {
                DisposableEffect("prepared") {
                    events += "remember"
                    onDispose { events += "dispose" }
                }
                SideEffect { events += "side" }
            },
        )

        session.prepareForActivation()

        assertEquals(1, nativeRenderCount)
        assertTrue(events.isEmpty())
        assertEquals(null, session.lastFrameReport)

        session.activatePrepared()

        assertEquals(1, nativeRenderCount)
        assertEquals(listOf("remember", "side", "native", "overlay"), events)
        assertEquals(RenderFrameStatus.Committed, session.lastFrameReport?.status)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `prepared frame defers saveable provider registration until activation`() {
        val failures = mutableListOf<RenderFailure>()
        val registry = createSaveableStateRegistry()
        session = createSession(failures = failures) {
            ProvideSaveableStateRegistry(registry) {
                val preparedValue: String = rememberSaveable(
                    key = "prepared-field",
                    saver = Saver(
                        save = { value -> value },
                        restore = { value -> value },
                    ),
                ) {
                    "prepared-value"
                }
                check(preparedValue.isNotEmpty())
            }
        }

        session.prepareForActivation()
        val competingEntry = registry.registerProvider("user:prepared-field") {
            "competing-value"
        }
        competingEntry.unregister()

        session.activatePrepared()

        assertEquals("prepared-value", registry.performSave()["user:prepared-field"])
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `state invalidation before activation discards stale prepared effects`() {
        val failures = mutableListOf<RenderFailure>()
        val events = mutableListOf<String>()
        val value = mutableStateOf("first")
        var nativeRenderCount = 0
        engine.renderBlock = { previous, _ ->
            nativeRenderCount += 1
            CoreRenderFrame(
                mountedNodes = previous,
                commitEffects = listOf(
                    CoreRenderCommitEffect(
                        operation = RenderFailureOperation.AndroidViewCommit,
                        nodeKey = "native",
                        commit = { events += "native:${value.value}" },
                    ),
                ),
            )
        }
        session = createSession(
            failures = failures,
            content = {
                val current = value.value
                DisposableEffect(current) {
                    events += "remember:$current"
                    onDispose { events += "dispose:$current" }
                }
                SideEffect { events += "side:$current" }
            },
        )

        session.prepareForActivation()
        value.value = "second"
        session.activatePrepared()

        assertEquals(2, nativeRenderCount)
        assertEquals(
            listOf("remember:second", "side:second", "native:second"),
            events,
        )
        assertTrue(events.none { event -> "first" in event })
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `disposing prepared frame never activates candidate work`() {
        val failures = mutableListOf<RenderFailure>()
        val events = mutableListOf<String>()
        engine.renderBlock = { previous, _ ->
            CoreRenderFrame(
                mountedNodes = previous,
                commitEffects = listOf(
                    CoreRenderCommitEffect(
                        operation = RenderFailureOperation.AndroidViewCommit,
                        nodeKey = "native",
                        commit = { events += "native" },
                    ),
                ),
            )
        }
        session = createSession(
            failures = failures,
            content = {
                DisposableEffect("prepared") {
                    events += "remember"
                    onDispose { events += "dispose" }
                }
                SideEffect { events += "side" }
            },
        )

        session.prepareForActivation()
        session.dispose()

        assertTrue(events.isEmpty())
        assertEquals(null, session.lastFrameReport)
        assertTrue(failures.isEmpty())
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
    fun `view tree rollback does not publish remember updated state candidate`() {
        val failures = mutableListOf<RenderFailure>()
        var input = "committed"
        lateinit var holder: State<String>
        session = createSession(
            failures = failures,
            content = {
                holder = rememberUpdatedState(input)
            },
        )

        session.render()
        assertEquals("committed", holder.value)

        input = "aborted"
        engine.renderBlock = { _, _ ->
            error("native render failed")
        }
        session.render()

        assertEquals(RenderFrameStatus.RolledBack, session.lastFrameReport?.status)
        assertEquals("committed", holder.value)

        input = "published"
        engine.renderBlock = { previous, _ ->
            CoreRenderFrame(mountedNodes = previous)
        }
        session.render()

        assertEquals(RenderFrameStatus.Committed, session.lastFrameReport?.status)
        assertEquals("published", holder.value)
    }

    @Test
    fun `effect lifecycle precedes side effect and native commit`() {
        val failures = mutableListOf<RenderFailure>()
        val events = mutableListOf<String>()
        var effectKey = 1
        val overlayHost = object : OverlayHost {
            override fun commit(
                sessionId: OverlaySessionId,
                requests: List<OverlayRequest>,
            ) {
                events += "overlay"
            }

            override fun clear(sessionId: OverlaySessionId) = Unit
        }
        engine.renderBlock = { previous, _ ->
            CoreRenderFrame(
                mountedNodes = previous,
                commitEffects = listOf(
                    CoreRenderCommitEffect(
                        operation = RenderFailureOperation.AndroidViewCommit,
                        nodeKey = "native",
                        commit = { events += "native" },
                    ),
                ),
            )
        }
        session = createSession(
            failures = failures,
            overlayHost = overlayHost,
            content = {
                val current = effectKey
                DisposableEffect(current) {
                    events += "start:$current"
                    onDispose {
                        events += "dispose:$current"
                    }
                }
                SideEffect {
                    events += "side:$current"
                }
            },
        )

        session.render()
        effectKey = 2
        session.render()

        assertEquals(
            listOf(
                "start:1",
                "side:1",
                "native",
                "overlay",
                "dispose:1",
                "start:2",
                "side:2",
                "native",
                "overlay",
            ),
            events,
        )
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `source tooling follows a successfully rendered session lifecycle`() {
        val events = mutableListOf<String>()
        NoOpRenderSessionDiagnostics.sourceTooling = object : RenderSessionSourceTooling {
            override fun shouldCapture(container: RenderContainerHandle): Boolean = true

            override fun register(
                container: RenderContainerHandle,
                sourceCandidates: List<List<UiSourceCallSite>>,
            ): RenderSessionSourceRegistration {
                assertTrue(sourceCandidates.isNotEmpty())
                assertTrue(sourceCandidates.all(List<UiSourceCallSite>::isNotEmpty))
                events += "registered"
                return object : RenderSessionSourceRegistration {
                    override fun setRenderingActive(active: Boolean) {
                        events += "active=$active"
                    }

                    override fun dispose() {
                        events += "disposed"
                    }
                }
            }
        }
        session = createSession(failures = mutableListOf()) {
            Text("Source page")
        }

        session.render()
        session.setRenderingActive(false)
        session.dispose()

        assertEquals(listOf("registered", "active=false", "disposed"), events)
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
            container = object : PlatformRenderContainerHandle {
                override val container: Any = FrameLayout(context)
            },
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

    @Test
    fun `lazy child render sessions isolate equal saveable keys during commit`() {
        val registry = createSaveableStateRegistry()
        var rootNodes = emptyList<VNode>()
        engine.renderBlock = { previous, nodes ->
            if (nodes.singleOrNull()?.spec is LazyColumnNodeProps) {
                rootNodes = nodes
            }
            CoreRenderFrame(mountedNodes = previous)
        }
        NoOpRenderSessionDiagnostics.errors.clear()
        session = createSession(failures = mutableListOf()) {
            ProvideSaveableStateRegistry(registry) {
                LazyColumn {
                    listOf("first", "second").forEach { itemKey ->
                        item(key = itemKey) {
                            rememberTextFieldState(initialText = "$itemKey-auto")
                            val explicitState = rememberSaveable(key = "shared-field") {
                                mutableStateOf("$itemKey-explicit")
                            }
                            Text(explicitState.value)
                        }
                    }
                }
            }
        }

        session.render()
        val items = (rootNodes.single().spec as LazyColumnNodeProps).items
        val childSessions = items.map { item ->
            item.sessionFactory.create(
                object : PlatformRenderContainerHandle {
                    override val container: Any = FrameLayout(context)
                },
            )
        }
        childSessions.forEach { it.render() }

        assertTrue(
            NoOpRenderSessionDiagnostics.errors.none { (_, cause) ->
                cause?.message.orEmpty().contains("already registered")
            },
        )
        val savedText = registry.performSave().toString()
        assertTrue(savedText, savedText.contains("first-auto"))
        assertTrue(savedText, savedText.contains("second-auto"))
        assertTrue(savedText, savedText.contains("first-explicit"))
        assertTrue(savedText, savedText.contains("second-explicit"))
        childSessions.forEach { it.dispose() }
    }

    @Test
    fun `pager and tab child sessions receive independent saveable registries`() {
        val registry = createSaveableStateRegistry()
        var rootNodes = emptyList<VNode>()
        engine.renderBlock = { previous, nodes ->
            if (nodes.size == 3) {
                rootNodes = nodes
            }
            CoreRenderFrame(mountedNodes = previous)
        }
        session = createSession(failures = mutableListOf()) {
            ProvideSaveableStateRegistry(registry) {
                HorizontalPager(currentPage = 0, onPageChanged = {}) {
                    Page { saveableChildContent("horizontal-0") }
                    Page { saveableChildContent("horizontal-1") }
                }
                VerticalPager(currentPage = 0, onPageChanged = {}) {
                    Page(key = "first") { saveableChildContent("vertical-0") }
                    Page(key = "second") { saveableChildContent("vertical-1") }
                }
                TabRow(selectedIndex = 0, onTabSelected = {}) {
                    Tab(key = "first") { saveableChildContent("tab-0") }
                    Tab(key = "second") { saveableChildContent("tab-1") }
                }
            }
        }

        session.render()
        val childItems = buildList {
            addAll((rootNodes[0].spec as HorizontalPagerNodeProps).pages)
            addAll((rootNodes[1].spec as VerticalPagerNodeProps).pages)
            addAll((rootNodes[2].spec as TabRowNodeProps).tabs.map { it.item })
        }
        val childSessions = childItems.map { item ->
            item.sessionFactory.create(childContainer())
        }
        childSessions.forEach { it.render() }

        assertTrue(
            NoOpRenderSessionDiagnostics.errors.none { (_, cause) ->
                cause?.message.orEmpty().contains("already registered")
            },
        )
        val savedText = registry.performSave().toString()
        listOf(
            "horizontal-0",
            "horizontal-1",
            "vertical-0",
            "vertical-1",
            "tab-0",
            "tab-1",
        ).forEach { value ->
            assertTrue(savedText, savedText.contains(value))
        }
        childSessions.forEach { it.dispose() }
    }

    @Test
    fun `overlay surface sessions isolate equal saveable keys`() {
        val registry = createSaveableStateRegistry()
        val capturedRequests = mutableListOf<OverlayRequest>()
        session = createSession(
            failures = mutableListOf(),
            overlayHost = object : OverlayHost {
                override fun commit(
                    sessionId: OverlaySessionId,
                    requests: List<OverlayRequest>,
                ) {
                    capturedRequests.clear()
                    capturedRequests.addAll(requests)
                }

                override fun clear(sessionId: OverlaySessionId) = Unit
            },
        ) {
            ProvideSaveableStateRegistry(registry) {
                Dialog(visible = true, requestKey = "first") {
                    saveableChildContent("dialog-first")
                }
                Dialog(visible = true, requestKey = "second") {
                    saveableChildContent("dialog-second")
                }
            }
        }

        session.render()
        val surfaceSessions = capturedRequests.toList().map { request ->
            val content = request.contentToken as DialogOverlayContent
            createOverlaySurfaceSession(
                container = childContainer(),
                content = content.surface,
            )
        }

        assertTrue(
            NoOpRenderSessionDiagnostics.errors.none { (_, cause) ->
                cause?.message.orEmpty().contains("already registered")
            },
        )
        val savedText = registry.performSave().toString()
        assertTrue(savedText, savedText.contains("dialog-first"))
        assertTrue(savedText, savedText.contains("dialog-second"))
        surfaceSessions.forEach(OverlaySurfaceSession::dispose)
    }

    private fun UiTreeBuilder.saveableChildContent(value: String) {
        rememberTextFieldState(initialText = "$value-auto")
        val explicitState = rememberSaveable(key = "shared-field") {
            mutableStateOf("$value-explicit")
        }
        Text(explicitState.value)
    }

    private fun childContainer(): PlatformRenderContainerHandle {
        return object : PlatformRenderContainerHandle {
            override val container: Any = FrameLayout(context)
        }
    }

    private fun createSession(
        failures: MutableList<RenderFailure>,
        overlayHost: OverlayHost = OverlayHostDefaults.noOp,
        content: UiTreeBuilder.() -> Unit = {},
    ): RenderSession {
        return RenderSession(
            container = object : PlatformRenderContainerHandle {
                override val container: Any = FrameLayout(context)
            },
            content = content,
            overlayHost = overlayHost,
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
                focusManagerFactory = { NoOpFocusManager },
                diagnostics = NoOpRenderSessionDiagnostics,
            )
        }
    }

    private class FakeRenderEngine : CoreRenderEngine {
        var renderBlock: (List<Any>, List<VNode>) -> CoreRenderFrame = { previous, _ ->
            CoreRenderFrame(mountedNodes = previous)
        }
        var disposeFailures: List<CoreRenderCommitFailure> = emptyList()

        override fun renderInto(
            container: RenderContainerHandle,
            previousMountedNodes: List<Any>,
            nodes: List<VNode>,
            collectDiagnostics: Boolean,
        ): CoreRenderFrame {
            return renderBlock(previousMountedNodes, nodes)
        }

        override fun disposeMounted(
            container: RenderContainerHandle,
            mountedNodes: List<Any>,
        ): List<CoreRenderCommitFailure> = disposeFailures
    }

    private object NoOpFocusManager : FocusManager {
        override fun clearFocus(force: Boolean) = Unit

        override fun moveFocus(direction: FocusDirection): Boolean = false
    }

    private object NoOpRenderSessionDiagnostics : RenderSessionPlatformDiagnostics {
        override var sourceTooling: RenderSessionSourceTooling? = null
        val errors = mutableListOf<Pair<String, Throwable?>>()

        override fun debug(tag: String, message: String) = Unit

        override fun warning(tag: String, message: String) = Unit

        override fun error(tag: String, message: String, cause: Throwable) {
            errors += message to cause
        }

        override fun <T> trace(name: String, block: () -> T): T = block()
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
