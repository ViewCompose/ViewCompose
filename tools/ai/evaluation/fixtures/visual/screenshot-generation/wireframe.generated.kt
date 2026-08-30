package generated.viewcompose

import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldInputProfile
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.node.TextFieldImeAction

fun UiTreeBuilder.ScreenshotWireframeView(
    emailState: TextFieldState,
    onEmailSubmit: (TextFieldImeAction) -> Boolean,
    onContinue: () -> Unit,
) {
    Column(
        key = "wireframe-root",
    ) {
        Text(
            text = "Welcome",
            key = "wireframe-title",
        )
        TextField(
            state = emailState,
            placeholder = "Email address",
            inputProfile = TextFieldInputProfile.Email,
            onKeyboardAction = onEmailSubmit,
            key = "wireframe-field",
        )
        Button(
            text = "Continue",
            onClick = onContinue,
            key = "wireframe-button",
        )
    }
}
