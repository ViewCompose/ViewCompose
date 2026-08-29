package com.viewcompose.navigation.core

/**
 * Couples one stable graph route name with the only typed argument encoder and decoder for [T].
 *
 * A spec is an application declaration, not back-stack state. [encode] produces the existing
 * closed [NavRoute] representation, so graph resolution, deep links, launch modes, transactions,
 * and process restoration continue to use one storage model. Recreate the same spec after process
 * death with a compatible [name] and argument schema.
 *
 * Encoder and decoder callbacks must be deterministic and must not retain navigation entries. An
 * exception is propagated to the caller; encoding completes before a controller command begins.
 *
 * @sample com.viewcompose.navigation.core.samples.typedRouteSample
 * @param T application route value reconstructed by [decode]
 * @property name non-blank stable identity registered in the navigation graph
 * @param encodeArguments converts a value into closed, persistable route arguments
 * @param decodeArguments reconstructs a value from an immutable route-argument snapshot
 */
class NavRouteSpec<T>(
    val name: String,
    private val encodeArguments: (T) -> Map<String, NavValue>,
    private val decodeArguments: (Map<String, NavValue>) -> T,
) {
    init {
        require(name.isNotBlank()) { "Navigation route spec name must not be blank." }
    }

    /**
     * Encodes [value] into a new immutable [NavRoute] carrying this spec's stable [name].
     *
     * [NavRoute] copies and validates the returned map. Encoder or argument-validation failures are
     * propagated before any navigation transaction can begin.
     */
    fun encode(value: T): NavRoute {
        return NavRoute(
            name = name,
            arguments = encodeArguments(value),
        )
    }

    /**
     * Decodes [route] after verifying that it carries this spec's stable [name].
     *
     * @throws IllegalArgumentException when [route] identifies another destination
     */
    fun decode(route: NavRoute): T {
        require(matches(route)) {
            "Navigation route '${route.name}' cannot be decoded by spec '$name'."
        }
        return decodeArguments(route.arguments)
    }

    /** Returns whether [route] carries this spec's stable [name], independent of its arguments. */
    fun matches(route: NavRoute): Boolean = route.name == name

    /** Returns the stable route name without exposing encoder or decoder callbacks. */
    override fun toString(): String = "NavRouteSpec(name=$name)"
}

/** Returns whether this entry belongs to [spec], independent of its concrete arguments. */
fun NavEntry.hasRoute(spec: NavRouteSpec<*>): Boolean = spec.matches(route)

/**
 * Reconstructs this entry's application route value through [spec].
 *
 * @throws IllegalArgumentException when this entry belongs to another route
 */
fun <T> NavEntry.toRoute(spec: NavRouteSpec<T>): T = spec.decode(route)
