package generated.viewcompose

import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp

fun UiTreeBuilder.ScreenView(
    profileAvatar: ImageSource,
    profilePhoto: String,
    profileTitle: String,
    editProfile: String,
    footerLabel: String,
) {
    Column(
        key = "xml:0",
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Box(
            key = "profile_header",
            modifier = Modifier.fillMaxWidth().height(120.dp),
        ) {
            Image(
                source = profileAvatar,
                contentDescription = profilePhoto,
                contentScale = ImageContentScale.Crop,
                key = "avatar",
                modifier = Modifier.width(96.dp).height(96.dp),
            )
        }
        Text(
            text = profileTitle,
            key = "title",
        )
        Button(
            text = editProfile,
            key = "edit_profile",
        )
        Text(
            text = footerLabel,
            key = "footer",
        )
    }
}
