package com.viewcompose.preview.tooling

import kotlinx.serialization.Serializable

/** Stable defaults shared by annotations, Gradle tooling, and render workers. */
object PreviewDefaults {
    /** Default logical width of the reference phone preview. */
    const val WIDTH_DP: Int = 411
    /**
     * Annotation/configuration value requesting a bounded full-content render.
     */
    const val AUTO_HEIGHT_DP: Int = -1

    /**
     * Initial device viewport used while resolving an [AUTO_HEIGHT_DP] preview.
     */
    const val VIEWPORT_HEIGHT_DP: Int = 891

    /**
     * Default preview height. Auto previews keep at least one viewport and grow when their root
     * content can still scroll vertically.
     */
    const val HEIGHT_DP: Int = AUTO_HEIGHT_DP

    /**
     * Safety ceiling for auto-height screenshots before density and pixel-budget limits apply.
     */
    const val MAX_AUTO_HEIGHT_DP: Int = 4_096

    /**
     * Reference-phone density shared with Compose Preview's 411 x 891 dp phone configuration.
     * 420 dpi / Android's 160 dpi baseline = 2.625 physical pixels per dp.
     */
    const val DENSITY: Float = 2.625f

    /** Reference-tablet density used by Compose Preview's 240 dpi tablet configuration. */
    const val TABLET_DENSITY: Float = 1.5f

    /** Default font-scale multiplier. */
    const val FONT_SCALE: Float = 1f

    /** Default BCP-47 locale tag. */
    const val LOCALE_TAG: String = "en-US"

    /** Annotation sentinel that delegates API selection to the render environment. */
    const val UNSPECIFIED_API_LEVEL: Int = -1
}

/**
 * Explicit day/night resource mode used for a deterministic preview render.
 *
 * The renderer resolves colors, shapes, and typography from the previewed application's Android
 * theme. This value selects that theme's light or dark resource configuration; it does not replace
 * the application theme with framework-default tokens.
 */
@Serializable
enum class PreviewTheme {
    Light,
    Dark,
}

/**
 * Explicit layout direction used for a deterministic preview render.
 */
@Serializable
enum class PreviewLayoutDirection {
    Ltr,
    Rtl,
}

/**
 * Fully resolved environment for one static preview render.
 *
 * This model intentionally contains no "system" values. A caller must resolve system-dependent
 * choices before sending a request so the same request produces the same preview in Gradle, CI,
 * and Android Studio.
 *
 * @sample com.viewcompose.preview.tooling.samples.previewConfigurationMatrixSample
 * @property widthDp logical canvas width; must be greater than zero
 * @property heightDp positive fixed viewport height or [PreviewDefaults.AUTO_HEIGHT_DP]
 * @property density positive finite physical-pixels-per-dp scale
 * @property fontScale positive finite scale applied to `sp` text
 * @property localeTags non-empty ordered BCP-47 locale preference list without blank entries
 * @property layoutDirection explicit layout direction independent of the host system
 * @property theme explicit light or dark application-resource mode
 * @property apiLevel positive Android API level, or `null` to use the worker-supported default
 */
@Serializable
data class PreviewConfiguration(
    val widthDp: Int = PreviewDefaults.WIDTH_DP,
    val heightDp: Int = PreviewDefaults.HEIGHT_DP,
    val density: Float = PreviewDefaults.DENSITY,
    val fontScale: Float = PreviewDefaults.FONT_SCALE,
    val localeTags: List<String> = listOf(PreviewDefaults.LOCALE_TAG),
    val layoutDirection: PreviewLayoutDirection = PreviewLayoutDirection.Ltr,
    val theme: PreviewTheme = PreviewTheme.Light,
    val apiLevel: Int? = null,
) {
    init {
        require(widthDp > 0) { "Preview widthDp must be greater than zero." }
        require(heightDp == PreviewDefaults.AUTO_HEIGHT_DP || heightDp > 0) {
            "Preview heightDp must be PreviewDefaults.AUTO_HEIGHT_DP or greater than zero."
        }
        require(density.isFinite() && density > 0f) {
            "Preview density must be finite and greater than zero."
        }
        require(fontScale.isFinite() && fontScale > 0f) {
            "Preview fontScale must be finite and greater than zero."
        }
        require(localeTags.isNotEmpty()) { "Preview localeTags must not be empty." }
        require(localeTags.none(String::isBlank)) {
            "Preview localeTags must not contain blank values."
        }
        require(apiLevel == null || apiLevel > 0) {
            "Preview apiLevel must be null or greater than zero."
        }
    }
}

