package com.viewcompose.host.android.resources

import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.os.LocaleList
import android.widget.FrameLayout
import com.viewcompose.host.android.renderInto
import com.viewcompose.host.android.test.R as TestR
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.unit.UiDp
import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class AndroidResourceEnvironmentTest {
    @Test
    fun `typed lookups resolve from the mounted resource context`() {
        val context = configuredContext("en-US")
        val root = FrameLayout(context)
        var capturedContext: android.content.Context? = null
        var capturedResources: android.content.res.Resources? = null
        var title = ""
        var formatted = ""
        var plural = ""
        var color = 0
        var dimension = UiDp.Zero
        var dimensionPixels = 0
        var boolean = false
        var integer = 0
        var strings = emptyList<String>()
        var integers = intArrayOf()

        val session = renderInto(root) {
            AndroidResourceEnvironment(context) {
                capturedContext = LocalAndroidContext.current
                capturedResources = LocalAndroidResources.current
                title = stringResource(TestR.string.resource_title)
                formatted = stringResource(TestR.string.resource_formatted, 3)
                plural = pluralStringResource(TestR.plurals.resource_items, 3, 3)
                color = colorResource(TestR.color.resource_color)
                dimension = dimensionResource(TestR.dimen.resource_dimension)
                dimensionPixels = dimensionPixelSizeResource(TestR.dimen.resource_dimension)
                boolean = booleanResource(TestR.bool.resource_boolean)
                integer = integerResource(TestR.integer.resource_integer)
                strings = stringArrayResource(TestR.array.resource_strings)
                integers = integerArrayResource(TestR.array.resource_integers)
                Text(title)
            }
        }

        assertSame(context, capturedContext)
        assertSame(context.resources, capturedResources)
        assertEquals("Resource title", title)
        assertEquals("Count: 3", formatted)
        assertEquals("3 items", plural)
        assertEquals(Color.rgb(0x33, 0x66, 0x99), color)
        assertEquals(12f, dimension.value)
        assertEquals((12f * context.resources.displayMetrics.density).toInt(), dimensionPixels)
        assertEquals(true, boolean)
        assertEquals(3, integer)
        assertEquals(listOf("alpha", "beta"), strings)
        assertArrayEquals(intArrayOf(2, 4), integers)

        session.dispose()
    }

    @Test
    fun `imperative refresh updates context resources and revision before recomposition`() {
        val english = configuredContext("en-US")
        val chinese = configuredContext("zh-CN")
        val stableContext = MutableContextWrapper(english)
        val controller = AndroidResourceRefreshController()
        var pendingContext: android.content.Context? = null
        var beforeRefreshCount = 0
        var title = ""
        var revision = -1L
        val root = FrameLayout(stableContext)

        val session = renderInto(root) {
            AndroidResourceEnvironment(
                context = stableContext,
                refreshController = controller,
                onBeforeRefresh = {
                    beforeRefreshCount += 1
                    pendingContext?.let(stableContext::setBaseContext)
                },
            ) {
                title = stringResource(TestR.string.resource_title)
                revision = Environment.resourceRevision
                Text(title)
            }
        }

        assertEquals("Resource title", title)
        assertEquals(0L, revision)

        pendingContext = chinese
        controller.refresh()
        session.render()

        assertEquals(1, beforeRefreshCount)
        assertEquals("资源标题", title)
        assertEquals(1L, revision)

        session.dispose()
        controller.refresh()
        assertEquals(1, beforeRefreshCount)
    }

    @Test
    fun `lifecycle registers one controller listener and advances fixed revision`() {
        val controller = AndroidResourceRefreshController()
        var beforeRefreshCount = 0
        val lifecycle = AndroidResourceEnvironmentLifecycle(
            context = RuntimeEnvironment.getApplication(),
            refreshController = controller,
            fixedEnvironmentValues = UiEnvironmentValues(resourceRevision = 4L),
            observeConfigurationChanges = false,
            onBeforeRefresh = { beforeRefreshCount += 1 },
        )

        lifecycle.start()
        lifecycle.start()
        controller.refresh()

        assertEquals(1, beforeRefreshCount)
        assertEquals(5L, lifecycle.snapshot.value.environment.resourceRevision)

        lifecycle.close()
        lifecycle.close()
        controller.refresh()
        assertEquals(1, beforeRefreshCount)
        assertEquals(5L, lifecycle.snapshot.value.environment.resourceRevision)
    }

    @Test
    fun `Android configuration callback advances the mounted resource revision`() {
        var beforeRefreshCount = 0
        val lifecycle = AndroidResourceEnvironmentLifecycle(
            context = RuntimeEnvironment.getApplication(),
            refreshController = null,
            fixedEnvironmentValues = UiEnvironmentValues(resourceRevision = 8L),
            observeConfigurationChanges = true,
            onBeforeRefresh = { beforeRefreshCount += 1 },
        )

        lifecycle.start()
        lifecycle.onConfigurationChanged(Configuration())

        assertEquals(1, beforeRefreshCount)
        assertEquals(9L, lifecycle.snapshot.value.environment.resourceRevision)
        lifecycle.close()
    }

    @Test
    fun `failed pre-refresh keeps the previous snapshot and remains retryable`() {
        var failRefresh = true
        val lifecycle = AndroidResourceEnvironmentLifecycle(
            context = RuntimeEnvironment.getApplication(),
            refreshController = null,
            fixedEnvironmentValues = UiEnvironmentValues(resourceRevision = 3L),
            observeConfigurationChanges = false,
            onBeforeRefresh = {
                check(!failRefresh) { "Expected refresh failure" }
            },
        )
        val previousSnapshot = lifecycle.snapshot.value

        assertThrows(IllegalStateException::class.java) {
            lifecycle.refresh()
        }
        assertSame(previousSnapshot, lifecycle.snapshot.value)

        failRefresh = false
        lifecycle.refresh()
        assertEquals(4L, lifecycle.snapshot.value.environment.resourceRevision)
    }

    @Test
    fun `lookup outside resource environment fails clearly`() {
        val error = assertThrows(IllegalStateException::class.java) {
            stringResource(TestR.string.resource_title)
        }

        assertEquals(true, error.message.orEmpty().contains("AndroidResourceEnvironment"))
    }

    private fun configuredContext(languageTag: String): android.content.Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        }
        return application.createConfigurationContext(configuration)
    }
}
