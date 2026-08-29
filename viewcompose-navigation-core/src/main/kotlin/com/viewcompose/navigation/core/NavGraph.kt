package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/** Restricts implicit receivers while declaring a navigation graph. */
@DslMarker
annotation class NavGraphDsl

/** Node inside an immutable navigation graph, either a leaf destination or a nested graph. */
sealed interface NavGraphNode {
    /** Non-blank route name, unique across the complete root graph. */
    val route: String
    /** Immutable deep-link patterns registered directly on this node. */
    val deepLinks: List<NavDeepLink>
}

/**
 * Leaf destination inside a navigation graph.
 *
 * @property route non-blank route name
 * @param deepLinks deep-link patterns copied in declaration order
 */
class NavDestination internal constructor(
    override val route: String,
    deepLinks: List<NavDeepLink>,
) : NavGraphNode {
    /** Immutable deep-link patterns registered for this destination. */
    override val deepLinks: List<NavDeepLink> = Collections.unmodifiableList(
        ArrayList(deepLinks),
    )

    init {
        require(route.isNotBlank()) {
            "Navigation destination route must not be blank."
        }
    }

    /** Compares route and deep-link declarations structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavDestination &&
            route == other.route &&
            deepLinks == other.deepLinks
    }

    /** Returns the structural hash of route and deep links. */
    override fun hashCode(): Int {
        var result = route.hashCode()
        result = 31 * result + deepLinks.hashCode()
        return result
    }

    /** Returns a diagnostic representation of this destination declaration. */
    override fun toString(): String {
        return "NavDestination(route=$route, deepLinks=$deepLinks)"
    }
}

/**
 * Immutable navigation graph that resolves routes, expands nested starts, and matches deep links.
 *
 * Route names and deep-link matcher triples must be unique across the complete graph. A nested
 * graph's start destination must be its direct child. Resolving a graph route recursively enters
 * start destinations while preserving graph-owner paths and inherited arguments.
 *
 * @sample com.viewcompose.navigation.core.samples.navigationGraphSample
 * @property route non-blank route name of this graph node
 * @property startDestination direct child entered when this graph route is requested
 * @param children non-empty child declarations copied in order
 * @param deepLinks deep-link patterns registered directly on this graph route
 */
class NavGraph internal constructor(
    override val route: String,
    val startDestination: NavRoute,
    children: List<NavGraphNode>,
    deepLinks: List<NavDeepLink>,
) : NavGraphNode {
    /** Immutable deep-link patterns registered directly on this graph route. */
    override val deepLinks: List<NavDeepLink> = Collections.unmodifiableList(
        ArrayList(deepLinks),
    )
    /** Immutable direct children in declaration order. */
    val children: List<NavGraphNode> = Collections.unmodifiableList(
        ArrayList(children),
    )

    private val routeIndex: Map<String, IndexedNode>
    private val deepLinkTargets: List<NavDeepLinkTarget>

    init {
        require(route.isNotBlank()) {
            "Navigation graph route must not be blank."
        }
        require(this.children.isNotEmpty()) {
            "Navigation graph '$route' must contain at least one destination."
        }
        validateStartDestination()
        // routeIndex includes this graph and all descendants for O(1) route lookup and duplicate-route validation.
        val mutableIndex = LinkedHashMap<String, IndexedNode>()
        indexInto(
            target = mutableIndex,
            ancestorGraphRoutes = emptyList(),
        )
        routeIndex = Collections.unmodifiableMap(mutableIndex)
        val registeredMatchers = mutableSetOf<NavDeepLinkMatchIdentity>()
        deepLinkTargets = Collections.unmodifiableList(
            mutableIndex.values.flatMap { indexed ->
                indexed.node.deepLinks.map { deepLink ->
                    check(registeredMatchers.add(deepLink.matchIdentity)) {
                        "Navigation deep-link matcher '${deepLink.matchIdentity}' is registered " +
                            "more than once."
                    }
                    NavDeepLinkTarget(
                        routeName = indexed.node.route,
                        deepLink = deepLink,
                    )
                }
            },
        )
    }

    /**
     * Resolves [route] to a leaf destination and its root-to-leaf graph-owner path.
     *
     * Requesting a graph route enters that graph's start chain. Arguments on the requested graph
     * override start-destination defaults and propagate through nested starts.
     *
     * @throws IllegalArgumentException if [route] is not registered
     */
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

    /** Returns whether [routeName] identifies this graph or any descendant node. */
    fun contains(routeName: String): Boolean = routeName in routeIndex

    /**
     * Resolves one URI-only request against all deep links registered in this graph.
     *
     * @param uri untrusted absolute hierarchical URI input
     * @return a match, no-match result, or structured rejection
     */
    fun resolveDeepLink(uri: String): NavDeepLinkResolution {
        return resolveDeepLinkTargets(
            uri = uri,
            targets = deepLinkTargets,
        )
    }

    /**
     * Resolves a structured URI, action, and MIME [request] against every registered deep link.
     *
     * Malformed request fields and tied most-specific declarations are rejected before a route is
     * returned. Resolution is immutable, side-effect free, and safe to call from any thread.
     *
     * @param request platform-neutral untrusted external-navigation input
     * @return a match, no-match result, or structured rejection
     */
    fun resolveDeepLink(request: NavDeepLinkRequest): NavDeepLinkResolution {
        return resolveDeepLinkTargets(
            request = request,
            targets = deepLinkTargets,
        )
    }

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
        // Arguments on a nested graph route flow into its startDestination so deep links and graph entries stay aligned.
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

    /** Compares the complete immutable graph declaration structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavGraph &&
            route == other.route &&
            startDestination == other.startDestination &&
            children == other.children &&
            deepLinks == other.deepLinks
    }

    /** Returns the structural hash of the complete graph declaration. */
    override fun hashCode(): Int {
        var result = route.hashCode()
        result = 31 * result + startDestination.hashCode()
        result = 31 * result + children.hashCode()
        result = 31 * result + deepLinks.hashCode()
        return result
    }

    /** Returns a diagnostic representation of this graph and its direct children. */
    override fun toString(): String {
        return "NavGraph(" +
            "route=$route, " +
            "startDestination=$startDestination, " +
            "children=$children, " +
            "deepLinks=$deepLinks" +
            ")"
    }

    private data class IndexedNode(
        val node: NavGraphNode,
        val ancestorGraphRoutes: List<String>,
    )
}