/** Concrete device height used by Android resources and the first layout pass. */
val PreviewConfiguration.viewportHeightDp: Int
    get() = if (heightDp == PreviewDefaults.AUTO_HEIGHT_DP) {
        PreviewDefaults.VIEWPORT_HEIGHT_DP
    } else {
        heightDp
    }

/** Whether the screenshot canvas may grow beyond [viewportHeightDp]. */
val PreviewConfiguration.isAutoHeight: Boolean
    get() = heightDp == PreviewDefaults.AUTO_HEIGHT_DP

/**
 * Named configuration presented as one selectable preview variant.
 *
 * @property id path-safe stable ID using lowercase ASCII words separated by `-` or reserved `__`
 * @property displayName non-blank human-readable label
 * @property configuration fully resolved render configuration
 */
@Serializable
data class PreviewVariant(
    val id: String,
    val displayName: String,
    val configuration: PreviewConfiguration,
) {
    init {
        requireStablePreviewId(id, "Preview variant id")
        require(displayName.isNotBlank()) { "Preview variant displayName must not be blank." }
    }
}

/**
 * Nullable fields applied by one matrix option over a base configuration.
 *
 * A `null` field inherits the current value. Axes apply from first to last, so later options win
 * when several axes override the same field. API level cannot explicitly reset a non-null base to
 * `null`; construct a different base when that distinction is required.
 *
 * @property widthDp replacement logical width, or `null` to inherit
 * @property heightDp replacement fixed/auto height, or `null` to inherit
 * @property density replacement density, or `null` to inherit
 * @property fontScale replacement font scale, or `null` to inherit
 * @property localeTags replacement locale preference list, or `null` to inherit
 * @property layoutDirection replacement direction, or `null` to inherit
 * @property theme replacement resource mode, or `null` to inherit
 * @property apiLevel replacement positive API level, or `null` to inherit
 */
@Serializable
data class PreviewConfigurationOverride(
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val density: Float? = null,
    val fontScale: Float? = null,
    val localeTags: List<String>? = null,
    val layoutDirection: PreviewLayoutDirection? = null,
    val theme: PreviewTheme? = null,
    val apiLevel: Int? = null,
) {
    /**
     * Returns a validated configuration with every non-null override applied to [base].
     *
     * @throws IllegalArgumentException when the resulting configuration violates its invariants
     */
    fun applyTo(base: PreviewConfiguration): PreviewConfiguration {
        return PreviewConfiguration(
            widthDp = widthDp ?: base.widthDp,
            heightDp = heightDp ?: base.heightDp,
            density = density ?: base.density,
            fontScale = fontScale ?: base.fontScale,
            localeTags = localeTags ?: base.localeTags,
            layoutDirection = layoutDirection ?: base.layoutDirection,
            theme = theme ?: base.theme,
            apiLevel = apiLevel ?: base.apiLevel,
        )
    }
}

/**
 * One named choice on a preview matrix axis.
 *
 * @property id stable option ID, unique within its owning axis
 * @property displayName non-blank label shown in tooling
 * @property override fields applied when this option participates in a combination
 */
@Serializable
data class PreviewConfigurationOption(
    val id: String,
    val displayName: String,
    val override: PreviewConfigurationOverride,
) {
    init {
        requireStablePreviewId(id, "Preview configuration option id")
        require(displayName.isNotBlank()) {
            "Preview configuration option displayName must not be blank."
        }
    }
}

/**
 * One independently selectable dimension of a preview matrix.
 *
 * @property id stable axis ID, unique within a matrix
 * @property displayName non-blank label shown in tooling
 * @property options non-empty options with unique stable IDs, preserved in declared order
 */
@Serializable
data class PreviewConfigurationAxis(
    val id: String,
    val displayName: String,
    val options: List<PreviewConfigurationOption>,
) {
    init {
        requireStablePreviewId(id, "Preview configuration axis id")
        require(displayName.isNotBlank()) {
            "Preview configuration axis displayName must not be blank."
        }
        require(options.isNotEmpty()) { "Preview configuration axis must contain options." }
        val duplicateIds = options.groupingBy(PreviewConfigurationOption::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Preview configuration axis '$id' contains duplicate option ids: " +
                duplicateIds.sorted().joinToString()
        }
    }
}

/**
 * Deterministic Cartesian product used by Gradle, tests, and the IDE variant selector.
 *
 * @sample com.viewcompose.preview.tooling.samples.previewConfigurationMatrixSample
 * @property base initial resolved configuration inherited by every combination
 * @property axes non-empty ordered axes with unique IDs
 */
