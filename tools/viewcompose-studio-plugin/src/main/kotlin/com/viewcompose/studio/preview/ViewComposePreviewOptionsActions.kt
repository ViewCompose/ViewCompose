package com.viewcompose.studio.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction

internal fun createPreviewOptionsActionGroup(
    language: PreviewUiLanguage,
    onLanguageSelected: (PreviewUiLanguage) -> Unit,
): DefaultActionGroup {
    val messages = PreviewUiMessages.forLanguage(language)
    val languageGroup = DefaultActionGroup(
        messages.text("menu.language"),
        true,
    )
    PreviewUiLanguage.entries.forEach { choice ->
        languageGroup.add(
            PreviewLanguageAction(
                language = choice,
                currentLanguage = { language },
                onSelected = onLanguageSelected,
            ),
        )
    }
    return DefaultActionGroup().apply {
        add(languageGroup)
    }
}

private class PreviewLanguageAction(
    private val language: PreviewUiLanguage,
    private val currentLanguage: () -> PreviewUiLanguage,
    private val onSelected: (PreviewUiLanguage) -> Unit,
) : ToggleAction(
    PreviewUiMessages.forLanguage(language).text(
        when (language) {
            PreviewUiLanguage.English -> "language.english"
            PreviewUiLanguage.SimplifiedChinese -> "language.chinese"
        },
    ),
) {
    override fun isSelected(event: AnActionEvent): Boolean {
        return currentLanguage() == language
    }

    override fun setSelected(
        event: AnActionEvent,
        state: Boolean,
    ) {
        if (state) {
            onSelected(language)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
