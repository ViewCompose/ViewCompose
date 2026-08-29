package generated.viewcompose

import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldInputProfile
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp

fun UiTreeBuilder.LoginView(
    loginTitle: String,
    emailHint: String,
    loginAction: String,
    emailState: TextFieldState,
) {
    Column(
        key = "xml:0",
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            text = loginTitle,
            key = "title",
        )
        TextField(
            state = emailState,
            placeholder = emailHint,
            inputProfile = TextFieldInputProfile.Email,
            key = "email",
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            text = loginAction,
            key = "submit",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
