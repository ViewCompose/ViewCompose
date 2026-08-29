package evaluation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun UnsupportedCustomEffect(runUnknownProtocol: suspend () -> Unit) {
    LaunchedEffect(runUnknownProtocol) {
        runUnknownProtocol()
    }
}
