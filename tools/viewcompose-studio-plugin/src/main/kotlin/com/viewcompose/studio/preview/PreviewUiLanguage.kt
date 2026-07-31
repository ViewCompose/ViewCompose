package com.viewcompose.studio.preview

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

internal enum class PreviewUiLanguage(
    val settingValue: String,
    val locale: Locale,
) {
    English(
        settingValue = "en",
        locale = Locale.ENGLISH,
    ),
    SimplifiedChinese(
        settingValue = "zh-CN",
        locale = Locale.SIMPLIFIED_CHINESE,
    ),
    ;

    companion object {
        val Default: PreviewUiLanguage = English

        fun fromSettingValue(value: String?): PreviewUiLanguage {
            return entries.firstOrNull { language -> language.settingValue == value } ?: Default
        }
    }
}

internal class PreviewUiMessages private constructor(
    private val language: PreviewUiLanguage,
    private val bundle: ResourceBundle,
) {
    fun text(
        key: String,
        vararg arguments: Any?,
    ): String {
        val pattern = bundle.getString(key)
        return if (arguments.isEmpty()) {
            pattern
        } else {
            MessageFormat(pattern, language.locale).format(arguments)
        }
    }

    fun loadingMessage(message: String): String {
        return when {
            message == "Preparing static preview…" -> text("loading.preparing")
            message == "Compiling preview descriptors…" -> text("loading.compiling")
            message == "Matching compiled preview…" -> text("loading.matching")
            message == "Discovering project previews…" -> text("loading.gallery.discovery")
            message.startsWith("Rendering ") && message.endsWith(" uncached previews…") -> {
                text("loading.gallery.uncached")
            }
            message.startsWith("Compiling preview descriptors for ") -> {
                text("loading.gallery.compiling")
            }
            message.startsWith("Rendering ") && ':' in message -> {
                message
            }
            message.startsWith("Rendering ") && message.endsWith("…") -> {
                text(
                    "loading.rendering",
                    message.removePrefix("Rendering ").removeSuffix("…"),
                )
            }
            else -> message
        }
    }

    fun failureTitle(title: String): String {
        val key = FAILURE_TITLE_KEYS[title] ?: return title
        return text(key)
    }

    companion object {
        private const val BUNDLE_NAME = "messages.ViewComposePreviewBundle"

        private val messages = PreviewUiLanguage.entries.associateWith { language ->
            PreviewUiMessages(
                language = language,
                bundle = ResourceBundle.getBundle(
                    BUNDLE_NAME,
                    language.locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT),
                ),
            )
        }

        fun forLanguage(language: PreviewUiLanguage): PreviewUiMessages {
            return checkNotNull(messages[language])
        }
    }
}

private val FAILURE_TITLE_KEYS = mapOf(
    "Preview project is unavailable" to "failure.projectUnavailable",
    "Preview render cancelled" to "failure.cancelled",
    "Preview discovery failed" to "failure.discovery",
    "No compiled preview matched this function" to "failure.noMatch",
    "Preview render failed" to "failure.render",
    "Preview tooling failed" to "failure.tooling",
)
