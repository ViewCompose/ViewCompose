package com.viewcompose.text

/**
 * 文本字段接收外部内容的来源。
 * Source from which a text field received external content.
 */
enum class ReceiveContentSource {
    Clipboard,
    DragAndDrop,
    InputMethod,
    Autofill,
    Application,
    Unknown,
}

/**
 * 平台 receive-content payload 归一化后的框架模型。
 * Framework model normalized from a platform receive-content payload.
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

fun interface ReceiveContentTransformation {
    /**
     * 返回要插入的文档；返回 `null` 表示拒绝整个 payload。
     * Returns the document to insert, or `null` to reject the entire received payload.
     */
    fun transform(content: ReceivedContent): TextDocument?
}

/**
 * 文本字段 receive-content 的 MIME 白名单和可选转换器。
 * MIME allowlist and optional transformer for text-field receive-content.
 */
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
