package com.viewcompose.studio.preview

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal fun createPreviewTitleActions(
    language: PreviewUiLanguage,
    isRefreshEnabled: () -> Boolean,
    onRefresh: () -> Unit,
): List<AnAction> {
    val messages = PreviewUiMessages.forLanguage(language)
    return listOf(
        object : DumbAwareAction(
            messages.text("action.refresh"),
            messages.text("action.refresh.description"),
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(event: AnActionEvent) {
                onRefresh()
            }

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = isRefreshEnabled()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        },
    )
}
