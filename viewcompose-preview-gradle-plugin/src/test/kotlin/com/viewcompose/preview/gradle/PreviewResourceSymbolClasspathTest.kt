package com.viewcompose.preview.gradle

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewResourceSymbolClasspathTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `retained classpath contains resource symbols but excludes implementation classes`() {
        val classes = temporaryFolder.newFolder("classes")
        classes.writeClass("example/R.class", byteArrayOf(1))
        classes.writeClass("example/R\$string.class", byteArrayOf(2))
        classes.writeClass("example/Screen.class", byteArrayOf(3))
        val jar = temporaryFolder.newFile("symbols.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.writeClass("library/R.class", byteArrayOf(4))
            zip.writeClass("library/R\$color.class", byteArrayOf(5))
            zip.writeClass("library/Widget.class", byteArrayOf(6))
        }

        val output = PreviewResourceSymbolClasspath.materialize(
            projectClasspath = listOf(classes, jar),
            artifactRoot = temporaryFolder.newFolder("artifacts"),
            compatibilityFingerprint = "a".repeat(64),
        )

        assertArrayEquals(byteArrayOf(1), output.resolve("example/R.class").readBytes())
        assertArrayEquals(byteArrayOf(2), output.resolve("example/R\$string.class").readBytes())
        assertArrayEquals(byteArrayOf(4), output.resolve("library/R.class").readBytes())
        assertArrayEquals(byteArrayOf(5), output.resolve("library/R\$color.class").readBytes())
        assertFalse(output.resolve("example/Screen.class").exists())
        assertFalse(output.resolve("library/Widget.class").exists())
        assertTrue(output.resolve(".complete").isFile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `conflicting resource symbols fail instead of choosing an arbitrary class`() {
        val first = temporaryFolder.newFolder("first")
        val second = temporaryFolder.newFolder("second")
        first.writeClass("example/R.class", byteArrayOf(1))
        second.writeClass("example/R.class", byteArrayOf(2))

        PreviewResourceSymbolClasspath.materialize(
            projectClasspath = listOf(first, second),
            artifactRoot = temporaryFolder.newFolder("artifacts-conflict"),
            compatibilityFingerprint = "b".repeat(64),
        )
    }
}

private fun File.writeClass(relativePath: String, bytes: ByteArray) {
    resolve(relativePath).apply {
        parentFile.mkdirs()
        writeBytes(bytes)
    }
}

private fun ZipOutputStream.writeClass(path: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(path))
    write(bytes)
    closeEntry()
}
