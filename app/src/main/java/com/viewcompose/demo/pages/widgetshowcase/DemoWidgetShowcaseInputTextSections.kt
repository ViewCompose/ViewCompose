package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.text.InputTransformation
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.EmailField
import com.viewcompose.widget.core.NumberField
import com.viewcompose.widget.core.PasswordField
import com.viewcompose.widget.core.TextArea
import com.viewcompose.widget.core.TextField
import com.viewcompose.widget.core.TextFieldSize
import com.viewcompose.widget.core.TextFieldVariant
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.rememberTextFieldState

@ViewComposePreview(name = "TextField", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseTextField() {
    val filledValue = rememberTextFieldState()
    val tonalValue = rememberTextFieldState()
    val outlinedValue = rememberTextFieldState()
    val errorValue = rememberTextFieldState("错误示例")
    val readOnlyValue = rememberTextFieldState("只读内容")

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "变体对比", subtitle = "Filled / Tonal / Outlined") {
            TextField(
                state = filledValue,
                label = "Filled",
                hint = "请输入",
                variant = TextFieldVariant.Filled,
                modifier = Modifier.fillMaxWidth().margin(bottom = 8.dp),
            )
            TextField(
                state = tonalValue,
                label = "Tonal",
                hint = "请输入",
                variant = TextFieldVariant.Tonal,
                modifier = Modifier.fillMaxWidth().margin(bottom = 8.dp),
            )
            TextField(
                state = outlinedValue,
                label = "Outlined",
                hint = "请输入",
                variant = TextFieldVariant.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "尺寸对比", subtitle = "Compact / Medium / Large") {
            TextFieldSize.entries.forEach { size ->
                val sizeValue = rememberTextFieldState()
                TextField(
                    state = sizeValue,
                    label = size.name,
                    hint = "${size.name} size",
                    size = size,
                    modifier = Modifier.fillMaxWidth().margin(bottom = 8.dp),
                )
            }
        }

        DemoSection(title = "辅助文本", subtitle = "label / hint / supportingText") {
            val v = rememberTextFieldState()
            TextField(
                state = v,
                label = "用户名",
                hint = "请输入用户名",
                supportingText = "用户名长度为 3-20 个字符",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "错误态", subtitle = "isError = true") {
            TextField(
                state = errorValue,
                label = "邮箱",
                isError = true,
                supportingText = "邮箱格式不正确",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "只读", subtitle = "readOnly = true") {
            TextField(
                state = readOnlyValue,
                label = "只读字段",
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "字数限制", subtitle = "maxLength = 20") {
            val v = rememberTextFieldState()
            TextField(
                state = v,
                label = "限制字数",
                inputTransformation = InputTransformation.maxCodePoints(20),
                supportingText = "${v.text.codePointCount(0, v.text.length)}/20",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@ViewComposePreview(name = "TextField variants", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseTextFieldVariants() {
    val pwdValue = rememberTextFieldState()
    val emailValue = rememberTextFieldState()
    val numberValue = rememberTextFieldState()
    val areaValue = rememberTextFieldState()

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "PasswordField", subtitle = "密码输入框") {
            PasswordField(
                state = pwdValue,
                label = "密码",
                hint = "请输入密码",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "EmailField", subtitle = "邮箱输入框") {
            EmailField(
                state = emailValue,
                label = "邮箱",
                hint = "user@example.com",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "NumberField", subtitle = "数字输入框") {
            NumberField(
                state = numberValue,
                label = "数量",
                hint = "请输入数字",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "TextArea", subtitle = "多行文本输入") {
            TextArea(
                state = areaValue,
                label = "备注",
                hint = "请输入备注信息...",
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
