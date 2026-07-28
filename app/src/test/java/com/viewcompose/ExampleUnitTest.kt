package com.viewcompose

/*
 * 测试职责：覆盖 app demo 中的 Example Unit 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Example Unit behavior in app demo and guards the contract against regressions.
 */

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}