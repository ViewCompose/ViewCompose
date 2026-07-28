package com.viewcompose.widget.core

/**
 * 表示平台层已展示的模态底部面板实例。
 * Represents a platform modal bottom sheet instance currently shown.
 */
interface ModalBottomSheetOverlayHandle {
    fun update(
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    )

    fun dismiss()
}

/**
 * 由平台实现的底部面板展示入口，负责创建真实 UI 容器。
 * Platform-provided bottom sheet presenter responsible for creating the real UI container.
 */
interface ModalBottomSheetOverlayPresenter {
    fun show(
        entryId: OverlayEntryId,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ): ModalBottomSheetOverlayHandle
}

/**
 * 将声明式底部面板 overlay 请求同步到平台 presenter，并复用相同 entry 的 handle。
 * Synchronizes declarative bottom sheet overlay requests to the platform presenter and reuses handles for the same entry.
 */
class ModalBottomSheetOverlayHost(
    private val presenter: ModalBottomSheetOverlayPresenter,
) : SessionBoundSurfaceOverlayHost<
    ModalBottomSheetOverlaySpec,
    ModalBottomSheetOverlayContent,
    ModalBottomSheetOverlayHandle,
>(
    overlayType = OverlayType.ModalBottomSheet,
    decode = { request ->
        val spec = request.payload as? ModalBottomSheetOverlaySpec
        val content = request.contentToken as? ModalBottomSheetOverlayContent
        if (spec == null || content == null) {
            null
        } else {
            spec to content
        }
    },
) {
    override fun onShow(
        entryId: OverlayEntryId,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ): ModalBottomSheetOverlayHandle = presenter.show(entryId, spec, content)

    override fun onUpdate(
        handle: ModalBottomSheetOverlayHandle,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ) {
        handle.update(spec, content)
    }

    override fun onDismiss(handle: ModalBottomSheetOverlayHandle) {
        handle.dismiss()
    }
}
