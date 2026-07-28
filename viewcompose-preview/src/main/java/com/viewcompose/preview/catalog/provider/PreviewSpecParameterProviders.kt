package com.viewcompose.preview.catalog.provider

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.viewcompose.preview.catalog.PreviewCatalog
import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpecRef

/**
 * 按领域把 PreviewSpec 转换为 PreviewParameter 可消费的轻量引用。
 * Converts PreviewSpec entries in one domain into lightweight refs consumable by PreviewParameter.
 */
internal abstract class DomainPreviewSpecProvider(
    domain: PreviewDomain,
) : PreviewParameterProvider<PreviewSpecRef> {
    private val specs = PreviewCatalog.byDomain(domain)

    override val values: Sequence<PreviewSpecRef>
        get() = specs.asSequence().map { spec -> PreviewSpecRef(spec.id) }
}

internal class ContentPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Content)

internal class InputPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Input)

internal class ContainerPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Container)

internal class CollectionPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Collection)

internal class NavigationPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Navigation)

internal class FeedbackPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Feedback)

internal class ModifierPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Modifier)

internal class AnimationPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Animation)

internal class GesturePreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Gesture)

internal class GraphicsPreviewSpecProvider : DomainPreviewSpecProvider(PreviewDomain.Graphics)
