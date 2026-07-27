package com.viewcompose.navigation

import android.os.Bundle
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavGraph
import com.viewcompose.navigation.core.NavGraphEntry
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.widget.core.Saver
import com.viewcompose.widget.core.mapSaver

internal data class NavHostRestorableState(
    val snapshot: NavBackStackSnapshot,
    val destinationState: Bundle?,
)

internal fun navHostControllerSaver(
    startDestination: NavRoute,
): Saver<NavHostController, Map<String, Any?>> {
    return mapSaver(
        save = { controller ->
            encodeNavHostState(controller.stateForSave())
        },
        restore = { saved ->
            val restored = decodeNavHostState(saved)
            if (restored == null) {
                createNavHostController(startDestination)
            } else {
                NavHostController(
                    backStackController = NavBackStackController.restore(restored.snapshot),
                    restoredDestinationState = restored.destinationState,
                )
            }
        },
    )
}

internal fun navHostControllerSaver(
    graph: NavGraph,
): Saver<NavHostController, Map<String, Any?>> {
    return mapSaver(
        save = { controller ->
            encodeNavHostState(controller.stateForSave())
        },
        restore = { saved ->
            val restored = decodeNavHostState(saved)
            if (restored == null) {
                createNavHostController(graph)
            } else {
                runCatching {
                    NavHostController(
                        backStackController = NavBackStackController.restore(
                            snapshot = restored.snapshot,
                            graph = graph,
                        ),
                        restoredDestinationState = restored.destinationState,
                    )
                }.getOrElse {
                    createNavHostController(graph)
                }
            }
        },
    )
}

internal fun encodeNavHostState(
    state: NavHostRestorableState,
): Map<String, Any?> {
    return linkedMapOf(
        KEY_FORMAT_VERSION to FORMAT_VERSION,
        KEY_ENTRIES to state.snapshot.entries.map(::encodeEntry),
        KEY_DESTINATION_STATE to state.destinationState?.let(::Bundle),
    )
}

internal fun decodeNavHostState(
    saved: Map<String, Any?>,
): NavHostRestorableState? {
    return runCatching {
        decodeNavHostStateUnsafe(saved)
    }.getOrNull()
}

private fun decodeNavHostStateUnsafe(
    saved: Map<String, Any?>,
): NavHostRestorableState? {
    if ((saved[KEY_FORMAT_VERSION] as? Number)?.toInt() != FORMAT_VERSION) {
        return null
    }
    val encodedEntries = saved[KEY_ENTRIES] as? List<*> ?: return null
    if (encodedEntries.isEmpty() || encodedEntries.size > MAX_ENTRY_COUNT) {
        return null
    }
    val entries = encodedEntries.map { encoded ->
        decodeEntry(encoded as? Map<*, *> ?: return null) ?: return null
    }
    val encodedDestinationState = saved[KEY_DESTINATION_STATE]
    if (encodedDestinationState != null && encodedDestinationState !is Bundle) {
        return null
    }
    return NavHostRestorableState(
        snapshot = NavBackStackSnapshot(entries),
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

private const val FORMAT_VERSION = 3
private const val KEY_FORMAT_VERSION = "formatVersion"
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
private const val MAX_ENTRY_COUNT = 10_000