/**
 * Resolved leaf destination and its graph-owner path.
 *
 * [graphPath] is ordered root-to-leaf. [enteredGraphRoute] identifies the graph explicitly entered
 * by the request, allowing the controller to allocate a new owner at that boundary instead of
 * incorrectly reusing a previous graph instance.
 *
 * @property destination concrete leaf route with merged arguments
 * @param graphPath copied root-to-leaf graph routes
 * @property enteredGraphRoute explicitly requested graph route, or `null` for a leaf request
 */
class NavGraphResolution internal constructor(
    val destination: NavRoute,
    graphPath: List<NavRoute>,
    val enteredGraphRoute: String? = null,
) {
    /** Immutable root-to-leaf graph-owner route path. */
    val graphPath: List<NavRoute> = Collections.unmodifiableList(
        ArrayList(graphPath),
    )
    /** Immutable route-name projection of [graphPath]. */
    val hierarchy: List<String> = Collections.unmodifiableList(
        this.graphPath.map(NavRoute::name),
    )

    /** Compares destination, graph path, and explicit entry boundary structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavGraphResolution &&
            destination == other.destination &&
            graphPath == other.graphPath &&
            enteredGraphRoute == other.enteredGraphRoute
    }

    /** Returns the structural hash of every resolution field. */
    override fun hashCode(): Int {
        var result = destination.hashCode()
        result = 31 * result + graphPath.hashCode()
        result = 31 * result + (enteredGraphRoute?.hashCode() ?: 0)
        return result
    }

    /** Returns a diagnostic representation of the complete resolution. */
    override fun toString(): String {
        return "NavGraphResolution(" +
            "destination=$destination, " +
            "graphPath=$graphPath, " +
            "enteredGraphRoute=$enteredGraphRoute" +
            ")"
    }
}

/** Mutable DSL receiver that produces an immutable [NavGraph]. */
@NavGraphDsl
class NavGraphBuilder internal constructor(
    private val route: String,
    private val startDestination: NavRoute,
    private val deepLinks: List<NavDeepLink>,
) {
    private val children = mutableListOf<NavGraphNode>()

    /**
     * Adds a leaf destination.
     *
     * @param route non-blank route name unique within the eventual root graph
     * @param deepLinks deep-link patterns registered on this destination
     */
    fun destination(
        route: String,
        deepLinks: List<NavDeepLink> = emptyList(),
    ) {
        children += NavDestination(
            route = route,
            deepLinks = deepLinks,
        )
    }

    /**
     * Adds a nested navigation graph.
     *
     * @param route non-blank nested graph route unique within the eventual root graph
     * @param startDestination route of a direct child declared by [builder]
     * @param deepLinks deep-link patterns that enter the nested graph
     * @param builder nested graph declarations
     */
    fun navigation(
        route: String,
        startDestination: NavRoute,
        deepLinks: List<NavDeepLink> = emptyList(),
        builder: NavGraphBuilder.() -> Unit,
    ) {
        children += NavGraphBuilder(
            route = route,
            startDestination = startDestination,
            deepLinks = deepLinks,
        ).apply(builder).build()
    }

    internal fun build(): NavGraph {
        return NavGraph(
            route = route,
            startDestination = startDestination,
            children = children,
            deepLinks = deepLinks,
        )
    }
}

/**
 * Builds and validates an immutable navigation graph.
 *
 * @sample com.viewcompose.navigation.core.samples.navigationGraphSample
 * @param route non-blank root graph route
 * @param startDestination route of a direct child declared by [builder]
 * @param deepLinks deep-link patterns that enter the root graph
 * @param builder graph destination and nested-graph declarations
 * @throws IllegalArgumentException for invalid route or start declarations
 * @throws IllegalStateException for duplicate routes or deep-link patterns
 */
fun navGraph(
    route: String,
    startDestination: NavRoute,
    deepLinks: List<NavDeepLink> = emptyList(),
    builder: NavGraphBuilder.() -> Unit,
): NavGraph {
    return NavGraphBuilder(
        route = route,
        startDestination = startDestination,
        deepLinks = deepLinks,
    ).apply(builder).build()
}
