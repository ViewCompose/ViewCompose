package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Render Session Failure 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Render Session Failure behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import android.content.Context
import android.widget.FrameLayout
import com.viewcompose.runtime.State
import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.environment.UiEnvironmentValues
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        engine.detachBlock = { _, _ -> null }
        engine.patchBlock = { _, _ -> CoreObservedPropertyFrame() }
        engine.disposeFailures = emptyList()
        NoOpRenderSessionDiagnostics.sourceTooling = null
        NoOpRenderSessionDiagnostics.errors.clear()
    }

    @Test
    fun `observed text patches exact targets without recomposing declaration`() {
        val value = mutableStateOf(0)
        var declarations = 0
        var structuralFrames = 0
        val patchBatches = mutableListOf<List<CoreObservedPropertyPatch>>()
        engine.renderBlock = { _, nodes ->
            structuralFrames += 1
            observedFrame(nodes)
        }
        engine.patchBlock = { _, patches ->
            patchBatches += patches
            observedPropertyFrame()
        }
        session = createSession(failures = mutableListOf()) {
            declarations += 1
            Text(observedValue { value.value.toString() })
        }

        session.render()
        value.value = 1
        checkNotNull(latestRuntime).drainPending()

        assertEquals(1, declarations)
        assertEquals(1, structuralFrames)
        assertEquals(1, patchBatches.size)
        assertEquals("1", patchBatches.single().single().next.requireText())
        assertSame(
            patchBatches.single().single().next,
            patchBatches.single().single().target.node,
        )

        session.render()

        assertEquals(2, declarations)
        assertEquals(2, structuralFrames)
    }

    @Test
    fun `observed properties coalesce and structural invalidation wins the frame`() {
        val first = mutableStateOf(0)
        val second = mutableStateOf(0)
        val structural = mutableStateOf(false)
        var declarations = 0
        var structuralFrames = 0
        val patchSizes = mutableListOf<Int>()
        engine.renderBlock = { _, nodes ->
            structuralFrames += 1
            observedFrame(nodes)
        }
        engine.patchBlock = { _, patches ->
            patchSizes += patches.size
            observedPropertyFrame()
        }
        session = createSession(failures = mutableListOf()) {
            declarations += 1
            if (structural.value) {
                Text("structure")
            }
            Text(observedValue { first.value.toString() }, key = "first")
            Text(observedValue { second.value.toString() }, key = "second")
        }
        session.render()
        val runtime = checkNotNull(latestRuntime)
        val requestsBeforeBatch = runtime.requestCount

        Snapshot.withMutableSnapshot {
            first.value = 1
            second.value = 2
        }
        assertEquals(requestsBeforeBatch + 1, runtime.requestCount)
        runtime.drainPending()

        assertEquals(listOf(2), patchSizes)
        assertEquals(1, declarations)

        Snapshot.withMutableSnapshot {
            first.value = 3
            structural.value = true
        }
        checkNotNull(latestRuntime).drainPending()

        assertEquals(listOf(2), patchSizes)
        assertEquals(2, declarations)
        assertEquals(2, structuralFrames)
    }

    @Test
    fun `state shared by observed property and boundary still commits structural frame`() {
        val revision = mutableStateOf(0)
        val renderedValues = mutableListOf<List<String>>()
        val patchSizes = mutableListOf<Int>()
        var boundaryDeclarations = 0
        engine.renderBlock = { _, nodes ->
            renderedValues += nodes.map { node -> node.requireText() }
            observedFrame(nodes)
        }
        engine.patchBlock = { _, patches ->
            patchSizes += patches.size
            observedPropertyFrame()
        }
        session = createSession(failures = mutableListOf()) {
            Text(observedValue { "state=${revision.value}" })
            RecomposeBoundary(key = "structure") {
                boundaryDeclarations += 1
                Text("structure=${revision.value}")
            }
        }

        session.render()
        revision.value = 1
        checkNotNull(latestRuntime).drainPending()

        assertEquals(2, boundaryDeclarations)
        assertEquals(
            listOf(
                listOf("state=0", "structure=0"),
                listOf("state=1", "structure=1"),
            ),
            renderedValues,
        )
        assertTrue(patchSizes.isEmpty())
    }

    @Test
    fun `failed observed property batch keeps previous dependencies and target`() {
        val value = mutableStateOf(0)
        val failures = mutableListOf<RenderFailure>()
        var failNextPatch = true
        val committedValues = mutableListOf<String>()
        engine.renderBlock = { _, nodes -> observedFrame(nodes) }
        engine.patchBlock = { _, patches ->
            if (failNextPatch) {
                failNextPatch = false
                error("property patch failed")
            }
            committedValues += patches.single().next.requireText()
            observedPropertyFrame()
        }
        session = createSession(failures = failures) {
            Text(observedValue { value.value.toString() })
        }
        session.render()

        value.value = 1
        checkNotNull(latestRuntime).drainPending()

        assertEquals(RenderFrameStatus.RolledBack, session.lastFrameReport?.status)
        assertEquals(RenderFailurePhase.ObservedPropertyRender, failures.single().phase)

        value.value = 2
        checkNotNull(latestRuntime).drainPending()

        assertEquals(listOf("2"), committedValues)
        assertEquals(RenderFrameStatus.Committed, session.lastFrameReport?.status)
    }

    @Test
    fun `equal observed value commits replacement dependencies without native patch`() {
        val selectSecond = mutableStateOf(false)
        val first = mutableStateOf("same")
        val second = mutableStateOf("same")
        val patchedValues = mutableListOf<String>()
        engine.renderBlock = { _, nodes -> observedFrame(nodes) }
        engine.patchBlock = { _, patches ->
            patchedValues += patches.single().next.requireText()
            observedPropertyFrame()
        }
        session = createSession(failures = mutableListOf()) {
            Text(observedValue {
                if (selectSecond.value) second.value else first.value
            })
        }
        session.render()

        selectSecond.value = true
        checkNotNull(latestRuntime).drainPending()

        assertTrue(patchedValues.isEmpty())
        first.value = "ignored"
        assertFalse(checkNotNull(latestRuntime).hasPending())
        second.value = "next"
        assertTrue(checkNotNull(latestRuntime).hasPending())
        checkNotNull(latestRuntime).drainPending()
        assertEquals(listOf("next"), patchedValues)
    }

    @Test
    fun `removing observed node disposes its state dependencies`() {
        val visible = mutableStateOf(true)
        val value = mutableStateOf("before")
        engine.renderBlock = { _, nodes -> observedFrame(nodes) }
        engine.patchBlock = { _, _ -> observedPropertyFrame() }
        session = createSession(failures = mutableListOf()) {
            if (visible.value) {
                Text(observedValue { value.value })
            }
        }
        session.render()

        visible.value = false
        checkNotNull(latestRuntime).drainPending()
        value.value = "after"

        assertFalse(checkNotNull(latestRuntime).hasPending())
    }

    @Test
    fun `prepared observed property invalidation rebuilds before activation`() {
        val value = mutableStateOf("first")
        val renderedValues = mutableListOf<String>()
        engine.renderBlock = { _, nodes ->
            renderedValues += nodes.single().requireText()
            observedFrame(nodes)
        }
        engine.patchBlock = { _, _ -> error("prepared activation must use a structural frame") }
        session = createSession(failures = mutableListOf()) {
            Text(observedValue { value.value })
        }

        session.prepareForActivation()
        value.value = "second"
        session.activatePrepared()

        assertEquals(listOf("first", "second"), renderedValues)
        assertEquals(RenderFrameStatus.Committed, session.lastFrameReport?.status)
    }

    @Test
    fun `environment change replaces observed target before later property patch`() {
        val value = mutableStateOf("first")
        var environment = UiEnvironmentValues(resourceRevision = 1L)
        var declarations = 0
        var structuralFrames = 0
        val patches = mutableListOf<CoreObservedPropertyPatch>()
        engine.renderBlock = { _, nodes ->
            structuralFrames += 1
            observedFrame(nodes)
        }
        engine.patchBlock = { _, batch ->
            patches += batch.single()
            observedPropertyFrame()
        }
        session = createSession(failures = mutableListOf()) {
            declarations += 1
            UiEnvironment(environment) {
                Text(observedValue { value.value })
            }
        }

        session.render()
        environment = UiEnvironmentValues(resourceRevision = 2L)
        session.render()
        value.value = "second"
        checkNotNull(latestRuntime).drainPending()

        assertEquals(2, declarations)
        assertEquals(2, structuralFrames)
        assertEquals(2L, patches.single().previous.environment.resourceRevision)
        assertEquals(2L, patches.single().next.environment.resourceRevision)
        assertEquals("second", patches.single().next.requireText())
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
    fun `item saveable ownership closes before effects dispose and native reset`() {
        val events = mutableListOf<String>()
        val holder = SaveableStateHolder.create(createSaveableStateRegistry())
        holder.retainKeys(setOf("item"))
        engine.detachBlock = { _, _ ->
            events += "reset"
            object : CoreReusableRenderTree {}
        }
        val itemSession = WidgetLazyListItemSession(
            container = childContainer(),
            localSnapshot = LocalContext.snapshot(),
            saveableStateHolder = holder,
            saveableStateKey = "item",
            content = {
                val value = rememberSaveable(
                    saver = Saver<String, String>(
                        save = { saved -> events += "save"; saved },
                        restore = { restored -> restored },
                    ),
                ) { "value" }
                check(value == "value")
                DisposableEffect(Unit) {
                    onDispose { events += "effect-dispose" }
                }
            },
        )

        itemSession.render()
        val presentation = itemSession.disposeForReuse()

        assertEquals(listOf("save", "effect-dispose", "reset"), events)
        presentation?.release()
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
    fun `lazy item session reports rollback without accepting the semantic revision`() {
        var failComposition = true
        val itemSession = WidgetLazyListItemSession(
            container = childContainer(),
            localSnapshot = LocalContext.snapshot(),
            saveableStateHolder = null,
            saveableStateKey = "item",
            content = {
                if (failComposition) error("composition failed")
            },
        )

        assertEquals(false, itemSession.render())
        failComposition = false
        assertEquals(true, itemSession.render())

        itemSession.dispose()
    }

    @Test
    fun `delayed item session preserves an explicitly provided nullable local`() {
        val nullable = uiLocalOf<String?>(debugName = "DelayedNullableLocal") { "default" }
        lateinit var captured: LocalSnapshot
        var observed: String? = "unset"
        LocalContext.provide(nullable.holder, null) {
            captured = LocalContext.snapshot()
        }
        val itemSession = WidgetLazyListItemSession(
            container = childContainer(),
            localSnapshot = captured,
            saveableStateHolder = null,
            saveableStateKey = "nullable-item",
            content = {
                observed = UiLocals.current(nullable)
            },
        )

        assertTrue(itemSession.render())
        assertNull(observed)
        itemSession.dispose()
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
    fun `disposed session rejects explicit render and activation changes`() {
        session = createSession(failures = mutableListOf())
        session.render()
        session.dispose()

        val renderFailure = runCatching(session::render).exceptionOrNull()
        val activationFailure = runCatching {
            session.setRenderingActive(false)
        }.exceptionOrNull()

        assertTrue(renderFailure is IllegalStateException)
        assertTrue(renderFailure?.message.orEmpty().contains("disposed"))
        assertTrue(activationFailure is IllegalStateException)
        assertTrue(activationFailure?.message.orEmpty().contains("disposed"))
        session.dispose()
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
                        item(
                            key = itemKey,
                            contentRevision = StaticContentRevision,
                        ) {
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
            NoOpRenderSessionDiagnostics.errors.toString(),
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
    fun `snapshot fast hit keeps item content state independently observable`() {
        val snapshot = listOf("item").toLazyItemsSnapshot()
        val itemState = mutableStateOf("first")
        var selectorCalls = 0
        var itemDeclarations = 0
        var latestItems = emptyList<com.viewcompose.ui.node.LazyListItem>()
        val renderedItemTexts = mutableListOf<String>()
        engine.renderBlock = { _, nodes ->
            val node = nodes.singleOrNull()
            when (node?.spec) {
                is LazyColumnNodeProps -> {
                    latestItems = (node.spec as LazyColumnNodeProps).items
                }

                is com.viewcompose.ui.node.spec.TextNodeProps -> {
                    renderedItemTexts += node.requireText()
                }
            }
            CoreRenderFrame(mountedNodes = nodes)
        }
        session = createSession(failures = mutableListOf()) {
            LazyColumn(
                items = snapshot,
                key = { item ->
                    selectorCalls += 1
                    item
                },
            ) { item ->
                itemDeclarations += 1
                Text("$item:${itemState.value}")
            }
        }

        session.render()
        val firstItems = latestItems
        session.render()
        assertSame(firstItems, latestItems)
        val childSession = latestItems.single().sessionFactory.create(childContainer())
        childSession.render()
        val childRuntime = checkNotNull(latestRuntime)
        itemState.value = "second"
        childRuntime.drainPending()

        assertEquals(1, selectorCalls)
        assertEquals(2, itemDeclarations)
        assertEquals(listOf("item:first", "item:second"), renderedItemTexts)
        childSession.dispose()
    }

    @Test
    fun `pager child sessions and eager keyed tabs isolate saveable state`() {
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
                    Page(key = "first", contentRevision = StaticContentRevision) {
                        saveableChildContent("horizontal-0")
                    }
                    Page(key = "second", contentRevision = StaticContentRevision) {
                        saveableChildContent("horizontal-1")
                    }
                }
                VerticalPager(currentPage = 0, onPageChanged = {}) {
                    Page(key = "first", contentRevision = StaticContentRevision) {
                        saveableChildContent("vertical-0")
                    }
                    Page(key = "second", contentRevision = StaticContentRevision) {
                        saveableChildContent("vertical-1")
                    }
                }
                TabRow(selectedIndex = 0, onTabSelected = {}) {
                    Tab(key = "first", contentRevision = StaticContentRevision) {
                        saveableChildContent("tab-0")
                    }
                    Tab(key = "second", contentRevision = StaticContentRevision) {
                        saveableChildContent("tab-1")
                    }
                }
            }
        }

        session.render()
        val childItems = buildList {
            addAll((rootNodes[0].spec as HorizontalPagerNodeProps).pages)
            addAll((rootNodes[1].spec as VerticalPagerNodeProps).pages)
        }
        val childSessions = childItems.map { item ->
            item.sessionFactory.create(childContainer())
        }
        childSessions.forEach { it.render() }

        assertTrue(
            NoOpRenderSessionDiagnostics.errors.toString(),
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

    private fun observedFrame(nodes: List<VNode>): CoreRenderFrame {
        val targets = LinkedHashMap<Long, CoreObservedPropertyTarget>()
        fun visit(node: VNode) {
            node.observedPropertyId?.let { id ->
                targets[id] = CoreObservedPropertyTarget(handle = node, node = node)
            }
            node.children.forEach(::visit)
        }
        nodes.forEach(::visit)
        return CoreRenderFrame(
            mountedNodes = nodes,
            observedPropertyTargets = targets,
        )
    }

    private fun observedPropertyFrame(): CoreObservedPropertyFrame = CoreObservedPropertyFrame()

    private fun VNode.requireText(): String {
        return (spec as com.viewcompose.ui.node.spec.TextNodeProps).document.text
    }

    private companion object {
        lateinit var engine: FakeRenderEngine
        var latestRuntime: ImmediateRuntime? = null

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
                    ).also { runtime -> latestRuntime = runtime }
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
        var patchBlock: (List<Any>, List<CoreObservedPropertyPatch>) -> CoreObservedPropertyFrame =
            { _, _ -> CoreObservedPropertyFrame() }
        var detachBlock: (
            RenderContainerHandle,
            List<Any>,
        ) -> CoreReusableRenderTree? = { _, _ -> null }

        override fun renderInto(
            container: RenderContainerHandle,
            previousMountedNodes: List<Any>,
            nodes: List<VNode>,
            collectDiagnostics: Boolean,
        ): CoreRenderFrame {
            return renderBlock(previousMountedNodes, nodes)
        }

        override fun patchObservedProperties(
            container: RenderContainerHandle,
            mountedNodes: List<Any>,
            patches: List<CoreObservedPropertyPatch>,
            collectDiagnostics: Boolean,
        ): CoreObservedPropertyFrame = patchBlock(mountedNodes, patches)

        override fun disposeMounted(
            container: RenderContainerHandle,
            mountedNodes: List<Any>,
        ): List<CoreRenderCommitFailure> = disposeFailures

        override fun detachMountedForReuse(
            container: RenderContainerHandle,
            mountedNodes: List<Any>,
        ): CoreReusableRenderTree? = detachBlock(container, mountedNodes)
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
        private var pending = false
        var requestCount: Int = 0
            private set

        override fun requestRender() {
            if (!disposed) {
                requestCount += 1
                pending = true
            }
        }

        override fun render() {
            if (!disposed) onRenderNow()
        }

        override fun dispose() {
            if (disposed) return
            disposed = true
            onDisposeNow()
        }

        fun drainPending() {
            if (disposed || !pending) return
            pending = false
            onRenderNow()
        }

        fun hasPending(): Boolean = pending
    }
}
