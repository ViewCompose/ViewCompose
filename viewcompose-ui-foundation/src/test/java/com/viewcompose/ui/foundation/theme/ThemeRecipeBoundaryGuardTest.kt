package com.viewcompose.ui.foundation

/*
 * 测试职责：锁定主题快照为纯数据 Token 边界，防止组件 Recipe、设计系统身份或行为闭包进入主题模型。
 * Test responsibility: keeps the theme snapshot a pure-data token boundary and prevents component
 * recipes, design-system identity, or behavior closures from entering the theme model.
 */

import java.lang.reflect.Modifier
import kotlin.Function
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRecipeBoundaryGuardTest {
    @Test
    fun `theme snapshot must not own recipes design-system identity or behavior`() {
        val instanceFields = UiThemeTokens::class.java.declaredFields
            .filterNot { field -> field.isSynthetic || Modifier.isStatic(field.modifiers) }
        val forbiddenNames = instanceFields
            .map { field -> field.name }
            .filter { name ->
                name.contains("recipe", ignoreCase = true) ||
                    name.contains("designSystem", ignoreCase = true)
            }
        val behaviorFields = instanceFields
            .filter { field -> Function::class.java.isAssignableFrom(field.type) }
            .map { field -> "${field.name}: ${field.type.name}" }

        assertTrue(
            "UiThemeTokens must keep component recipes and design-system identity separate: " +
                forbiddenNames.joinToString(),
            forbiddenNames.isEmpty(),
        )
        assertFalse(
            "UiThemeTokens must not store behavior closures: ${behaviorFields.joinToString()}",
            behaviorFields.isNotEmpty(),
        )
    }
}
