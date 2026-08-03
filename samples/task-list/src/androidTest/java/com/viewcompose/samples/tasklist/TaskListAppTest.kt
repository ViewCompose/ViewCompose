package com.viewcompose.samples.tasklist

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskListAppTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun addsAndCompletesATask() {
        onView(withText("1 of 2 complete")).check(matches(isDisplayed()))
        onView(withHint("New task"))
            .perform(click(), replaceText("Write a device test"), closeSoftKeyboard())
        onView(withText("Add task")).perform(click())
        onView(withText("Write a device test")).check(matches(isDisplayed()))
        onView(withText("1 of 3 complete")).check(matches(isDisplayed()))

        onView(withText("Read the tutorial")).perform(click())
        onView(withText("2 of 3 complete")).check(matches(isDisplayed()))
    }
}
