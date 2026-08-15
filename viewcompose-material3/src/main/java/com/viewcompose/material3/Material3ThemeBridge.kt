package com.viewcompose.material3

import android.content.Context
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.content.res.TypedArray
import com.google.android.material.color.DynamicColors
import com.viewcompose.ui.unit.UiSp
import com.viewcompose.ui.foundation.UiColors
import com.viewcompose.ui.foundation.UiOverlays
import com.viewcompose.ui.foundation.UiShapes
import com.viewcompose.ui.foundation.UiStateColor
import com.viewcompose.ui.foundation.UiStateColorDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeMetadata
import com.viewcompose.ui.foundation.UiThemeOrigin
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTokenProvenance
import com.viewcompose.ui.foundation.UiTypography

/**
 * Selects whether an Android Material 3 theme may use system-provided dynamic colors.
 *
 * [Disabled] always preserves the supplied context. [UseIfAvailable] wraps it only when the
 * Material library reports dynamic color support; otherwise it has the same behavior as
 * [Disabled].
 */
enum class Material3DynamicColorPolicy {
    /** Preserves the caller's Android theme without applying dynamic colors. */
    Disabled,

    /** Applies Material dynamic colors when supported and otherwise preserves the caller's theme. */
    UseIfAvailable,
}

/**
 * The Android context and semantic origin selected for one ViewCompose tree.
 *
 * Hosts must use [context] to create the root view as well as every overlay so
 * framework tokens and native widgets resolve the same Android theme.
 *
 * Instances are created by [Material3ThemeBridge.resolveContext]. The exposed [context] keeps a
 * stable wrapper identity across configuration refreshes, while [origin] is a live value that may
 * change after refresh.
 */
class Material3ResolvedTheme internal constructor(
    private val sourceContext: Context,
    private val dynamicColorPolicy: Material3DynamicColorPolicy,
    initialContext: Context,
    initialOrigin: UiThemeOrigin,
) {
    private val mutableContext = MutableContextWrapper(initialContext)

    /** Returns the stable context wrapper used to create the root View and every overlay. */
    val context: Context
        get() = mutableContext

    /** Reports the live semantic source of the currently resolved theme attributes. */
    var origin: UiThemeOrigin = initialOrigin
        private set

    /**
     * Refreshes the stable wrapper from its source Context without changing [context] identity.
     *
     * Named Android hosts call this before publishing a new resource environment snapshot.
     * Low-level hosts call it on the Android main thread before resolving tokens after an
     * imperative source-theme or configuration mutation.
     *
     * @sample com.viewcompose.material3.samples.material3ResolvedThemeRefreshSample
     * @throws IllegalStateException when called off the Android main thread
     */
    fun refresh() {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "Material3ResolvedTheme.refresh() must be called on the Android main thread."
        }
        val resolved = resolveAndroidThemeContext(
            context = sourceContext,
            dynamicColorPolicy = dynamicColorPolicy,
        )
        mutableContext.setBaseContext(resolved.context)
        origin = resolved.origin
    }
}

private data class ResolvedAndroidThemeContext(
    val context: Context,
    val origin: UiThemeOrigin,
)

private fun resolveAndroidThemeContext(
    context: Context,
    dynamicColorPolicy: Material3DynamicColorPolicy,
): ResolvedAndroidThemeContext {
    val useDynamicColor = dynamicColorPolicy == Material3DynamicColorPolicy.UseIfAvailable &&
        DynamicColors.isDynamicColorAvailable()
    return ResolvedAndroidThemeContext(
        context = if (useDynamicColor) {
            DynamicColors.wrapContextIfAvailable(context)
        } else {
            context
        },
        origin = if (useDynamicColor) {
            UiThemeOrigin.AndroidDynamicColor
        } else {
            UiThemeOrigin.AndroidTheme
        },
    )
}

/**
 * Resolves Android Material 3 themes into contexts and platform-neutral ViewCompose tokens.
 *
 * Resolution reads resources synchronously and must run on the Android main thread when the
 * returned context is used to create Views. The bridge does not retain the caller's host or own
 * its lifecycle.
 *
 * @sample com.viewcompose.material3.samples.material3ThemeBridgeSample
 */
