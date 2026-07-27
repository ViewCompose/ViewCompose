package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

@DslMarker
annotation class NavGraphDsl

sealed interface NavGraphNode {
    val route: String
}

class NavDestination internal constructor(
    override val route: String,
) : NavGraphNode {
    init {
        require(route.isNotBlank()) {
            "Navigation destination route must not be blank."
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is NavDestination && route == other.route
    }

    override fun hashCode(): Int = route.hashCode()

    override fun toString(): String = "NavDestination(route=$route)"
}

class NavGraph internal constructor(
    override val route: String,
    val startDestination: NavRoute,
    children: List<NavGraphNode>,
) : NavGraphNode {
    val children: List<NavGraphNode> = Collections.unmodifiableList(
        ArrayList(children),
    )

    private val routeIndex: Map<String, IndexedNode>

    init {
        require(route.isNotBlank()) {
            "Navigation graph route must not be blank."
        }
        require(this.children.isNotEmpty()) {
            "Navigation graph '$route' must contain at least one destination."
        }
        validateStartDestination()
        val mutableIndex = LinkedHashMap<String, IndexedNode>()
        indexInto(
            target = mutableIndex,
            ancestorGraphRoutes = emptyList(),
        )
        routeIndex = Collections.unmodifiableMap(mutableIndex)
    }

    fun resolve(route: NavRoute): NavGraphResolution {
        val indexed = requireNotNull(routeIndex[route.name]) {
            "Navigation route '${route.name}' is not registered in graph '${this.route}'."
        }
        val ancestorGraphPath = indexed.ancestorGraphRoutes.map(::NavRoute)
        return when (val node = indexed.node) {
            is NavDestination -> {
                NavGraphResolution(
                    destination = route,
                    graphPath = ancestorGraphPath,
                )
            }

            is NavGraph -> {
                node.resolveStartDestination(
                    graphPath = ancestorGraphPath + route,
                    inheritedArguments = route.arguments,
                    enteredGraphRoute = node.route,
                )
            }
        }
    }

    fun contains(routeName: String): Boolean = routeName in routeIndex

    private fun resolveStartDestination(
        graphPath: List<NavRoute>,
        inheritedArguments: Map<String, NavValue>,
        enteredGraphRoute: String,
    ): NavGraphResolution {
        val child = children.first { node ->
            node.route == startDestination.name
        }
        val mergedArguments = LinkedHashMap(startDestination.arguments).apply {
            putAll(inheritedArguments)
        }
        return when (child) {
            is NavDestination -> {
                NavGraphResolution(
                    destination = NavRoute(
                        name = child.route,
                        arguments = mergedArguments,
                    ),
                    graphPath = graphPath,
                    enteredGraphRoute = enteredGraphRoute,
                )
            }

            is NavGraph -> {
                child.resolveStartDestination(
                    graphPath = graphPath + NavRoute(
                        name = child.route,
                        arguments = mergedArguments,
                    ),
                    inheritedArguments = mergedArguments,
                    enteredGraphRoute = enteredGraphRoute,
                )
            }
        }
    }

    private fun validateStartDestination() {
        require(children.any { child -> child.route == startDestination.name }) {
            "Start destination '${startDestination.name}' must be a direct child of graph '$route'."
        }
    }

    private fun indexInto(
        target: MutableMap<String, IndexedNode>,
        ancestorGraphRoutes: List<String>,
    ) {
        check(
            target.put(
                route,
                IndexedNode(
                    node = this,
                    ancestorGraphRoutes = ancestorGraphRoutes,
                ),
            ) == null,
        ) {
            "Navigation route '$route' is registered more than once."
        }
        val childAncestors = ancestorGraphRoutes + route
        children.forEach { child ->
            when (child) {
                is NavDestination -> {
                    check(
                        target.put(
                            child.route,
                            IndexedNode(
                                node = child,
                                ancestorGraphRoutes = childAncestors,
                            ),
                        ) == null,
                    ) {
                        "Navigation route '${child.route}' is registered more than once."
                    }
                }

                is NavGraph -> {
                    child.indexInto(
                        target = target,
                        ancestorGraphRoutes = childAncestors,
                    )
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is NavGraph &&
            route == other.route &&
            startDestination == other.startDestination &&
            children == other.children
    }

    override fun hashCode(): Int {
        var result = route.hashCode()
        result = 31 * result + startDestination.hashCode()
        result = 31 * result + children.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavGraph(route=$route, startDestination=$startDestination, children=$children)"
    }

    private data class IndexedNode(
        val node: NavGraphNode,
        val ancestorGraphRoutes: List<String>,
    )
}

class NavGraphResolution internal constructor(
    val destination: NavRoute,
    graphPath: List<NavRoute>,
    val enteredGraphRoute: String? = null,
) {
    val graphPath: List<NavRoute> = Collections.unmodifiableList(
        ArrayList(graphPath),
    )
    val hierarchy: List<String> = Collections.unmodifiableList(
        this.graphPath.map(NavRoute::name),
    )

    override fun equals(other: Any?): Boolean {
        return other is NavGraphResolution &&
            destination == other.destination &&
            graphPath == other.graphPath &&
            enteredGraphRoute == other.enteredGraphRoute
    }

    override fun hashCode(): Int {
        var result = destination.hashCode()
        result = 31 * result + graphPath.hashCode()
        result = 31 * result + (enteredGraphRoute?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "NavGraphResolution(" +
            "destination=$destination, " +
            "graphPath=$graphPath, " +
            "enteredGraphRoute=$enteredGraphRoute" +
            ")"
    }
}

@NavGraphDsl
class NavGraphBuilder internal constructor(
    private val route: String,
    private val startDestination: NavRoute,
) {
    private val children = mutableListOf<NavGraphNode>()

    fun destination(route: String) {
        children += NavDestination(route)
    }

    fun navigation(
        route: String,
        startDestination: NavRoute,
        builder: NavGraphBuilder.() -> Unit,
    ) {
        children += NavGraphBuilder(
            route = route,
            startDestination = startDestination,
        ).apply(builder).build()
    }

    internal fun build(): NavGraph {
        return NavGraph(
            route = route,
            startDestination = startDestination,
            children = children,
        )
    }
}

fun navGraph(
    route: String,
    startDestination: NavRoute,
    builder: NavGraphBuilder.() -> Unit,
): NavGraph {
    return NavGraphBuilder(
        route = route,
        startDestination = startDestination,
    ).apply(builder).build()
}
