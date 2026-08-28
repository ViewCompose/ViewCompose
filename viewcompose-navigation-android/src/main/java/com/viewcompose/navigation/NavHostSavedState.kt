package com.viewcompose.navigation

import android.os.Bundle
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavGraph
import com.viewcompose.navigation.core.NavGraphEntry
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.ui.foundation.Saver
import com.viewcompose.ui.foundation.mapSaver
import java.util.UUID

/** Stable, saveable identity for one NavHost's retained ViewModel scope provider. */
internal data class NavHostOwnerScopeId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "NavHost owner scope identity must not be blank."
        }
    }

    companion object {
        fun random(): NavHostOwnerScopeId = NavHostOwnerScopeId(UUID.randomUUID().toString())
    }
}

/**
 * Minimal state carrier that lets NavHost survive host recreation.
 */
internal data class NavHostRestorableState(
    val ownerScopeId: NavHostOwnerScopeId = NavHostOwnerScopeId.random(),
    val stackState: NavStackSetSnapshot,
    val destinationState: Bundle?,
)

/**
 * Creates a saver for a single-start controller, falling back to the initial destination on failure.
 */
internal fun navHostControllerSaver(
    startDestination: NavRoute,
): Saver<NavHostController, Map<String, Any?>> {
    return navHostControllerSaver(
        stackConfiguration = NavStackConfiguration.single(startDestination),
    )
}

/**
 * Creates a saver for a graphless multi-stack controller.
 */
internal fun navHostControllerSaver(
    stackConfiguration: NavStackConfiguration,
): Saver<NavHostController, Map<String, Any?>> {
    return mapSaver(
        save = { controller ->
            encodeNavHostState(controller.stateForSave())
        },
        restore = { saved ->
            val restored = decodeNavHostState(saved)
            if (restored == null) {
                createNavHostController(stackConfiguration)
            } else {
                // Restored data comes from an external Bundle, so incompatible shape is discardable.
                runCatching {
                    NavHostController(
                        backStackController = NavBackStackController.restore(
                            state = restored.stackState,
                            configuration = stackConfiguration,
                        ),
                        restoredDestinationState = restored.destinationState,
                        ownerScopeId = restored.ownerScopeId,
                    )
                }.getOrElse {
                    createNavHostController(stackConfiguration)
                }
            }
        },
    )
}

/**
 * Creates a saver for a single-graph controller, keeping route validation tied to the graph.
 */
internal fun navHostControllerSaver(
    graph: NavGraph,
): Saver<NavHostController, Map<String, Any?>> {
    return navHostControllerSaver(
        stackConfiguration = NavStackConfiguration.single(graph.startDestination),
        graph = graph,
    )
}

/**
 * Creates a saver for a multi-stack controller backed by a shared graph.
 */
internal fun navHostControllerSaver(
    stackConfiguration: NavStackConfiguration,
    graph: NavGraph,
): Saver<NavHostController, Map<String, Any?>> {
    return mapSaver(
        save = { controller ->
            encodeNavHostState(controller.stateForSave())
        },
        restore = { saved ->
            val restored = decodeNavHostState(saved)
            if (restored == null) {
                createNavHostController(
                    stackConfiguration = stackConfiguration,
                    graph = graph,
                )
            } else {
                // Graph shape may change across versions; rebuilding beats keeping half-restored state.
                runCatching {
                    NavHostController(
                        backStackController = NavBackStackController.restore(
                            state = restored.stackState,
                            configuration = stackConfiguration,
                            graph = graph,
                        ),
                        restoredDestinationState = restored.destinationState,
                        ownerScopeId = restored.ownerScopeId,
                    )
                }.getOrElse {
                    createNavHostController(
                        stackConfiguration = stackConfiguration,
                        graph = graph,
                    )
                }
            }
        },
    )
}

/**
 * Encodes stack state and destination Bundle into a Map accepted by the saveable registry.
 */
internal fun encodeNavHostState(
    state: NavHostRestorableState,
): Map<String, Any?> {
    return linkedMapOf(
        KEY_FORMAT_VERSION to FORMAT_VERSION,
        KEY_OWNER_SCOPE_ID to state.ownerScopeId.value,
        KEY_ACTIVE_STACK_ID to state.stackState.activeStackId.value,
        KEY_SELECTION_HISTORY to state.stackState.selectionHistory.map(NavStackId::value),
        KEY_STACKS to state.stackState.stacks.map { (stackId, snapshot) ->
            linkedMapOf(
                KEY_STACK_ID to stackId.value,
                KEY_ENTRIES to snapshot.entries.map(::encodeEntry),
            )
        },
        KEY_DESTINATION_STATE to state.destinationState?.let(::Bundle),
    )
}

