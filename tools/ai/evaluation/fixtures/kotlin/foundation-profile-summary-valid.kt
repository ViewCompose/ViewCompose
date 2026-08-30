package evaluation.valid

import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp

// Consumer-form equivalent of the canonical module.ui-foundation-profile-summary compiled region.
fun UiTreeBuilder.ProfileSummary(name: String, role: String) {
    UiTheme {
        Column(spacing = 8.dp) {
            Text(name, style = TextDefaults.titleMediumStyle())
            Text(role, color = TextDefaults.secondaryColor())
        }
    }
}
