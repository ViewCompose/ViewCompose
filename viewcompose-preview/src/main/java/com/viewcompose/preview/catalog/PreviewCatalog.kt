package com.viewcompose.preview.catalog

import com.viewcompose.preview.catalog.domain.CollectionPreviewSpecs
import com.viewcompose.preview.catalog.domain.ContainerPreviewSpecs
import com.viewcompose.preview.catalog.domain.ContentPreviewSpecs
import com.viewcompose.preview.catalog.domain.AnimationPreviewSpecs
import com.viewcompose.preview.catalog.domain.FeedbackPreviewSpecs
import com.viewcompose.preview.catalog.domain.GesturePreviewSpecs
import com.viewcompose.preview.catalog.domain.GraphicsPreviewSpecs
import com.viewcompose.preview.catalog.domain.InputPreviewSpecs
import com.viewcompose.preview.catalog.domain.ModifierPreviewSpecs
import com.viewcompose.preview.catalog.domain.NavigationPreviewSpecs
import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec

/**
 * 静态预览用例的集中索引。
 * Central index for static preview specs.
 *
 * 新增组件领域时先在这里接入，Preview 参数提供器和快照测试都会消费同一份列表。
 * Add new component domains here first; Preview parameter providers and snapshot tests share this list.
 */
internal object PreviewCatalog {
    /**
     * 按领域顺序展开的全部预览用例。
     * All preview specs expanded in domain order.
     */
    val specs: List<PreviewSpec> by lazy {
        buildList {
            addAll(ContentPreviewSpecs.all)
            addAll(InputPreviewSpecs.all)
            addAll(ContainerPreviewSpecs.all)
            addAll(CollectionPreviewSpecs.all)
            addAll(NavigationPreviewSpecs.all)
            addAll(FeedbackPreviewSpecs.all)
            addAll(ModifierPreviewSpecs.all)
            addAll(AnimationPreviewSpecs.all)
            addAll(GesturePreviewSpecs.all)
            addAll(GraphicsPreviewSpecs.all)
        }
    }

    /**
     * id 到 spec 的懒加载索引，用于 PreviewParameterProvider 的轻量引用回查。
     * Lazy id-to-spec index used to resolve lightweight refs from PreviewParameterProvider.
     */
    private val specsById: Map<String, PreviewSpec> by lazy {
        specs.associateBy(PreviewSpec::id)
    }

    /**
     * 返回指定领域下的预览用例。
     * Returns preview specs for the given domain.
     */
    fun byDomain(domain: PreviewDomain): List<PreviewSpec> {
        return specs.filter { it.domain == domain }
    }

    /**
     * 按 id 获取预览用例，未知 id 直接失败以暴露损坏的参数或快照配置。
     * Resolves a preview spec by id and fails fast for broken parameters or snapshot config.
     */
    fun require(id: String): PreviewSpec {
        return requireNotNull(specsById[id]) { "Unknown preview spec id: $id" }
    }
}
