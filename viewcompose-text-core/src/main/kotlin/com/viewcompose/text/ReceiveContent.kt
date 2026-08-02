package com.viewcompose.text

/** Source category assigned to content entering a text field from outside ordinary typing. */
enum class ReceiveContentSource {
    Clipboard,
    DragAndDrop,
    InputMethod,
    Autofill,
    Application,
    Unknown,
}

/**
 * Immutable, platform-neutral content normalized from one external receive-content payload.
 *
 * @property document text, styles, paragraphs, and inline attachments proposed for insertion
 * @property source channel through which the content entered the application
 * @property mimeTypes MIME types represented by the normalized payload
 * @property platformItemCount number of original platform items; always greater than zero
 */
data class ReceivedContent(
    val document: TextDocument,
    val source: ReceiveContentSource,
    val mimeTypes: Set<String>,
    val platformItemCount: Int,
) {
    init {
        require(platformItemCount > 0) { "platformItemCount must be greater than zero." }
    }
}

/** Decides whether and how normalized external content is inserted into a text field. */
fun interface ReceiveContentTransformation {
    /**
     * Returns the document to insert, or `null` to reject the complete payload.
     *
     * The transformation runs synchronously and should not retain [content].
     */
    fun transform(content: ReceivedContent): TextDocument?
}

/**
 * Defines the accepted MIME patterns and optional conversion policy for receive-content.
 *
 * MIME values are copied on construction and compared structurally. The transformation is compared
 * by identity because lambdas do not have stable value equality.
 *
 * @property transformation optional policy applied after platform normalization
 */
class ReceiveContentConfiguration(
    mimeTypes: Set<String> = DefaultMimeTypes,
    val transformation: ReceiveContentTransformation? = null,
) {
    /** Immutable, non-empty set of accepted MIME types or wildcard patterns. */
    val mimeTypes: Set<String> = mimeTypes.toSet()

    init {
        require(this.mimeTypes.isNotEmpty()) {
            "Receive Content must accept at least one MIME type."
        }
        require(this.mimeTypes.none(String::isBlank)) {
            "Receive Content MIME types must not be blank."
        }
    }

    /** Uses structural MIME equality and transformation identity. */
    override fun equals(other: Any?): Boolean {
        return this === other || (
            other is ReceiveContentConfiguration &&
                mimeTypes == other.mimeTypes &&
                transformation === other.transformation
            )
    }

    /** Combines structural MIME hashing with transformation identity. */
    override fun hashCode(): Int {
        return 31 * mimeTypes.hashCode() + System.identityHashCode(transformation)
    }

    /** Default receive-content policy values. */
    companion object {
        /** Default allowlist accepting text and image payloads. */
        val DefaultMimeTypes: Set<String> = linkedSetOf(
            "text/*",
            "image/*",
        )

        /** Default configuration with [DefaultMimeTypes] and no transformation. */
        val Default: ReceiveContentConfiguration = ReceiveContentConfiguration()
    }
}
