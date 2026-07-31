package com.viewcompose.studio.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction

internal fun createPreviewOptionsActionGroup(
    language: PreviewUiLanguage,
    onLanguageSelected: (PreviewUiLanguage) -> Unit,
    followEditor: () -> Boolean,
    onFollowEditorChanged: (Boolean) -> Unit,
    autoRefreshOnSave: () -> Boolean,
    onAutoRefreshOnSaveChanged: (Boolean) -> Unit,
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
        add(
            PreviewBooleanToggleAction(
                text = messages.text("menu.followEditor"),
                isEnabled = followEditor,
                onChanged = onFollowEditorChanged,
            ),
        )
        add(
            PreviewBooleanToggleAction(
                text = messages.text("menu.autoRefreshOnSave"),
                isEnabled = autoRefreshOnSave,
                onChanged = onAutoRefreshOnSaveChanged,
            ),
        )
        addSeparator()
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

private class PreviewBooleanToggleAction(
    text: String,
    private val isEnabled: () -> Boolean,
    private val onChanged: (Boolean) -> Unit,
) : ToggleAction(text) {
    override fun isSelected(event: AnActionEvent): Boolean = isEnabled()

    override fun setSelected(
        event: AnActionEvent,
        state: Boolean,
    ) {
        onChanged(state)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