object Material3ThemeBridge {
    /**
     * Resolves an Android theme context shared by Views and overlays.
     *
     * @param context themed Android context used as the fallback and resource source
     * @param dynamicColorPolicy policy selecting whether supported system dynamic colors are
     * applied
     * @return a new resolved theme with a stable context-wrapper identity
     */
    fun resolveContext(
        context: Context,
        dynamicColorPolicy: Material3DynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
    ): Material3ResolvedTheme {
        val resolved = resolveAndroidThemeContext(
            context = context,
            dynamicColorPolicy = dynamicColorPolicy,
        )
        return Material3ResolvedTheme(
            sourceContext = context,
            dynamicColorPolicy = dynamicColorPolicy,
            initialContext = resolved.context,
            initialOrigin = resolved.origin,
        )
    }

    /**
     * Reads a snapshot of ViewCompose theme tokens directly from an Android context.
     *
     * Use [resolveContext] with [fromResolvedTheme] when the same context must also create Views
     * and overlays.
     *
     * @param context themed Android context supplying Material and AppCompat attributes
     * @param dynamicColorPolicy policy selecting whether supported system dynamic colors are
     * applied
     * @return an immutable token snapshot with Android theme-origin and dark-mode metadata
     */
    fun fromContext(
        context: Context,
        dynamicColorPolicy: Material3DynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
    ): UiThemeTokens {
        return fromResolvedTheme(
            resolveContext(
                context = context,
                dynamicColorPolicy = dynamicColorPolicy,
            ),
        )
    }

    /**
     * Reads a token snapshot from a resolved theme and preserves origin and dark-mode metadata.
     *
     * @param resolvedTheme context and origin shared by the tree's Views and overlays
     * @return an immutable token snapshot reflecting the resolved context's current configuration
     */
    fun fromResolvedTheme(
        resolvedTheme: Material3ResolvedTheme,
    ): UiThemeTokens {
        val isDark = isNightMode(resolvedTheme.context)
        val snapshot = Material3ThemeSnapshotReader.read(resolvedTheme.context)
        return Material3ThemeTokenMapper.fromSnapshot(
            snapshot = snapshot,
            isDarkMode = isDark,
            sourceOrigin = resolvedTheme.origin,
        )
    }

    private fun isNightMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }
}

/**
 * Mapper from Android theme snapshots to ViewCompose tokens.
 */
