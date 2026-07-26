package com.viewcompose.text

enum class ReceiveContentSource {
    Clipboard,
    DragAndDrop,
    InputMethod,
    Autofill,
    Application,
    Unknown,
}

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

fun interface ReceiveContentTransformation {
    /**
     * Returns the document to insert, or `null` to reject the entire received payload.
     */
    fun transform(content: ReceivedContent): TextDocument?
}

class ReceiveContentConfiguration(
    mimeTypes: Set<String> = DefaultMimeTypes,
    val transformation: ReceiveContentTransformation? = null,
) {
    val mimeTypes: Set<String> = mimeTypes.toSet()

    init {
        require(this.mimeTypes.isNotEmpty()) {
            "Receive Content must accept at least one MIME type."
        }
        require(this.mimeTypes.none(String::isBlank)) {
            "Receive Content MIME types must not be blank."
        }
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (
            other is ReceiveContentConfiguration &&
                mimeTypes == other.mimeTypes &&
                transformation === other.transformation
            )
    }

    override fun hashCode(): Int {
        return 31 * mimeTypes.hashCode() + System.identityHashCode(transformation)
    }

    companion object {
        val DefaultMimeTypes: Set<String> = linkedSetOf(
            "text/*",
            "image/*",
        )

        val Default: ReceiveContentConfiguration = ReceiveContentConfiguration()
    }
}
