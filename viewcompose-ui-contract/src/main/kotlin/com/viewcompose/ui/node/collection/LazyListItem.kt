package com.viewcompose.ui.node

class LazyListItem(
    val key: Any?,
    val contentToken: Any?,
    val contentType: Any? = null,
    val kind: LazyListItemKind = LazyListItemKind.Item,
    val span: Int = 1,
    val sessionFactory: LazyListItemSessionFactory,
    val sessionUpdater: ((LazyListItemSession) -> Unit)? = null,
) {
    init {
        require(span > 0) { "Lazy item span must be greater than zero." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LazyListItem) return false
        return key == other.key &&
            contentToken == other.contentToken &&
            contentType == other.contentType &&
            kind == other.kind &&
            span == other.span
    }

    override fun hashCode(): Int {
        var result = key?.hashCode() ?: 0
        result = 31 * result + (contentToken?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + kind.hashCode()
        result = 31 * result + span
        return result
    }
}

enum class LazyListItemKind {
    Item,
    StickyHeader,
}

fun interface LazyListItemSessionFactory {
    fun create(container: RenderContainerHandle): LazyListItemSession
}

interface LazyListItemSession {
    fun render()
    fun dispose()
}

interface RenderContainerHandle

val RenderContainerHandle.nativeContainer: Any
    get() = (this as PlatformRenderContainerHandle).container

interface PlatformRenderContainerHandle : RenderContainerHandle {
    val container: Any
}
