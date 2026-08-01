package com.viewcompose.preview.gradle

import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewWorkerReuseVerifierTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `tooling node ids may change while pixels and structure remain equivalent`() {
        val warmImage = temporaryFolder.newFile("warm.png").apply { writeBytes(byteArrayOf(1, 2)) }
        val coldImage = temporaryFolder.newFile("cold.png").apply { writeBytes(byteArrayOf(1, 2)) }
        val warmTree = temporaryFolder.newFile("warm.json").apply {
            writeText("""{"tree":[{"nodeId":"node-a","type":"Text"}]}""")
        }
        val coldTree = temporaryFolder.newFile("cold.json").apply {
            writeText("""{"tree":[{"nodeId":"node-z","type":"Text"}]}""")
        }

        PreviewWorkerReuseVerifier.requireEquivalent(
            warmImage = warmImage,
            coldImage = coldImage,
            warmTree = warmTree,
            coldTree = coldTree,
        )
    }

    @Test
    fun `pixel or semantic structure differences fail the reuse gate`() {
        val warmImage = temporaryFolder.newFile("warm-diff.png").apply { writeBytes(byteArrayOf(1)) }
        val coldImage = temporaryFolder.newFile("cold-diff.png").apply { writeBytes(byteArrayOf(2)) }
        val warmTree = temporaryFolder.newFile("warm-diff.json").apply {
            writeText("""{"tree":[{"type":"Text"}]}""")
        }
        val coldTree = temporaryFolder.newFile("cold-diff.json").apply {
            writeText("""{"tree":[{"type":"Button"}]}""")
        }

        assertThrows(IllegalArgumentException::class.java) {
            PreviewWorkerReuseVerifier.requireEquivalent(
                warmImage = warmImage,
                coldImage = coldImage,
                warmTree = warmTree,
                coldTree = warmTree,
            )
        }
        coldImage.writeBytes(warmImage.readBytes())
        assertThrows(IllegalArgumentException::class.java) {
            PreviewWorkerReuseVerifier.requireEquivalent(
                warmImage = warmImage,
                coldImage = coldImage,
                warmTree = warmTree,
                coldTree = coldTree,
            )
        }
    }
}
