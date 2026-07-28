package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 限制导航图 DSL 的接收者作用域。
 * Restricts receiver scope for the navigation graph DSL.
 */
@DslMarker
annotation class NavGraphDsl

/**
 * 导航图中的节点契约，可能是 destination 或嵌套 graph。
 * Node contract inside a navigation graph, either a destination or a nested graph.
 */
sealed interface NavGraphNode {
    val route: String
    val deepLinks: List<NavDeepLink>
}

/**
 * 导航图中的叶子目的地。
 * Leaf destination inside a navigation graph.
 */
class NavDestination internal constructor(
    override val route: String,
    deepLinks: List<NavDeepLink>,
) : NavGraphNode {
    override val deepLinks: List<NavDeepLink> = Collections.unmodifiableList(
        ArrayList(deepLinks),
    )

    init {
        require(route.isNotBlank()) {
            "Navigation destination route must not be blank."
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is NavDestination &&
            route == other.route &&
            deepLinks == other.deepLinks
    }

    override fun hashCode(): Int {
        var result = route.hashCode()
        result = 31 * result + deepLinks.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavDestination(route=$route, deepLinks=$deepLinks)"
    }
}

/**
 * 不可变导航图，提供 route 解析、startDestination 展开和 deep-link 匹配。
 * Immutable navigation graph that resolves routes, expands start destinations, and matches deep links.
 */
class NavGraph internal constructor(
    override val route: String,
    val startDestination: NavRoute,
    children: List<NavGraphNode>,
    deepLinks: List<NavDeepLink>,
) : NavGraphNode {
    override val deepLinks: List<NavDeepLink> = Collections.unmodifiableList(
        ArrayList(deepLinks),
    )
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
        // routeIndex 包含当前图及所有后代节点，用于 O(1) route 名称查找和重复 route 校验。
        // routeIndex includes this graph and all descendants for O(1) route lookup and duplicate-route validation.
        val mutableIndex = LinkedHashMap<String, IndexedNode>()
        indexInto(
            target = mutableIndex,
            ancestorGraphRoutes = emptyList(),
        )
        routeIndex = Collections.unmodifiableMap(mutableIndex)
        val registeredPatterns = mutableSetOf<String>()
        deepLinkTargets = Collections.unmodifiableList(
            mutableIndex.values.flatMap { indexed ->
                indexed.node.deepLinks.map { deepLink ->
                    check(registeredPatterns.add(deepLink.uriPattern)) {
                        "Navigation deep-link pattern '${deepLink.uriPattern}' is registered " +
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
     * 将 route 解析为最终叶子目的地及其 graph 层级。
     * Resolves a route to the final leaf destination plus its graph hierarchy.
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

    fun contains(routeName: String): Boolean = routeName in routeIndex

    fun resolveDeepLink(uri: String): NavDeepLinkResolution {
        return resolveDeepLinkTargets(
            uri = uri,
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
        // 嵌套 graph route 的参数会传递给其 startDestination，让 deep link/graph entry 参数保持一致。
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

    override fun equals(other: Any?): Boolean {
        return other is NavGraph &&
            route == other.route &&
            startDestination == other.startDestination &&
            children == other.children &&
            deepLinks == other.deepLinks
    }

    override fun hashCode(): Int {
        var result = route.hashCode()
        result = 31 * result + startDestination.hashCode()
        result = 31 * result + children.hashCode()
        result = 31 * result + deepLinks.hashCode()
        return result
    }

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
 * NavGraph 解析结果，包含实际 destination 和所属 graphPath。
 * NavGraph resolution containing the concrete destination and owning graphPath.
 */
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

/**
 * navigation DSL 的可变构建器，最终输出不可变 NavGraph。
 * Mutable builder for the navigation DSL that produces an immutable NavGraph.
 */
@NavGraphDsl
class NavGraphBuilder internal constructor(
    private val route: String,
    private val startDestination: NavRoute,
    private val deepLinks: List<NavDeepLink>,
) {
    private val children = mutableListOf<NavGraphNode>()

    /**
     * 添加一个叶子目的地。
     * Adds one leaf destination.
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
     * 添加一个嵌套导航图。
     * Adds one nested navigation graph.
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
 * 构建一个不可变导航图。
 * Builds an immutable navigation graph.
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
