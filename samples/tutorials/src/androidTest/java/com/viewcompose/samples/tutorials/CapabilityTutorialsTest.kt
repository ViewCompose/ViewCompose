package com.viewcompose.samples.tutorials

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapabilityTutorialsTest {
    @Test
    fun stateTutorialIncrementsTheCounter() {
        ActivityScenario.launch(StateTutorialActivity::class.java).use {
            onView(withText("Count: 0")).check(matches(withText("Count: 0")))
            onView(withText("Increment")).perform(click())
            onView(withText("Count: 1")).check(matches(withText("Count: 1")))
        }
    }

    @Test
    fun navigationTutorialPushesAndPops() {
        ActivityScenario.launch(NavigationTutorialActivity::class.java).use {
            onView(withText("Open details")).perform(click())
            onView(withText("Details")).check(matches(withText("Details")))
            onView(withText("Back")).perform(click())
            onView(withText("Home")).check(matches(withText("Home")))
        }
    }
}
