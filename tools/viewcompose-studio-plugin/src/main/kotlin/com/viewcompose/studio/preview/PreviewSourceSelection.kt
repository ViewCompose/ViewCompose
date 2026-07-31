package com.viewcompose.studio.preview

internal data class PreviewSourceSelection(
    val filePath: String,
    val symbolName: String,
    val line: Int,
) {
    init {
        require(filePath.isNotBlank()) { "Preview source file path must not be blank." }
        require(symbolName.isNotBlank()) { "Preview symbol name must not be blank." }
        require(line > 0) { "Preview source line must be positive." }
    }
}
