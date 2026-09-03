package generated.viewcompose

import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.unit.dp

fun UiTreeBuilder.CommonAttributesView(
) {
    Column(
        key = "xml:0",
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = HorizontalAlignment.Center,
    ) {
        Text(
            text = "",
            key = "title",
            modifier = Modifier.margin(top = 16.dp),
        )
    }
}
