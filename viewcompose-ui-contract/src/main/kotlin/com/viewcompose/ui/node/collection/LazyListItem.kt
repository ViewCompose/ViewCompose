package com.viewcompose.ui.node

/**
 * 带 key 的 lazy 容器 item。
 * One keyed lazy-container item.
 *
 * [contentToken] 是渲染内容的语义版本。item content 捕获的值变化时它必须变化。
 * 相等 token 允许 renderer 刷新最新 content closure，而不必同步重绘已绑定 item。
 * [contentToken] is the semantic version of the rendered content. It must change when values
 * captured by the item content change. An equal token allows the renderer to refresh the latest
 * content closure without synchronously redrawing an already-bound item.
 */
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