@Serializable
data class PreviewConfigurationMatrix(
    val base: PreviewConfiguration = PreviewConfiguration(),
    val axes: List<PreviewConfigurationAxis>,
) {
    init {
        require(axes.isNotEmpty()) { "Preview configuration matrix must contain axes." }
        val duplicateIds = axes.groupingBy(PreviewConfigurationAxis::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Preview configuration matrix contains duplicate axis ids: " +
                duplicateIds.sorted().joinToString()
        }
    }

    /**
     * Expands all axes into variants in deterministic declaration order.
     *
     * IDs concatenate `axis-option` segments with the reserved `__` separator. Display names use
     * ` / `. Later axes apply after earlier axes and therefore win on overlapping fields. The
     * returned count is the product of option counts and no combinations are cached.
     */
    fun variants(): List<PreviewVariant> {
        val combinations = axes.fold(
            initial = listOf(MatrixCombination(configuration = base)),
        ) { existing, axis ->
            existing.flatMap { combination ->
                axis.options.map { option ->
                    MatrixCombination(
                        optionIds = combination.optionIds + "${axis.id}-${option.id}",
                        optionNames = combination.optionNames + option.displayName,
                        configuration = option.override.applyTo(combination.configuration),
                    )
                }
            }
        }
        return combinations.map { combination ->
            PreviewVariant(
                id = combination.optionIds.joinToString(MATRIX_ID_SEPARATOR),
                displayName = combination.optionNames.joinToString(" / "),
                configuration = combination.configuration,
            )
        }
    }
}

/** Reusable axes covering common first-party static preview configurations. */
object PreviewConfigurationPresets {
    /** Light and dark application-resource modes. */
    val Theme: PreviewConfigurationAxis = PreviewConfigurationAxis(
        id = "theme",
        displayName = "Theme",
        options = listOf(
            PreviewConfigurationOption(
                id = "light",
                displayName = "Light",
                override = PreviewConfigurationOverride(theme = PreviewTheme.Light),
            ),
            PreviewConfigurationOption(
                id = "dark",
                displayName = "Dark",
                override = PreviewConfigurationOverride(theme = PreviewTheme.Dark),
            ),
        ),
    )

    /** LTR English and RTL Arabic locale/direction pairs. */
    val LayoutDirection: PreviewConfigurationAxis = PreviewConfigurationAxis(
        id = "layout-direction",
        displayName = "Layout direction",
        options = listOf(
            PreviewConfigurationOption(
                id = "ltr",
                displayName = "LTR · en-US",
                override = PreviewConfigurationOverride(
                    localeTags = listOf("en-US"),
                    layoutDirection = PreviewLayoutDirection.Ltr,
                ),
            ),
            PreviewConfigurationOption(
                id = "rtl",
                displayName = "RTL · ar-EG",
                override = PreviewConfigurationOverride(
                    localeTags = listOf("ar-EG"),
                    layoutDirection = PreviewLayoutDirection.Rtl,
                ),
            ),
        ),
    )

    /** Reference phone and tablet dimensions with matching densities. */
    val Device: PreviewConfigurationAxis = PreviewConfigurationAxis(
        id = "device",
        displayName = "Device",
        options = listOf(
            PreviewConfigurationOption(
                id = "phone",
                displayName = "Phone",
                override = PreviewConfigurationOverride(
                    widthDp = 411,
                    heightDp = 891,
                    density = PreviewDefaults.DENSITY,
                ),
            ),
            PreviewConfigurationOption(
                id = "tablet",
                displayName = "Tablet",
                override = PreviewConfigurationOverride(
                    widthDp = 800,
                    heightDp = 1280,
                    density = PreviewDefaults.TABLET_DENSITY,
                ),
            ),
        ),
    )

    /** Default, large, and accessibility-scale typography options. */
    val FontScale: PreviewConfigurationAxis = PreviewConfigurationAxis(
        id = "font-scale",
        displayName = "Font scale",
        options = listOf(
            PreviewConfigurationOption(
                id = "font-default",
                displayName = "Font 100%",
                override = PreviewConfigurationOverride(fontScale = 1f),
            ),
            PreviewConfigurationOption(
                id = "font-large",
                displayName = "Font 130%",
                override = PreviewConfigurationOverride(fontScale = 1.3f),
            ),
            PreviewConfigurationOption(
                id = "font-largest",
                displayName = "Font 200%",
                override = PreviewConfigurationOverride(fontScale = 2f),
            ),
        ),
    )
}

private data class MatrixCombination(
    val optionIds: List<String> = emptyList(),
    val optionNames: List<String> = emptyList(),
    val configuration: PreviewConfiguration,
)

private const val MATRIX_ID_SEPARATOR: String = "__"
