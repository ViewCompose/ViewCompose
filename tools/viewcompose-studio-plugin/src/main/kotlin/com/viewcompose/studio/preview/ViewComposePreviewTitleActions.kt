package com.viewcompose.studio.preview

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal fun createPreviewTitleActions(
    language: PreviewUiLanguage,
    onShowGallery: () -> Unit,
    isOpenSourceEnabled: () -> Boolean,
    onOpenSource: () -> Unit,
    isFullRefreshEnabled: () -> Boolean,
    onFullRefresh: () -> Unit,
    isRefreshEnabled: () -> Boolean,
    onRefresh: () -> Unit,
): List<AnAction> {
    val messages = PreviewUiMessages.forLanguage(language)
    return listOf(
        object : DumbAwareAction(
            messages.text("action.gallery"),
            messages.text("action.gallery.description"),
            AllIcons.Actions.ShowAsTree,
        ) {
            override fun actionPerformed(event: AnActionEvent) {
                onShowGallery()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        },
        object : DumbAwareAction(
            messages.text("action.fullRefresh"),
            messages.text("action.fullRefresh.description"),
            AllIcons.Actions.Compile,
        ) {
            override fun actionPerformed(event: AnActionEvent) {
                onFullRefresh()
            }

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = isFullRefreshEnabled()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        },
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
        object : DumbAwareAction(
            messages.text("action.openSource"),
            messages.text("action.openSource.description"),
            AllIcons.General.Locate,
        ) {
            override fun actionPerformed(event: AnActionEvent) {
                onOpenSource()
            }

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = isOpenSourceEnabled()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        },
    )
}