internal object Material3ThemeTokenMapper {
    /**
     * Builds tokens from a complete Android snapshot, falling back to Material light/dark defaults.
     */
    fun fromSnapshot(
        snapshot: Material3ThemeSnapshot,
        isDarkMode: Boolean = false,
        sourceOrigin: UiThemeOrigin = UiThemeOrigin.AndroidTheme,
    ): UiThemeTokens {
        val fallback = if (isDarkMode) Material3ThemeDefaults.dark() else Material3ThemeDefaults.light()
        val baseTokens = fromThemeColors(
            readColor = { attr ->
                when (attr) {
                    android.R.attr.colorBackground -> snapshot.colors.background
                    com.google.android.material.R.attr.colorOnBackground -> snapshot.colors.onBackground
                    com.google.android.material.R.attr.colorSurface -> snapshot.colors.surface
                    com.google.android.material.R.attr.colorSurfaceDim -> snapshot.colors.surfaceDim
                    com.google.android.material.R.attr.colorSurfaceBright -> snapshot.colors.surfaceBright
                    com.google.android.material.R.attr.colorSurfaceContainerLowest ->
                        snapshot.colors.surfaceContainerLowest
                    com.google.android.material.R.attr.colorSurfaceContainerLow ->
                        snapshot.colors.surfaceContainerLow
                    com.google.android.material.R.attr.colorSurfaceContainer ->
                        snapshot.colors.surfaceContainer
                    com.google.android.material.R.attr.colorSurfaceContainerHigh ->
                        snapshot.colors.surfaceContainerHigh
                    com.google.android.material.R.attr.colorSurfaceContainerHighest ->
                        snapshot.colors.surfaceContainerHighest
                    com.google.android.material.R.attr.colorSurfaceVariant -> snapshot.colors.surfaceVariant
                    com.google.android.material.R.attr.colorOnSurface -> snapshot.colors.onSurface
                    com.google.android.material.R.attr.colorOnSurfaceVariant -> snapshot.colors.onSurfaceVariant
                    androidx.appcompat.R.attr.colorPrimary -> snapshot.colors.primary
                    com.google.android.material.R.attr.colorOnPrimary -> snapshot.colors.onPrimary
                    com.google.android.material.R.attr.colorPrimaryContainer -> snapshot.colors.primaryContainer
                    com.google.android.material.R.attr.colorOnPrimaryContainer -> snapshot.colors.onPrimaryContainer
                    com.google.android.material.R.attr.colorSecondary -> snapshot.colors.secondary
                    com.google.android.material.R.attr.colorOnSecondary -> snapshot.colors.onSecondary
                    com.google.android.material.R.attr.colorSecondaryContainer -> snapshot.colors.secondaryContainer
                    com.google.android.material.R.attr.colorOnSecondaryContainer -> snapshot.colors.onSecondaryContainer
                    com.google.android.material.R.attr.colorTertiary -> snapshot.colors.tertiary
                    com.google.android.material.R.attr.colorOnTertiary -> snapshot.colors.onTertiary
                    com.google.android.material.R.attr.colorTertiaryContainer -> snapshot.colors.tertiaryContainer
                    com.google.android.material.R.attr.colorOnTertiaryContainer ->
                        snapshot.colors.onTertiaryContainer
                    android.R.attr.colorError -> snapshot.colors.error
                    com.google.android.material.R.attr.colorOnError -> snapshot.colors.onError
                    com.google.android.material.R.attr.colorErrorContainer -> snapshot.colors.errorContainer
                    com.google.android.material.R.attr.colorOnErrorContainer -> snapshot.colors.onErrorContainer
                    com.google.android.material.R.attr.colorOutline -> snapshot.colors.outline
                    com.google.android.material.R.attr.colorOutlineVariant -> snapshot.colors.outlineVariant
                    com.google.android.material.R.attr.colorSurfaceInverse -> snapshot.colors.inverseSurface
                    com.google.android.material.R.attr.colorOnSurfaceInverse -> snapshot.colors.inverseOnSurface
                    com.google.android.material.R.attr.colorPrimaryInverse -> snapshot.colors.inversePrimary
                    android.R.attr.textColorPrimary -> snapshot.colors.onSurface
                    android.R.attr.textColorSecondary -> snapshot.colors.onSurfaceVariant
                    else -> null
                }
            },
            readStateColor = { attr ->
                when (attr) {
                    android.R.attr.textColorPrimary -> snapshot.colors.primaryText
                    android.R.attr.textColorSecondary -> snapshot.colors.secondaryText
                    androidx.appcompat.R.attr.colorControlNormal -> snapshot.colors.control
                    androidx.appcompat.R.attr.colorControlActivated -> snapshot.colors.controlActivated
                    else -> null
                }
            },
            readScrimOpacity = { snapshot.scrimOpacity },
            isDarkMode = isDarkMode,
        )
        return baseTokens.copy(
            colors = baseTokens.colors.copy(
                surfaceTint = snapshot.colors.surfaceTint ?: baseTokens.colors.primary,
                scrim = snapshot.colors.scrim ?: fallback.colors.scrim,
            ),
            typography = UiTypography(
                titleMedium = resolveTextStyle(
                    snapshot.typography.titleMedium
                        ?: snapshot.typography.titleLarge
                        ?: snapshot.typography.titleSmall,
                    fallback.typography.titleMedium,
                ),
                bodyMedium = resolveTextStyle(
                    snapshot.typography.bodyMedium
                        ?: snapshot.typography.bodyLarge
                        ?: snapshot.typography.bodySmall,
                    fallback.typography.bodyMedium,
                ),
                labelMedium = resolveTextStyle(
                    snapshot.typography.labelMedium
                        ?: snapshot.typography.labelLarge
                        ?: snapshot.typography.labelSmall,
                    fallback.typography.labelMedium,
                ),
                titleLarge = resolveTextStyle(snapshot.typography.titleLarge, fallback.typography.titleLarge),
                titleSmall = resolveTextStyle(snapshot.typography.titleSmall, fallback.typography.titleSmall),
                bodyLarge = resolveTextStyle(snapshot.typography.bodyLarge, fallback.typography.bodyLarge),
                bodySmall = resolveTextStyle(snapshot.typography.bodySmall, fallback.typography.bodySmall),
                labelLarge = resolveTextStyle(snapshot.typography.labelLarge, fallback.typography.labelLarge),
                labelSmall = resolveTextStyle(snapshot.typography.labelSmall, fallback.typography.labelSmall),
                headlineLarge = resolveTextStyle(
                    snapshot.typography.headlineLarge,
                    fallback.typography.headlineLarge,
                ),
                headlineMedium = resolveTextStyle(
                    snapshot.typography.headlineMedium,
                    fallback.typography.headlineMedium,
                ),
                headlineSmall = resolveTextStyle(
                    snapshot.typography.headlineSmall,
                    fallback.typography.headlineSmall,
                ),
                displayLarge = resolveTextStyle(
                    snapshot.typography.displayLarge,
                    fallback.typography.displayLarge,
                ),
                displayMedium = resolveTextStyle(
                    snapshot.typography.displayMedium,
                    fallback.typography.displayMedium,
                ),
                displaySmall = resolveTextStyle(
                    snapshot.typography.displaySmall,
                    fallback.typography.displaySmall,
                ),
            ),
            shapes = UiShapes(
                small = snapshot.shapes.small ?: fallback.shapes.small,
                medium = snapshot.shapes.medium ?: fallback.shapes.medium,
                large = snapshot.shapes.large ?: fallback.shapes.large,
                extraSmall = snapshot.shapes.extraSmall ?: fallback.shapes.extraSmall,
                extraLarge = snapshot.shapes.extraLarge ?: fallback.shapes.extraLarge,
                full = fallback.shapes.full,
            ),
            metadata = UiThemeMetadata(
                origin = sourceOrigin,
                isDark = isDarkMode,
                provenance = material3Provenance(
                    snapshot = snapshot,
                    sourceOrigin = sourceOrigin,
                ),
            ),
        )
    }

