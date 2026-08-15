package com.viewcompose.preview.catalog.domain

import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.size
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.BasicTextField
import com.viewcompose.ui.foundation.BasicTextFieldStyle
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.PasswordField
import com.viewcompose.ui.foundation.RadioButton
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.TextArea
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldDefaults
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.node.ImageSource

internal object InputPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "input-controls",
            title = "Checkbox / Switch / Radio / Slider",
            domain = PreviewDomain.Input,
            content = {
                val checkboxState = remember { mutableStateOf(true) }
                val switchState = remember { mutableStateOf(true) }
                val radioState = remember { mutableStateOf(true) }
                val sliderState = remember { mutableStateOf(38) }
                Column(
                    spacing = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Checkbox(
                        text = "启用通知",
                        checked = checkboxState.value,
                        onCheckedChange = { checkboxState.value = it },
                    )
                    Switch(
                        text = "自动同步",
                        checked = switchState.value,
                        onCheckedChange = { switchState.value = it },
                    )
                    RadioButton(
                        text = "选项 A",
                        checked = radioState.value,
                        onCheckedChange = { radioState.value = it },
                    )
                    Slider(
                        value = sliderState.value,
                        onValueChange = { sliderState.value = it },
                    )
                }
            },
        ),
        PreviewSpec(
            id = "input-text-fields",
            title = "TextField / SearchBar",
            domain = PreviewDomain.Input,
            content = {
                val textFieldState = rememberTextFieldState("示例输入")
                val passwordState = rememberTextFieldState("123456")
                val textAreaState = rememberTextFieldState("这是多行输入示例。")
                val basicTextState = rememberTextFieldState("基础输入")
                val searchQueryState = rememberTextFieldState("ViewCompose")
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    TextField(
                        state = textFieldState,
                        label = "用户名",
                        supportingText = "支持 4-20 个字符",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PasswordField(
                        state = passwordState,
                        label = "密码",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextArea(
                        state = textAreaState,
                        label = "备注",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BasicTextField(
                        state = basicTextState,
                        style = BasicTextFieldStyle(
                            cursorColor = TextFieldDefaults.cursorColor(),
                            textColor = TextFieldDefaults.textColor(),
                            textStyle = TextFieldDefaults.textStyle(),
                            placeholderColor = TextFieldDefaults.hintColor(),
                            containerColor = 0x00000000,
                            borderWidth = UiDp.Zero,
                            borderColor = 0x00000000,
                            shape = UiShape.rounded(UiDp.Zero),
                            minimumHeight = UiDp.Zero,
                            horizontalPadding = UiDp.Zero,
                            verticalPadding = UiDp.Zero,
                        ),
                        placeholder = "BasicTextField",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SearchBar(
                        state = searchQueryState,
                        onSearch = { query -> searchQueryState.setTextAndPlaceCursorAtEnd(query) },
                        placeholder = "搜索组件",
                        leadingIcon = ImageSource.Resource(android.R.drawable.ic_menu_search),
                        trailingIcon = {
                            IconButton(
                                icon = ImageSource.Resource(android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "清除",
                                onClick = searchQueryState::clearText,
                                modifier = Modifier.size(24.dp, 24.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        ),
    )
}