/**
 * Defensively decodes saved state; any version, type, or bound mismatch returns null.
 */
internal fun decodeNavHostState(
    saved: Map<String, Any?>,
): NavHostRestorableState? {
    return runCatching {
        decodeNavHostStateUnsafe(saved)
    }.getOrNull()
}

/**
 * Concrete decoder wrapped by [decodeNavHostState] so failure handling stays simple.
 */
private fun decodeNavHostStateUnsafe(
    saved: Map<String, Any?>,
): NavHostRestorableState? {
    val formatVersion = (saved[KEY_FORMAT_VERSION] as? Number)?.toInt()
        ?: return null
    val ownerScopeId = when (formatVersion) {
        LEGACY_FORMAT_VERSION -> NavHostOwnerScopeId.random()
        FORMAT_VERSION -> NavHostOwnerScopeId(
            saved[KEY_OWNER_SCOPE_ID] as? String ?: return null,
        )
        else -> return null
    }
    val activeStackId = NavStackId(
        saved[KEY_ACTIVE_STACK_ID] as? String ?: return null,
    )
    val encodedStacks = saved[KEY_STACKS] as? List<*> ?: return null
    if (encodedStacks.isEmpty() || encodedStacks.size > MAX_STACK_COUNT) {
        return null
    }
    // Bound restored size to avoid creating too many entry owners from malformed or incompatible data.
    var totalEntryCount = 0
    val stacks = linkedMapOf<NavStackId, NavBackStackSnapshot>()
    encodedStacks.forEach { encodedStack ->
        val stackMap = encodedStack as? Map<*, *> ?: return null
        val stackId = NavStackId(
            stackMap[KEY_STACK_ID] as? String ?: return null,
        )
        val encodedEntries = stackMap[KEY_ENTRIES] as? List<*> ?: return null
        if (encodedEntries.isEmpty()) {
            return null
        }
        totalEntryCount += encodedEntries.size
        if (totalEntryCount > MAX_ENTRY_COUNT) {
            return null
        }
        val entries = encodedEntries.map { encoded ->
            decodeEntry(encoded as? Map<*, *> ?: return null) ?: return null
        }
        if (stacks.put(stackId, NavBackStackSnapshot(entries)) != null) {
            return null
        }
    }
    val selectionHistory = (saved[KEY_SELECTION_HISTORY] as? List<*>)
        ?.map { encodedStackId ->
            NavStackId(encodedStackId as? String ?: return null)
        }
        ?: return null
    val encodedDestinationState = saved[KEY_DESTINATION_STATE]
    if (encodedDestinationState != null && encodedDestinationState !is Bundle) {
        return null
    }
    return NavHostRestorableState(
        ownerScopeId = ownerScopeId,
        stackState = NavStackSetSnapshot(
            activeStackId = activeStackId,
            stacks = stacks,
            selectionHistory = selectionHistory,
        ),
        destinationState = (encodedDestinationState as? Bundle)?.let(::Bundle),
    )
}

private fun encodeEntry(entry: NavEntry): Map<String, Any?> {
    return linkedMapOf(
        KEY_ENTRY_ID to entry.id.value,
        KEY_ROUTE_NAME to entry.route.name,
        KEY_GRAPH_ENTRIES to entry.graphEntries.map(::encodeGraphEntry),
        KEY_ROUTE_ARGUMENTS to entry.route.arguments.mapValues { (_, value) ->
            encodeValue(value)
        },
    )
}

private fun encodeGraphEntry(entry: NavGraphEntry): Map<String, Any?> {
    return linkedMapOf(
        KEY_ENTRY_ID to entry.id.value,
        KEY_ROUTE_NAME to entry.route.name,
        KEY_ROUTE_ARGUMENTS to entry.route.arguments.mapValues { (_, value) ->
            encodeValue(value)
        },
    )
}

private fun decodeEntry(encoded: Map<*, *>): NavEntry? {
    val entryId = encoded[KEY_ENTRY_ID] as? String ?: return null
    val routeName = encoded[KEY_ROUTE_NAME] as? String ?: return null
    val graphEntries = (encoded[KEY_GRAPH_ENTRIES] as? List<*>)
        ?.map { graphEntry ->
            decodeGraphEntry(graphEntry as? Map<*, *> ?: return null) ?: return null
        }
        ?: return null
    return NavEntry(
        id = NavEntryId(entryId),
        route = NavRoute(
            name = routeName,
            arguments = decodeArguments(encoded[KEY_ROUTE_ARGUMENTS]) ?: return null,
        ),
        graphEntries = graphEntries,
    )
}

