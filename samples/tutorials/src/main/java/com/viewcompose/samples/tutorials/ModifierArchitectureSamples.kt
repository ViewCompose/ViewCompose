package com.viewcompose.samples.tutorials

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.modifier.Visibility
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.contentDescription
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.drawWithContent
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.sharedBounds
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.visibility
import com.viewcompose.ui.shared.SharedContentKey
import com.viewcompose.ui.unit.dp

// DOCS_REGION_START(modifier-appearance)
private fun cardAppearance(): Modifier = Modifier
    .backgroundColor(0xFFF5F5F5.toInt())
    .cornerRadius(16.dp)
    .alpha(0.96f)
// DOCS_REGION_END(modifier-appearance)

// DOCS_REGION_START(modifier-drawing)
private fun forwardedContentDrawing(): Modifier = Modifier
    .drawWithContent { drawContent() }
    .visibility(Visibility.Visible)
// DOCS_REGION_END(modifier-drawing)

// DOCS_REGION_START(modifier-interaction)
private fun inspectableAction(onOpen: () -> Unit): Modifier = Modifier
    .clickable(onOpen)
    .contentDescription("Open details")
    .testTag("details-action")
// DOCS_REGION_END(modifier-interaction)

// DOCS_REGION_START(modifier-shared-content)
private fun sharedCardBounds(): Modifier = Modifier
    .sharedBounds(SharedContentKey("article-card"))
// DOCS_REGION_END(modifier-shared-content)

// DOCS_REGION_START(modifier-semantics)
private fun buttonSemantics(): Modifier = Modifier.semantics {
    role = SemanticsRole.Button
    stateDescription = "Ready"
}
// DOCS_REGION_END(modifier-semantics)
