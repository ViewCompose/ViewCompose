package com.viewcompose.host.android.resources

import android.content.res.Resources
import com.viewcompose.ui.unit.UiDp

/**
 * Returns the string resolved for [id] by the current themed Android resource environment.
 *
 * The result is a snapshot for the current resource revision. Configuration or imperative host
 * refresh invalidates the calling composition so a later render resolves it again.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android string resource identifier
 * @return resolved immutable String value
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not a string resource
 */
fun stringResource(id: Int): String = requireAndroidResourceContext().getString(id)

/**
 * Returns the formatted string resolved for [id] by the current themed resource environment.
 *
 * Android applies the resource's active locale and standard `Formatter` rules. The supplied
 * arguments are consumed synchronously and are not retained by ViewCompose.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android formatted-string resource identifier
 * @param formatArgs values substituted into the resource format in declaration order
 * @return resolved and formatted immutable String value
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not a string resource
 * @throws java.util.IllegalFormatException when the resource format and arguments are incompatible
 */
fun stringResource(id: Int, vararg formatArgs: Any): String {
    return requireAndroidResourceContext().getString(id, *formatArgs)
}

/**
 * Returns the locale-qualified plural string for [quantity] and formats it with [formatArgs].
 *
 * Android uses [quantity] to select the quantity rule; include it in [formatArgs] when the rendered
 * message must display the number. Arguments are consumed synchronously and are not retained.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android plurals resource identifier
 * @param quantity value used to select the locale-specific quantity rule
 * @param formatArgs values substituted into the selected resource format
 * @return resolved and formatted immutable String value
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not a plurals resource
 * @throws java.util.IllegalFormatException when the selected format and arguments are incompatible
 */
fun pluralStringResource(
    id: Int,
    quantity: Int,
    vararg formatArgs: Any,
): String {
    return requireAndroidResourceContext().resources.getQuantityString(id, quantity, *formatArgs)
}

/**
 * Returns the ARGB color resolved for [id] with the current Android theme.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android color resource identifier
 * @return resolved packed ARGB color integer
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not a color resource
 */
fun colorResource(id: Int): Int = requireAndroidResourceContext().getColor(id)

/**
 * Returns the current resource-qualified dimension as a logical [UiDp] distance.
 *
 * Android first resolves the resource to physical pixels, including density and any unit encoded
 * by the resource. ViewCompose then divides by the same resource density so the renderer can apply
 * its captured density exactly once. For exact Android pixel rounding, use
 * [dimensionPixelSizeResource].
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android dimension resource identifier
 * @return resolved logical density-independent distance
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not a dimension resource
 */
fun dimensionResource(id: Int): UiDp {
    val resources = LocalAndroidResources.current
    return UiDp(resources.getDimension(id) / resources.displayMetrics.density)
}

/**
 * Returns the current resource-qualified dimension rounded to Android physical pixels.
 *
 * The result follows `Resources.getDimensionPixelSize`, including Android's non-zero rounding
 * behavior for non-zero dimensions smaller than one pixel.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android dimension resource identifier
 * @return rounded physical-pixel size
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not a dimension resource
 */
fun dimensionPixelSizeResource(id: Int): Int = LocalAndroidResources.current.getDimensionPixelSize(id)

/**
 * Returns the boolean resolved for [id] by the current Android resources.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android boolean resource identifier
 * @return resolved boolean value
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not a boolean resource
 */
fun booleanResource(id: Int): Boolean = LocalAndroidResources.current.getBoolean(id)

/**
 * Returns the integer resolved for [id] by the current Android resources.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android integer resource identifier
 * @return resolved integer value
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not an integer resource
 */
fun integerResource(id: Int): Int = LocalAndroidResources.current.getInteger(id)

/**
 * Returns an immutable snapshot of the string array resolved for [id].
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android string-array resource identifier
 * @return caller-owned immutable List snapshot in resource order
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not a string-array resource
 */
fun stringArrayResource(id: Int): List<String> = LocalAndroidResources.current.getStringArray(id).toList()

/**
 * Returns a caller-owned snapshot of the integer array resolved for [id].
 *
 * @sample com.viewcompose.host.android.samples.androidResourceLookupSample
 * @param id Android integer-array resource identifier
 * @return newly resolved IntArray in resource order
 * @throws IllegalStateException when no Android resource environment is active
 * @throws Resources.NotFoundException when [id] is not an integer-array resource
 */
fun integerArrayResource(id: Int): IntArray = LocalAndroidResources.current.getIntArray(id)
