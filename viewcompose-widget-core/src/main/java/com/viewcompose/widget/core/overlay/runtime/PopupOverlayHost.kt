package com.viewcompose.widget.core

/**
 * 表示平台层已展示的 popup 实例，host 通过它执行 update/dismiss。
 * Represents a platform popup instance used by the host for update and dismiss operations.
 */
interface PopupOverlayHandle {
    fun update(
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    )

    fun dismiss()
}

/**
 * 由平台实现的 popup 展示入口，接收已计算好的声明式规格与内容 token。
 * Platform-provided popup presenter that receives declarative specs and content tokens.
 */
interface PopupOverlayPresenter {
    fun show(
        entryId: OverlayEntryId,
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    ): PopupOverlayHandle
}

/**
 * 将 popup overlay 请求同步到平台 presenter，并过滤非 popup 类型的请求。
 * Synchronizes popup overlay requests to the platform presenter and filters out non-popup requests.
 */
class PopupOverlayHost(
    private val presenter: PopupOverlayPresenter,
) : SessionBoundSurfaceOverlayHost<PopupOverlaySpec, PopupOverlayContent, PopupOverlayHandle>(
    overlayType = OverlayType.Popup,
    decode = { request ->
        val spec = request.payload as? PopupOverlaySpec
        val content = request.contentToken as? PopupOverlayContent
        if (spec == null || content == null) {
            null
        } else {
            spec to content
        }
    },
) {
    override fun onShow(
        entryId: OverlayEntryId,
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    ): PopupOverlayHandle = presenter.show(entryId, spec, content)

    override fun onUpdate(
        handle: PopupOverlayHandle,
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    ) {
        handle.update(spec, content)
    }

    override fun onDismiss(handle: PopupOverlayHandle) {
        handle.dismiss()
    }
}