    private fun material3Provenance(
        snapshot: Material3ThemeSnapshot,
        sourceOrigin: UiThemeOrigin,
    ): UiTokenProvenance {
        val mapped = linkedMapOf<String, UiThemeOrigin>()
        fun mapped(path: String, value: Any?) {
            if (value != null) mapped[path] = sourceOrigin
        }
        with(snapshot.colors) {
            mapped("colors.background", background)
            mapped("colors.surface", surface)
            mapped("colors.surfaceVariant", surfaceVariant)
            mapped("colors.surfaceContainerLow", surfaceContainerLow)
            mapped("colors.surfaceContainer", surfaceContainer)
            mapped("colors.surfaceContainerHigh", surfaceContainerHigh)
            mapped("colors.surfaceContainerHighest", surfaceContainerHighest)
            mapped("colors.onSurface", onSurface)
            mapped("colors.onSurfaceVariant", onSurfaceVariant)
            mapped("colors.primary", primary)
            mapped("colors.onPrimary", onPrimary)
            mapped("colors.primaryContainer", primaryContainer)
            mapped("colors.onPrimaryContainer", onPrimaryContainer)
            mapped("colors.secondaryContainer", secondaryContainer)
            mapped("colors.onSecondaryContainer", onSecondaryContainer)
            mapped("colors.error", error)
            mapped("colors.errorContainer", errorContainer)
            mapped("colors.outline", outline)
            mapped("colors.outlineVariant", outlineVariant)
            mapped("stateColors.primaryText", primaryText)
            mapped("stateColors.secondaryText", secondaryText)
            mapped("stateColors.control", control)
            mapped("stateColors.controlActivated", controlActivated)
        }
        with(snapshot.typography) {
            mapped("typography.bodyMedium", bodyMedium)
            mapped("typography.bodySmall", bodySmall)
            mapped("typography.labelLarge", labelLarge)
            mapped("typography.labelMedium", labelMedium)
            mapped("typography.labelSmall", labelSmall)
        }
        with(snapshot.shapes) {
            mapped("shapes.extraSmall", extraSmall)
            mapped("shapes.small", small)
            mapped("shapes.medium", medium)
            mapped("shapes.large", large)
            mapped("shapes.extraLarge", extraLarge)
        }
        mapped("overlays.scrimOpacity", snapshot.scrimOpacity)
        return UiTokenProvenance(
            sourceId = when (sourceOrigin) {
                UiThemeOrigin.AndroidDynamicColor -> "viewcompose-material3/android-dynamic"
                else -> "viewcompose-material3/android-xml"
            },
            defaultOrigin = UiThemeOrigin.FrameworkDefault,
            tokenOrigins = mapped,
        )
    }

