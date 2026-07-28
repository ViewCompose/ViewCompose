package com.viewcompose.widget.core

/**
 * 表示平台层已经展示的对话框实例，支持原地更新和关闭。
 * Represents a platform dialog instance that can be updated in place or dismissed.
 */
interface DialogOverlayHandle {
    fun update(
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    )

    fun dismiss()
}

/**
 * 由平台实现的对话框展示入口，widget-core 只依赖这个最小契约。
 * Platform-provided dialog presentation entry point; widget-core depends only on this minimal contract.
 */
interface DialogOverlayPresenter {
    fun show(
        entryId: OverlayEntryId,
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ): DialogOverlayHandle
}

/**
 * 将声明式 overlay 请求同步到对话框 presenter，并按 session/key 维护生命周期。
 * Synchronizes declarative overlay requests to a dialog presenter and owns lifecycle by session/key.
 */
class DialogOverlayHost(
    private val presenter: DialogOverlayPresenter,
) : SessionBoundSurfaceOverlayHost<DialogOverlaySpec, DialogOverlayContent, DialogOverlayHandle>(
    overlayType = OverlayType.Dialog,
    decode = { request ->
        val spec = request.payload as? DialogOverlaySpec
        val content = request.contentToken as? DialogOverlayContent
        if (spec == null || content == null) {
            null
        } else {
            spec to content
        }
    },
) {
    override fun onShow(
        entryId: OverlayEntryId,
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ): DialogOverlayHandle = presenter.show(entryId, spec, content)

    override fun onUpdate(
        handle: DialogOverlayHandle,
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ) {
        handle.update(spec, content)
    }

    override fun onDismiss(handle: DialogOverlayHandle) {
        handle.dismiss()
    }
}
