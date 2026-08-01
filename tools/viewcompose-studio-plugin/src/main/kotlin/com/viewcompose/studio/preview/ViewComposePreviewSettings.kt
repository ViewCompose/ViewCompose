package com.viewcompose.studio.preview

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

internal class ViewComposePreviewSettings(
    private val properties: PropertiesComponent,
) {
    var language: PreviewUiLanguage
        get() = PreviewUiLanguage.fromSettingValue(properties.getValue(LANGUAGE_KEY))
        set(value) {
            if (value == PreviewUiLanguage.Default) {
                properties.unsetValue(LANGUAGE_KEY)
            } else {
                properties.setValue(LANGUAGE_KEY, value.settingValue)
            }
        }

    var followEditor: Boolean
        get() = properties.getBoolean(FOLLOW_EDITOR_KEY, true)
        set(value) {
            properties.setValue(FOLLOW_EDITOR_KEY, value, true)
        }

    var autoRefreshOnSave: Boolean
        get() = properties.getBoolean(AUTO_REFRESH_ON_SAVE_KEY, true)
        set(value) {
            properties.setValue(AUTO_REFRESH_ON_SAVE_KEY, value, true)
        }

    companion object {
        fun forProject(project: Project): ViewComposePreviewSettings {
            return ViewComposePreviewSettings(PropertiesComponent.getInstance(project))
        }
    }
}

private const val LANGUAGE_KEY = "viewcompose.preview.ui.language"
private const val FOLLOW_EDITOR_KEY = "viewcompose.preview.followEditor"
private const val AUTO_REFRESH_ON_SAVE_KEY = "viewcompose.preview.autoRefreshOnSave"