    /**
     * Builds tokens from attribute reader functions for tests or lightweight bridges.
     */
    fun fromThemeColors(
        readColor: (Int) -> Int?,
        readTextSizeSp: (Int) -> UiSp? = { null },
        readStateColor: (Int) -> UiStateColor? = { null },
        readScrimOpacity: () -> Float? = { null },
        isDarkMode: Boolean = false,
    ): UiThemeTokens {
        val fallback = if (isDarkMode) Material3ThemeDefaults.dark() else Material3ThemeDefaults.light()
        val colors = UiColors(
            background = readColor(android.R.attr.colorBackground) ?: fallback.colors.background,
            onBackground = readColor(com.google.android.material.R.attr.colorOnBackground)
                ?: fallback.colors.onBackground,
            surface = readColor(com.google.android.material.R.attr.colorSurface) ?: fallback.colors.surface,
            surfaceVariant = readColor(com.google.android.material.R.attr.colorSurfaceVariant)
                ?: fallback.colors.surfaceVariant,
            surfaceDim = readColor(com.google.android.material.R.attr.colorSurfaceDim)
                ?: fallback.colors.surfaceDim,
            surfaceBright = readColor(com.google.android.material.R.attr.colorSurfaceBright)
                ?: fallback.colors.surfaceBright,
            surfaceContainerLowest =
                readColor(com.google.android.material.R.attr.colorSurfaceContainerLowest)
                    ?: fallback.colors.surfaceContainerLowest,
            surfaceContainerLow = readColor(com.google.android.material.R.attr.colorSurfaceContainerLow)
                ?: fallback.colors.surfaceContainerLow,
            surfaceContainer = readColor(com.google.android.material.R.attr.colorSurfaceContainer)
                ?: fallback.colors.surfaceContainer,
            surfaceContainerHigh = readColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
                ?: fallback.colors.surfaceContainerHigh,
            surfaceContainerHighest =
                readColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                    ?: fallback.colors.surfaceContainerHighest,
            onSurface = readColor(com.google.android.material.R.attr.colorOnSurface)
                ?: readColor(android.R.attr.textColorPrimary)
                ?: fallback.colors.onSurface,
            onSurfaceVariant = readColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                ?: readColor(android.R.attr.textColorSecondary)
                ?: fallback.colors.onSurfaceVariant,
            primary = readColor(androidx.appcompat.R.attr.colorPrimary) ?: fallback.colors.primary,
            onPrimary = readColor(com.google.android.material.R.attr.colorOnPrimary)
                ?: fallback.colors.onPrimary,
            primaryContainer = readColor(com.google.android.material.R.attr.colorPrimaryContainer)
                ?: fallback.colors.primaryContainer,
            onPrimaryContainer = readColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
                ?: fallback.colors.onPrimaryContainer,
            secondary = readColor(com.google.android.material.R.attr.colorSecondary)
                ?: fallback.colors.secondary,
            onSecondary = readColor(com.google.android.material.R.attr.colorOnSecondary)
                ?: fallback.colors.onSecondary,
            secondaryContainer = readColor(com.google.android.material.R.attr.colorSecondaryContainer)
                ?: fallback.colors.secondaryContainer,
            onSecondaryContainer = readColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
                ?: fallback.colors.onSecondaryContainer,
            tertiary = readColor(com.google.android.material.R.attr.colorTertiary)
                ?: fallback.colors.tertiary,
            onTertiary = readColor(com.google.android.material.R.attr.colorOnTertiary)
                ?: fallback.colors.onTertiary,
            tertiaryContainer = readColor(com.google.android.material.R.attr.colorTertiaryContainer)
                ?: fallback.colors.tertiaryContainer,
            onTertiaryContainer = readColor(com.google.android.material.R.attr.colorOnTertiaryContainer)
                ?: fallback.colors.onTertiaryContainer,
            error = readColor(android.R.attr.colorError) ?: fallback.colors.error,
            onError = readColor(com.google.android.material.R.attr.colorOnError) ?: fallback.colors.onError,
            errorContainer = readColor(com.google.android.material.R.attr.colorErrorContainer)
                ?: fallback.colors.errorContainer,
            onErrorContainer = readColor(com.google.android.material.R.attr.colorOnErrorContainer)
                ?: fallback.colors.onErrorContainer,
            success = fallback.colors.success,
            warning = fallback.colors.warning,
            info = fallback.colors.info,
            outline = readColor(com.google.android.material.R.attr.colorOutline) ?: fallback.colors.outline,
            outlineVariant = readColor(com.google.android.material.R.attr.colorOutlineVariant)
                ?: fallback.colors.outlineVariant,
            surfaceTint = readColor(androidx.appcompat.R.attr.colorPrimary)
                ?: fallback.colors.surfaceTint,
            inverseSurface = readColor(com.google.android.material.R.attr.colorSurfaceInverse)
                ?: fallback.colors.inverseSurface,
            inverseOnSurface = readColor(com.google.android.material.R.attr.colorOnSurfaceInverse)
                ?: fallback.colors.inverseOnSurface,
            inversePrimary = readColor(com.google.android.material.R.attr.colorPrimaryInverse)
                ?: fallback.colors.inversePrimary,
            scrim = fallback.colors.scrim,
        )
        val fallbackStateColors = UiStateColorDefaults.from(colors)
        return UiThemeTokens(
            colors = colors,
            stateColors = fallbackStateColors.copy(
                primaryText = readStateColor(android.R.attr.textColorPrimary)
                    ?: fallbackStateColors.primaryText,
                secondaryText = readStateColor(android.R.attr.textColorSecondary)
                    ?: fallbackStateColors.secondaryText,
                control = readStateColor(androidx.appcompat.R.attr.colorControlNormal)
                    ?: fallbackStateColors.control,
                controlActivated = readStateColor(androidx.appcompat.R.attr.colorControlActivated)
                    ?: fallbackStateColors.controlActivated,
            ),
            typography = fallback.typography.copy(
                titleMedium = fallback.typography.titleMedium.copy(
                    fontSizeSp = readTextSizeSp(android.R.attr.textAppearanceLarge)
                        ?: fallback.typography.titleMedium.fontSizeSp,
                ),
                bodyMedium = fallback.typography.bodyMedium.copy(
                    fontSizeSp = readTextSizeSp(android.R.attr.textAppearanceMedium)
                        ?: fallback.typography.bodyMedium.fontSizeSp,
                ),
                labelMedium = fallback.typography.labelMedium.copy(
                    fontSizeSp = readTextSizeSp(android.R.attr.textAppearanceSmall)
                        ?: fallback.typography.labelMedium.fontSizeSp,
                ),
            ),
            shapes = fallback.shapes,
            controls = fallback.controls,
            interactions = fallback.interactions,
            overlays = UiOverlays(
                scrimOpacity = readScrimOpacity() ?: fallback.overlays.scrimOpacity,
            ),
            metadata = UiThemeMetadata(
                origin = UiThemeOrigin.AndroidTheme,
                isDark = isDarkMode,
                provenance = UiTokenProvenance(
                    sourceId = "viewcompose-material3/android-reader",
                    defaultOrigin = UiThemeOrigin.AndroidTheme,
                ),
            ),
        )
    }
}

