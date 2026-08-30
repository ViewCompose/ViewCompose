package evaluation.invalid

import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp

fun UiTreeBuilder.fabricatedPaddingApi() {
    Column {
        padding(16.dp)
    }
}
