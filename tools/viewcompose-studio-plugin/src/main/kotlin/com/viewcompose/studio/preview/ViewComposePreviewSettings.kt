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

    companion object {
        fun forProject(project: Project): ViewComposePreviewSettings {
            return ViewComposePreviewSettings(PropertiesComponent.getInstance(project))
        }
    }
}

private const val LANGUAGE_KEY = "viewcompose.preview.ui.language"
