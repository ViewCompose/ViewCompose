package evaluation.invalid

import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.node.ImageSource

fun UiTreeBuilder.unlabeledMeaningfulImage() {
    Image(source = ImageSource.Resource(1))
}
