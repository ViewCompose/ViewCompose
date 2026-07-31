package com.viewcompose.studio.preview

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test
    fun `reads bounded render tree composition and patch diagnostics`() {
        val snapshotFile = temporaryFolder.newFile("render-tree.json").toPath()
        Files.writeString(
            snapshotFile,
            """
            {
              "stats": {
                "inserts": 4,
                "reuses": 2,
                "removals": 1,
                "reboundNodes": 3,
                "patchedNodes": 2,
                "skippedBindings": 5,
                "skippedSubtrees": 6
              },
              "structure": {
                "vnodeCount": 7,
                "mountedNodeCount": 6,
                "maxVNodeDepth": 3,
                "maxMountedDepth": 2
              },
              "warnings": ["sample warning"],
              "tree": [
                {
                  "type": "Column",
                  "key": "root",
                  "children": [
                    {
                      "type": "Text",
                      "key": "title",
                      "nodeId": "node-title",
                      "sourceCallSites": [
                        {
                          "className": "sample.SampleKt",
                          "methodName": "SampleCard",
                          "fileName": "Sample.kt",
                          "lineNumber": 24
                        }
                      ],
                      "children": []
                    }
                  ]
                }
              ],
              "nativeViewTree": [
                {
                  "className": "android.widget.FrameLayout",
                  "bounds": {
                    "left": 0,
                    "top": 0,
                    "right": 1080,
                    "bottom": 1920
                  },
                  "measuredWidth": 1080,
                  "measuredHeight": 1920,
                  "visibility": "VISIBLE",
                  "children": [
                    {
                      "className": "android.widget.TextView",
                      "bounds": {
                        "left": 32,
                        "top": 48,
                        "right": 256,
                        "bottom": 112
                      },
                      "measuredWidth": 224,
                      "measuredHeight": 64,
                      "visibility": "VISIBLE",
                      "nodeId": "node-title",
                      "sourceCallSites": [
                        {
                          "className": "sample.SampleKt",
                          "methodName": "SampleCard",
                          "fileName": "Sample.kt",
                          "lineNumber": 24
                        }
                      ],
                      "children": []
                    }
                  ]
                }
              ],
              "patches": [
                {
                  "operation": "Insert",
                  "type": "Text",
                  "key": "title",
                  "parentKey": "root",
                  "index": 0,
                  "moved": false,
                  "detail": "mounted"
                }
              ],
              "composition": {
                "invalidatedScopeCount": 1,
                "recomposedScopeCount": 1,
                "skippedScopeCount": 0,
                "scopes": [
                  {
                    "path": "root/content",
                    "signature": "abc",
                    "depth": 1,
                    "reasons": ["StateChanged"],
                    "recomposed": true,
                    "skipped": false,
                    "locals": [
                      {
                        "name": "Theme",
                        "value": "Dark"
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val snapshot = StudioPreviewProtocolReader.readRenderSnapshot(snapshotFile)

        assertEquals(7, snapshot.structure.vnodeCount)
        assertEquals(4, snapshot.stats.inserts)
        assertEquals("Text", snapshot.tree.single().children.single().type)
        assertEquals("node-title", snapshot.tree.single().children.single().nodeId)
        assertEquals(
            24,
            snapshot.tree.single().children.single().sourceCallSites.single().lineNumber,
        )
        assertEquals(
            "android.widget.TextView",
            snapshot.nativeViewTree.single().children.single().className,
        )
        assertEquals(224, snapshot.nativeViewTree.single().children.single().bounds.width)
        assertEquals(
            "node-title",
            snapshot.nativeViewTree.single().children.single().nodeId,
        )
        assertEquals("Insert", snapshot.patches.single().operation)
        assertTrue(snapshot.composition.scopes.single().recomposed)
        assertEquals(
            listOf("StateChanged"),
            snapshot.composition.scopes.single().reasons,
        )
        assertEquals("Dark", snapshot.composition.scopes.single().locals.single().value)
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
