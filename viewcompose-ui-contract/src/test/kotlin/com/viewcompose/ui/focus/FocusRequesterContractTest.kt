package com.viewcompose.ui.focus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRequesterContractTest {
    @Test
    fun `requester fails fast before a target is attached`() {
        val requester = FocusRequester()

        val error = runCatching {
            requester.requestFocus()
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("Modifier.focusRequester"))
    }

    @Test
    fun `requester offers focus in attachment order`() {
        val requester = FocusRequester()
        val rejected = FakeConnector(
            restorationKey = "first",
            acceptsFocus = false,
        )
        val accepted = FakeConnector(
            restorationKey = "second",
            acceptsFocus = true,
        )
        requester.attach(rejected)
        requester.attach(accepted)

        assertTrue(requester.requestFocus(FocusDirection.Down))
        assertTrue(rejected.requests == listOf(FocusDirection.Down))
        assertTrue(accepted.requests == listOf(FocusDirection.Down))
    }

    @Test
    fun `saved focus restores after connector remount`() {
        val requester = FocusRequester()
        val firstMount = FakeConnector(
            restorationKey = "field",
            acceptsFocus = true,
            state = FocusState(isFocused = true, hasFocus = true),
        )
        requester.attach(firstMount)

        assertTrue(requester.saveFocusedChild())
        requester.detach(firstMount)
        assertFalse(requester.restoreFocusedChild())

        val secondMount = FakeConnector(
            restorationKey = "field",
            acceptsFocus = true,
        )
        requester.attach(secondMount)

        assertTrue(secondMount.requests == listOf(FocusDirection.Enter))
        assertFalse(requester.restoreFocusedChild())
    }

    private class FakeConnector(
        override val restorationKey: Any,
        private val acceptsFocus: Boolean,
        private val state: FocusState = FocusState.Inactive,
    ) : FocusRequesterConnector {
        val requests = mutableListOf<FocusDirection>()

        override val focusState: FocusState
            get() = state

        override fun requestFocus(direction: FocusDirection): Boolean {
            requests += direction
            return acceptsFocus
        }
    }
}
