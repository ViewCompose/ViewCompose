package com.viewcompose.ui.foundation

/** Selects the design-system-neutral execution strategy behind a component recipe. */
enum class UiComponentBackend {
    /** Retains Android behavior such as editing, range input, focus, or accessibility ownership. */
    NativeBehavioralCore,

    /** Composes shared ViewCompose primitives without a design-specific renderer branch. */
    DslComposite,

    /** Uses a design-system-neutral custom Android View driven by resolved values. */
    NeutralCustomView,

    /** Uses a named Android integration hidden behind a design-system public API. */
    NamedAndroidIntegration,
}

/** Reports the accepted fidelity result for one component recipe and capability path. */
enum class UiDesignConformance {
    Exact,
    Equivalent,
    Degraded,
    Unsupported,
}

/**
 * Immutable diagnostic attribution for one design-system component family.
 *
 * This snapshot contains identity and evidence only. It is not a recipe registry and stores no
 * factories, callbacks, Android resources, or behavior closures.
 *
 * @property familyId stable design-system-owned component family identity
 * @property recipeId stable recipe identity selected above the renderer boundary
 * @property backend neutral execution strategy used by the resolved component
 * @property conformance accepted fidelity result for [capabilityPath]
 * @property capabilityPath stable description of the selected platform path
 * @property fallback stable fallback identity, or `none` when no fallback is active
 * @throws IllegalArgumentException when an identity or capability value is blank
 */
data class UiComponentAttribution(
    val familyId: String,
    val recipeId: String,
    val backend: UiComponentBackend,
    val conformance: UiDesignConformance,
    val capabilityPath: String,
    val fallback: String = "none",
) {
    init {
        require(familyId.isNotBlank()) { "UiComponentAttribution familyId must not be blank." }
        require(recipeId.isNotBlank()) { "UiComponentAttribution recipeId must not be blank." }
        require(capabilityPath.isNotBlank()) {
            "UiComponentAttribution capabilityPath must not be blank."
        }
        require(fallback.isNotBlank()) { "UiComponentAttribution fallback must not be blank." }
    }
}

/**
 * Immutable diagnostic attribution for one platform or library integration capability.
 *
 * This describes the selected execution path without carrying factories, Android objects, or
 * mutable backend state. It can therefore travel with captured composition-local snapshots.
 *
 * @property capabilityId stable capability identity such as `overlay.snackbar`
 * @property transportId stable owner of lifecycle, queuing, or platform-surface transport
 * @property presenterId stable presenter or behavior backend identity
 * @property conformance accepted fidelity result for this integration path
 * @property fallback stable fallback identity, or `none` when no fallback is active
 * @throws IllegalArgumentException when an identity or fallback value is blank
 */
data class UiIntegrationAttribution(
    val capabilityId: String,
    val transportId: String,
    val presenterId: String,
    val conformance: UiDesignConformance,
    val fallback: String = "none",
) {
    init {
        require(capabilityId.isNotBlank()) { "UiIntegrationAttribution capabilityId must not be blank." }
        require(transportId.isNotBlank()) { "UiIntegrationAttribution transportId must not be blank." }
        require(presenterId.isNotBlank()) { "UiIntegrationAttribution presenterId must not be blank." }
        require(fallback.isNotBlank()) { "UiIntegrationAttribution fallback must not be blank." }
    }
}

/**
 * Immutable identity and component-attribution snapshot for one active design-system scope.
 *
 * Component families must be unique so screenshots and delayed-content diagnostics cannot report
 * contradictory backends for the same root snapshot.
 *
 * @property designSystemId stable selected design-system identity
 * @property recipeSetId stable versioned identity of the provided recipe slice
 * @property components bounded component evidence owned by this design-system scope
 * @property integrations bounded platform and library integration evidence for this scope
 * @throws IllegalArgumentException for blank identities or duplicate capability identities
 */
data class UiDesignSystemAttribution(
    val designSystemId: String,
    val recipeSetId: String,
    val components: List<UiComponentAttribution>,
    val integrations: List<UiIntegrationAttribution> = emptyList(),
) {
    init {
        require(designSystemId.isNotBlank()) {
            "UiDesignSystemAttribution designSystemId must not be blank."
        }
        require(recipeSetId.isNotBlank()) {
            "UiDesignSystemAttribution recipeSetId must not be blank."
        }
        require(components.map(UiComponentAttribution::familyId).distinct().size == components.size) {
            "UiDesignSystemAttribution component family ids must be unique."
        }
        require(
            integrations.map(UiIntegrationAttribution::capabilityId).distinct().size ==
                integrations.size,
        ) {
            "UiDesignSystemAttribution integration capability ids must be unique."
        }
    }

    /** Returns the attribution for [familyId], or `null` when the family is not provided. */
    fun component(familyId: String): UiComponentAttribution? {
        require(familyId.isNotBlank()) { "UiDesignSystemAttribution familyId must not be blank." }
        return components.firstOrNull { component -> component.familyId == familyId }
    }

    /** Returns integration evidence for [capabilityId], or `null` when it is not declared. */
    fun integration(capabilityId: String): UiIntegrationAttribution? {
        require(capabilityId.isNotBlank()) {
            "UiDesignSystemAttribution capabilityId must not be blank."
        }
        return integrations.firstOrNull { integration -> integration.capabilityId == capabilityId }
    }
}

private val LocalDesignSystemAttribution = uiLocalOf<UiDesignSystemAttribution?>(
    debugName = "DesignSystemAttribution",
    debugValueFormatter = { attribution ->
        attribution?.let { "${it.designSystemId}/${it.recipeSetId}" } ?: "absent"
    },
    defaultFactory = { null },
)

/** Reads optional design-system attribution for the current synchronous composition scope. */
object DesignSystemDiagnostics {
    /**
     * Returns the active immutable attribution, or `null` for a neutral/unattributed subtree.
     */
    val current: UiDesignSystemAttribution?
        get() = UiLocals.current(LocalDesignSystemAttribution)
}

/**
 * Provides one bounded design-system diagnostic snapshot while building [content].
 *
 * Delayed content captures this local through the existing composition-local snapshot mechanism.
 * The provider does not install recipes or alter rendering; the owning design-system provider must
 * resolve both from the same immutable root bundle.
 *
 * @sample com.viewcompose.ui.foundation.samples.designSystemAttributionSample
 * @receiver active tree builder receiving the diagnostic scope
 * @param attribution immutable identity, backend, and conformance evidence
 * @param content subtree built synchronously under [attribution]
 */
fun UiTreeBuilder.DesignSystemAttributionProvider(
    attribution: UiDesignSystemAttribution,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalDesignSystemAttribution, attribution, content)
}
