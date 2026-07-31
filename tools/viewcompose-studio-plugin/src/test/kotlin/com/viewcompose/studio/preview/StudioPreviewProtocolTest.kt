package com.viewcompose.studio.preview

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StudioPreviewProtocolTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `reads the stable catalog subset and ignores renderer configuration details`() {
        val catalogFile = temporaryFolder.newFile("descriptors.json").toPath()
        Files.writeString(
            catalogFile,
            catalogJson(
                sourcePath = "/project/src/Sample.kt",
            ),
        )

        val catalog = StudioPreviewProtocolReader.readCatalog(catalogFile)

        assertEquals(":app", catalog.modulePath)
        assertEquals("debug", catalog.buildVariant)
        assertEquals("sample-card", catalog.descriptors.single().id)
        assertEquals("default", catalog.descriptors.single().variants.single().id)
        assertEquals(
            "/project/src/Sample.kt",
            catalog.descriptors.single().sourceLocation?.filePath,
        )
    }

    @Test
    fun `rejects a path-unsafe descriptor id`() {
        val catalogFile = temporaryFolder.newFile("unsafe.json").toPath()
        Files.writeString(
            catalogFile,
            catalogJson(
                sourcePath = "/project/src/Sample.kt",
                descriptorId = "../sample",
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            StudioPreviewProtocolReader.readCatalog(catalogFile)
        }
    }
}

internal fun catalogJson(
    sourcePath: String,
    descriptorId: String = "sample-card",
    includeDarkVariant: Boolean = false,
): String {
    val darkVariant = if (includeDarkVariant) {
        """,
                {
                  "id": "dark",
                  "displayName": "Dark",
                  "configuration": {
                    "widthDp": 411,
                    "heightDp": 891
                  }
                }"""
    } else {
        ""
    }
    return """
        {
          "protocolVersion": 1,
          "modulePath": ":app",
          "buildVariant": "debug",
          "buildFingerprint": "${"a".repeat(64)}",
          "descriptors": [
            {
              "id": "$descriptorId",
              "displayName": "SampleCard",
              "group": "catalog",
              "entryPoint": {
                "ownerClassName": "sample.SampleKt",
                "methodName": "SampleCard"
              },
              "variants": [
                {
                  "id": "default",
                  "displayName": "Default",
                  "configuration": {
                    "widthDp": 411,
                    "heightDp": 891
                  }
                }$darkVariant
              ],
              "sourceLocation": {
                "filePath": "$sourcePath",
                "line": 10,
                "column": 1,
                "symbolName": "SampleCard"
              }
            }
          ],
          "diagnostics": []
        }
    """.trimIndent()
}