private fun decodeGraphEntry(encoded: Map<*, *>): NavGraphEntry? {
    val entryId = encoded[KEY_ENTRY_ID] as? String ?: return null
    val routeName = encoded[KEY_ROUTE_NAME] as? String ?: return null
    return NavGraphEntry(
        id = NavEntryId(entryId),
        route = NavRoute(
            name = routeName,
            arguments = decodeArguments(encoded[KEY_ROUTE_ARGUMENTS]) ?: return null,
        ),
    )
}

private fun decodeArguments(encoded: Any?): Map<String, NavValue>? {
    val encodedArguments = encoded as? Map<*, *> ?: return null
    val arguments = linkedMapOf<String, NavValue>()
    encodedArguments.forEach { (argumentName, encodedValue) ->
        val name = argumentName as? String ?: return null
        val value = decodeValue(encodedValue as? List<*> ?: return null) ?: return null
        arguments[name] = value
    }
    return arguments
}

private fun encodeValue(value: NavValue): List<Any?> {
    return when (value) {
        NavValue.Null -> listOf(VALUE_NULL)
        is NavValue.Text -> listOf(VALUE_TEXT, value.value)
        is NavValue.IntValue -> listOf(VALUE_INT, value.value)
        is NavValue.LongValue -> listOf(VALUE_LONG, value.value)
        is NavValue.BooleanValue -> listOf(VALUE_BOOLEAN, value.value)
        is NavValue.FloatValue -> listOf(VALUE_FLOAT, value.value)
        is NavValue.DoubleValue -> listOf(VALUE_DOUBLE, value.value)
    }
}

private fun decodeValue(encoded: List<*>): NavValue? {
    return when (encoded.firstOrNull() as? Int) {
        VALUE_NULL -> {
            if (encoded.size == 1) NavValue.Null else null
        }
        VALUE_TEXT -> {
            if (encoded.size == 2) {
                (encoded[1] as? String)?.let(NavValue::Text)
            } else {
                null
            }
        }
        VALUE_INT -> {
            if (encoded.size == 2) {
                (encoded[1] as? Int)?.let(NavValue::IntValue)
            } else {
                null
            }
        }
        VALUE_LONG -> {
            if (encoded.size == 2) {
                (encoded[1] as? Long)?.let(NavValue::LongValue)
            } else {
                null
            }
        }
        VALUE_BOOLEAN -> {
            if (encoded.size == 2) {
                (encoded[1] as? Boolean)?.let(NavValue::BooleanValue)
            } else {
                null
            }
        }
        VALUE_FLOAT -> {
            if (encoded.size == 2) {
                (encoded[1] as? Float)?.let(NavValue::FloatValue)
            } else {
                null
            }
        }
        VALUE_DOUBLE -> {
            if (encoded.size == 2) {
                (encoded[1] as? Double)?.let(NavValue::DoubleValue)
            } else {
                null
            }
        }
        else -> null
    }
}

private const val LEGACY_FORMAT_VERSION = 4
private const val FORMAT_VERSION = 5
private const val KEY_FORMAT_VERSION = "formatVersion"
private const val KEY_OWNER_SCOPE_ID = "ownerScopeId"
private const val KEY_ACTIVE_STACK_ID = "activeStackId"
private const val KEY_SELECTION_HISTORY = "selectionHistory"
private const val KEY_STACKS = "stacks"
private const val KEY_STACK_ID = "stackId"
private const val KEY_ENTRIES = "entries"
private const val KEY_DESTINATION_STATE = "destinationState"
private const val KEY_ENTRY_ID = "id"
private const val KEY_ROUTE_NAME = "routeName"
private const val KEY_GRAPH_ENTRIES = "graphEntries"
private const val KEY_ROUTE_ARGUMENTS = "routeArguments"

private const val VALUE_NULL = 0
private const val VALUE_TEXT = 1
private const val VALUE_INT = 2
private const val VALUE_LONG = 3
private const val VALUE_BOOLEAN = 4
private const val VALUE_FLOAT = 5
private const val VALUE_DOUBLE = 6
private const val MAX_STACK_COUNT = 100
private const val MAX_ENTRY_COUNT = 10_000
