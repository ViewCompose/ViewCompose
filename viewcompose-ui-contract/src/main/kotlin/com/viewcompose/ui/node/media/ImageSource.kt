package com.viewcompose.ui.node

/**
 * Selects the primary data for an image request.
 *
 * Renderers keep `null` outside this hierarchy to represent absent content. A non-null source is
 * therefore always a request that either succeeds or fails; it is never treated as absent data.
 */
sealed interface ImageSource {
    /**
     * Loads an Android drawable resource from the rendered View context.
     *
     * @property resId Android drawable resource identifier
     */
    data class Resource(
        val resId: Int,
    ) : ImageSource {
        init {
            require(resId > 0) { "ImageSource.Resource.resId must be positive." }
        }
    }

    /**
     * Loads a URL through the configured image loader.
     *
     * Use [Uri] for non-HTTP schemes and [Model] for adapter-owned URL models. Blank, relative,
     * malformed, and non-HTTP values are rejected so a non-null source cannot silently select a
     * fallback or change model interpretation between adapters.
     *
     * @property url absolute HTTP or HTTPS URL
     * @throws IllegalArgumentException if [url] is not an absolute HTTP or HTTPS URL
     */
    data class Url(
        val url: String,
    ) : ImageSource {
        init {
            require(url.isNotBlank()) { "ImageSource.Url.url must not be blank." }
            val parsed = url.requireAbsoluteUri("ImageSource.Url.url")
            require(
                parsed.scheme.equals("http", ignoreCase = true) ||
                    parsed.scheme.equals("https", ignoreCase = true),
            ) {
                "ImageSource.Url.url must use the http or https scheme."
            }
            require(!parsed.isOpaque && !parsed.host.isNullOrBlank()) {
                "ImageSource.Url.url must be an absolute HTTP or HTTPS URL."
            }
        }
    }

    /**
     * Loads a URI represented without importing a platform URI type into the contract module.
     *
     * @property uri absolute URI using a loader-supported scheme
     * @throws IllegalArgumentException if [uri] is blank, malformed, or relative
     */
    data class Uri(
        val uri: String,
    ) : ImageSource {
        init {
            require(uri.isNotBlank()) { "ImageSource.Uri.uri must not be blank." }
            uri.requireAbsoluteUri("ImageSource.Uri.uri")
        }
    }

    /**
     * Loads a local file through the configured image loader.
     *
     * The file remains caller-owned; the loader and renderer must not delete, close, or mutate it.
     *
     * @property file local file model
     */
    data class File(
        val file: java.io.File,
    ) : ImageSource {
        init {
            require(file.path.isNotBlank()) { "ImageSource.File.file must have a non-blank path." }
        }
    }

    /**
     * Carries an adapter-owned model with an explicit stable identity.
     *
     * Equality and hashing use only the immutable runtime type discriminator of [value] and
     * [stableKey]. The payload is retained but never traversed for identity, and it is never closed,
     * recycled, mutated, or otherwise owned by the image pipeline. Callers must change [stableKey]
     * whenever the represented bytes or load behavior changes. Use a stable key for mutable byte
     * containers, one-shot streams, authenticated models, or other values whose contents are not
     * suitable as identity.
     *
     * @property value raw adapter-specific model, retained without ownership transfer
     * @property stableKey immutable identity chosen by the caller
     */
    class Model(
        val value: Any,
        val stableKey: Any,
    ) : ImageSource {
        private val typeDiscriminator: Class<*> = value::class.java

        /** Compares the type discriminator and stable key without traversing [value]. */
        override fun equals(other: Any?): Boolean {
            return other is Model &&
                typeDiscriminator == other.typeDiscriminator &&
                stableKey == other.stableKey
        }

        /** Hashes the type discriminator and stable key without traversing [value]. */
        override fun hashCode(): Int {
            return 31 * typeDiscriminator.hashCode() + stableKey.hashCode()
        }

        /** Redacts the payload while retaining the identity fields useful for diagnostics. */
        override fun toString(): String {
            return "Model(type=${typeDiscriminator.name}, stableKey=$stableKey)"
        }
    }
}

private fun String.requireAbsoluteUri(field: String): java.net.URI {
    val parsed = try {
        java.net.URI(this)
    } catch (_: java.net.URISyntaxException) {
        throw IllegalArgumentException("$field must be an absolute URI.")
    }
    require(parsed.isAbsolute) { "$field must be an absolute URI." }
    return parsed
}