/**
 * Resolves textSize from a textAppearance resource and converts it to sp.
 */
private fun Context.resolveTextAppearanceTextSizeSp(
    textAppearanceAttr: Int,
): UiSp? {
    val ta: TypedArray = obtainStyledAttributes(intArrayOf(textAppearanceAttr))
    val resId = try {
        if (ta.hasValue(0)) ta.getResourceId(0, 0) else 0
    } finally {
        ta.recycle()
    }
    if (resId == 0) return null

    val attrs: TypedArray = obtainStyledAttributes(resId, intArrayOf(android.R.attr.textSize))
    val px = try {
        if (attrs.hasValue(0)) attrs.getDimensionPixelSize(0, 0) else -1
    } finally {
        attrs.recycle()
    }
    if (px <= 0) return null

    val density = resources.displayMetrics.density
    val fontScale = resources.configuration.fontScale.takeIf { it > 0f } ?: 1f
    return UiSp(px / (density * fontScale))
}

/**
 * Merges an Android text-style snapshot with framework fallback.
 */
private fun resolveTextStyle(
    snapshot: Material3TextStyleSnapshot?,
    fallback: UiTextStyle,
): UiTextStyle {
    return UiTextStyle(
        fontSizeSp = snapshot?.fontSizeSp ?: fallback.fontSizeSp,
        fontWeight = snapshot?.fontWeight ?: fallback.fontWeight,
        fontFamily = snapshot?.fontFamily ?: fallback.fontFamily,
        letterSpacingEm = snapshot?.letterSpacingEm ?: fallback.letterSpacingEm,
        lineHeightSp = snapshot?.lineHeightSp ?: fallback.lineHeightSp,
        includeFontPadding = snapshot?.includeFontPadding ?: fallback.includeFontPadding,
        textDecoration = fallback.textDecoration,
    )
}
