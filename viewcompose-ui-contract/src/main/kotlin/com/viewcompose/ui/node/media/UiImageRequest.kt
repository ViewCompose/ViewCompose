package com.viewcompose.ui.node

import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.UiDp

/** Selects the logical bounds a loader uses while decoding an image. */
sealed interface UiImageDecodeSize {
    /** Resolves decode bounds from the measured target. */
    data object Target : UiImageDecodeSize

    /** Uses the source's intrinsic pixel bounds without target-sized downsampling. */
    data object Original : UiImageDecodeSize

    /**
     * Uses explicit positive density-independent decode bounds.
     *
     * The loader converts these logical dimensions with the [UiImageRequest.density] captured by the
     * renderer and keeps every positive dimension at least one physical pixel. They select
     * decode/downsample work; they do not replace measured layout bounds or [ImageContentScale]
     * behavior.
     *
     * @property width requested logical decoded width
     * @property height requested logical decoded height
     * @throws IllegalArgumentException if either dimension is non-finite or not positive
     */
    data class Fixed(
        val width: UiDp,
        val height: UiDp,
    ) : UiImageDecodeSize {
        init {
            require(width.value > 0f && width.value.isFinite()) {
                "UiImageDecodeSize.Fixed.width must be finite and positive."
            }
            require(height.value > 0f && height.value.isFinite()) {
                "UiImageDecodeSize.Fixed.height must be finite and positive."
            }
        }
    }
}

/** Common cache policy understood by image-loader adapters. */
enum class UiImageCachePolicy {
    /** Use the loader's configured cache behavior. */
    Default,

    /** Do not read or write this cache for the request. */
    Disabled,
}

/** Common transition behavior understood by image-loader adapters. */
sealed interface UiImageTransition {
    /** Use the loader's configured transition behavior. */
    data object Default : UiImageTransition

    /** Disable request transitions. */
    data object None : UiImageTransition

    /**
     * Crossfade the loaded result for a non-negative duration.
     *
     * @property durationMillis transition duration in milliseconds
     */
    data class Crossfade(
        val durationMillis: Int,
    ) : UiImageTransition {
        init {
            require(durationMillis >= 0) {
                "UiImageTransition.Crossfade.durationMillis must be non-negative."
            }
        }
    }
}

/**
 * Supplies one immutable adapter-owned option with an explicit stable identity.
 *
 * Request equality uses the concrete extension type plus [stableKey], never the extension object's
 * `equals` implementation or diagnostic string. An adapter consumes concrete extension types it
 * publishes and ignores types it does not recognize. Implementations must keep their option values
 * immutable and change [stableKey] whenever those values change; mutable builders and unkeyed
 * function captures are not valid extensions.
 */
interface UiImageRequestExtension {
    /** Immutable identity for this concrete extension type. */
    val stableKey: Any
}

/**
 * Small cross-loader option set carried by an image node.
 *
 * The extension order is significant and participates in equality. The supplied collection is
 * defensively copied when this value is created.
 *
 * @sample com.viewcompose.ui.samples.uiImageRequestSample
 * @property decodeSize desired logical decode bounds; defaults to the measured target
 * @property memoryCachePolicy memory-cache behavior
 * @property diskCachePolicy disk-cache behavior
 * @property transition display transition behavior
 * @property extensions ordered adapter-owned immutable extensions
 */
class UiImageRequestOptions(
    val decodeSize: UiImageDecodeSize = UiImageDecodeSize.Target,
    val memoryCachePolicy: UiImageCachePolicy = UiImageCachePolicy.Default,
    val diskCachePolicy: UiImageCachePolicy = UiImageCachePolicy.Default,
    val transition: UiImageTransition = UiImageTransition.Default,
    extensions: List<UiImageRequestExtension> = emptyList(),
) {
    val extensions: List<UiImageRequestExtension> = extensions.toList()
    private val extensionIdentities: List<ExtensionIdentity> = this.extensions.map { extension ->
        ExtensionIdentity(
            type = extension::class.java,
            stableKey = extension.stableKey,
        )
    }

    /** Compares all common policies and the ordered extension identity list. */
    override fun equals(other: Any?): Boolean {
        return other is UiImageRequestOptions &&
            decodeSize == other.decodeSize &&
            memoryCachePolicy == other.memoryCachePolicy &&
            diskCachePolicy == other.diskCachePolicy &&
            transition == other.transition &&
            extensionIdentities == other.extensionIdentities
    }

    /** Hashes all common policies and the ordered extension identity list. */
    override fun hashCode(): Int {
        var result = decodeSize.hashCode()
        result = 31 * result + memoryCachePolicy.hashCode()
        result = 31 * result + diskCachePolicy.hashCode()
        result = 31 * result + transition.hashCode()
        result = 31 * result + extensionIdentities.hashCode()
        return result
    }

    /** Returns a deterministic diagnostic representation of the option values. */
    override fun toString(): String {
        return "UiImageRequestOptions(" +
            "decodeSize=$decodeSize, " +
            "memoryCachePolicy=$memoryCachePolicy, " +
            "diskCachePolicy=$diskCachePolicy, " +
            "transition=$transition, " +
            "extensions=$extensionIdentities)"
    }

    private data class ExtensionIdentity(
        val type: Class<*>,
        val stableKey: Any,
    ) {
        override fun toString(): String {
            return "${type.name}(stableKey=$stableKey)"
        }
    }
}

/**
 * Normalized request passed from a renderer to a [UiImageLoader].
 *
 * `source` is always non-null. The renderer handles an absent source and its fallback before a
 * request is created, so loaders receive no asynchronous fallback request chain.
 *
 * @sample com.viewcompose.ui.samples.uiImageRequestSample
 * @property source non-null primary image source
 * @property placeholder resource displayed while loading, or `null`
 * @property error resource displayed after a load failure, or `null`
 * @property options common cache, decode, transition, and extension options
 * @property contentScale renderer display scale supplied to the adapter for decode/transform choice
 * @property density captured renderer density used to resolve logical decode dimensions
 */
data class UiImageRequest(
    val source: ImageSource,
    val placeholder: ImageSource.Resource? = null,
    val error: ImageSource.Resource? = null,
    val options: UiImageRequestOptions = UiImageRequestOptions(),
    val contentScale: ImageContentScale = ImageContentScale.Fit,
    val density: UiDensity = UiDensity.Default,
)
