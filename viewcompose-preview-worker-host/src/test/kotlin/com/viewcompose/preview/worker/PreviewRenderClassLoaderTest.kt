package com.viewcompose.preview.worker

import java.net.URLClassLoader
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewRenderClassLoaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `each render gets a fresh loader and restores the worker context loader`() {
        val original = Thread.currentThread().contextClassLoader
        val classpath = listOf(temporaryFolder.newFolder("app-classes").absolutePath)

        val first = withPreviewRenderClassLoader(classpath) {
            Thread.currentThread().contextClassLoader.also { loader ->
                assertTrue(loader is URLClassLoader)
            }
        }
        assertSame(original, Thread.currentThread().contextClassLoader)
        val second = withPreviewRenderClassLoader(classpath) {
            Thread.currentThread().contextClassLoader
        }

        assertNotSame(first, second)
        assertSame(original, Thread.currentThread().contextClassLoader)
    }
}
