package generated.viewcompose

import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.Visibility
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.visibility
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp

fun UiTreeBuilder.ProfileCardView(
    profileAvatar: ImageSource,
    profilePhoto: String,
    statusLabel: String,
) {
    Box(
        key = "profile_card",
        modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp),
    ) {
        Image(
            source = profileAvatar,
            contentDescription = profilePhoto,
            contentScale = ImageContentScale.Crop,
            key = "avatar",
            modifier = Modifier.width(96.dp).height(96.dp),
        )
        Text(
            text = statusLabel,
            key = "status",
            modifier = Modifier.visibility(Visibility.Gone),
        )
    }
}
