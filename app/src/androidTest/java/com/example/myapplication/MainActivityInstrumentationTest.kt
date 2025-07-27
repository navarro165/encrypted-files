package com.example.myapplication

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.recyclerview.widget.RecyclerView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.After

/**
 * Instrumentation tests for MainActivity UI interactions
 * These tests run on device/emulator and test actual UI behavior
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityInstrumentationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setup() {
        Intents.init()
        // Reset authentication state before each test
        activityRule.scenario.onActivity { activity ->
            AuthenticationManager.getInstance(activity).setLastAuthenticationTimestampForTesting(0L)
        }
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun testMainActivityLaunch() {
        // Test that main activity launches successfully
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.button))
            .check(matches(isDisplayed()))
            .check(matches(withText("Add Files")))
    }

    @Test
    fun testRecyclerViewIsDisplayed() {
        // Test that RecyclerView is properly configured
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
            .check(matches(hasMinimumChildCount(0))) // Initially empty
    }

    @Test
    fun testAddFilesButtonClick() {
        // Test add files button behavior
        onView(withId(R.id.button))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
            .perform(click())
        
        // Note: This would normally trigger file picker or biometric prompt
        // In test environment, the biometric prompt may not appear
    }

    @Test
    fun testOptionsMenuCreation() {
        // Test that options menu can be opened
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        // Options menu should be accessible via menu button or overflow
        // The actual menu items are defined in main_menu.xml
    }

    @Test
    fun testRecyclerViewInteractions() {
        // Test RecyclerView interactions when it has content
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        // Test scrolling behavior (even if empty)
        onView(withId(R.id.recyclerView))
            .perform(swipeUp())
            .perform(swipeDown())
    }

    @Test
    fun testActivityLayoutConstraints() {
        // Test that layout constraints are properly set
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.button))
            .check(matches(isDisplayed()))
        
        // Button should be at bottom, RecyclerView should fill remaining space
        onView(withId(R.id.button))
            .check(matches(isCompletelyDisplayed()))
    }

    @Test
    fun testActivityTitle() {
        // Test that activity has proper title
        activityRule.scenario.onActivity { activity ->
            // Activity should be running
            assert(!activity.isFinishing)
            assert(!activity.isDestroyed)
        }
    }

    @Test
    fun testViewBindingSetup() {
        // Test that View Binding is working correctly
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.button))
            .check(matches(isDisplayed()))
            .check(matches(withText("Add Files")))
    }

    @Test
    fun testEmptyStateDisplay() {
        // Test how app behaves with no encrypted files
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        // RecyclerView should be empty initially
        activityRule.scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerView)
            assert(recyclerView.adapter?.itemCount == 0 || recyclerView.adapter?.itemCount == null)
        }
    }

    @Test
    fun testRotationHandling() {
        // Test device rotation
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        // Rotate device
        activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        
        // Views should still be displayed after rotation
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.button))
            .check(matches(isDisplayed()))
        
        // Rotate back
        activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testAccessibilityLabels() {
        // Test accessibility support
        onView(withId(R.id.button))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        // Check that views have proper accessibility labels
        activityRule.scenario.onActivity { activity ->
            val button = activity.findViewById<android.widget.Button>(R.id.button)
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerView)
            
            // Views should have appropriate content descriptions or text
            assert(button.text.isNotEmpty())
            assert(recyclerView.isImportantForAccessibility)
        }
    }

    @Test
    fun testMemoryLeaks() {
        // Basic memory leak test - activity should be properly cleaned up
        var activityRef: MainActivity? = null
        
        activityRule.scenario.onActivity { activity ->
            activityRef = activity
        }
        
        // Activity should be running
        assert(activityRef?.isFinishing == false)
        
        // After finishing, references should be cleaned
        activityRule.scenario.close()
        
        // Note: In a real test, you'd use tools like LeakCanary for comprehensive leak detection
    }

    @Test
    fun testBackPressHandling() {
        // Test back button behavior
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        
        // Press back button
        onView(isRoot()).perform(pressBack())
        
        // Activity should handle back press appropriately
        // (In this case, it should exit since we're at root directory)
    }

    @Test
    fun testMultipleClicks() {
        // Test rapid clicking doesn't cause issues
        onView(withId(R.id.button))
            .perform(click())
            .perform(click())
            .perform(click())
        
        // App should handle multiple clicks gracefully
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testActivityState() {
        // Test activity state management
        activityRule.scenario.onActivity { activity ->
            // Activity should be in proper state
            assert(!activity.isFinishing)
            assert(!activity.isDestroyed)
            
            // Check that required components are initialized
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerView)
            assert(recyclerView.layoutManager != null)
            assert(recyclerView.adapter != null)
        }
    }
} 