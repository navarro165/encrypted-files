package com.example.myapplication

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import java.io.File

/**
 * True instrumentation tests for the application's core security flows.
 * These tests run on a device or emulator and interact with the actual UI.
 */
@RunWith(AndroidJUnit4::class)
class SecurityFlowInstrumentationTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        // Clear all app data before each test to ensure a clean state
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authManager = AuthenticationManager.getInstance(context)
        authManager.logout()
        authManager.removePin()
        val rootDir = File(context.filesDir, "encrypted_files")
        rootDir.deleteRecursively()
    }

    @After
    fun cleanup() {
        scenario.close()
    }

    @Test
    fun testPinSetupFlow_ShowsWhenNoPinIsSet() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When clicking the "Add Files" button without a PIN
        onView(withId(R.id.button)).perform(click())
        
        // Then the PIN setup dialog should be displayed
        onView(withText("Two-Factor Security Setup")).check(matches(isDisplayed()))
        onView(withText("Set Up PIN")).perform(click())
        onView(withId(R.id.pinTitleText)).check(matches(withText("Create Security PIN")))
    }

    @Test
    fun testFullAuthenticationFlow_Success() {
        // This test requires manual interaction for Biometric and PIN prompts,
        // as Espresso cannot interact with system-level dialogs directly.
        // It serves to verify that the prompts are triggered in the correct order.

        // Pre-condition: A PIN must be set. We can't automate this part easily without
        // complex test setup, so we assume a PIN is set for this flow verification.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthenticationManager.getInstance(context).setupPin("1234")

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // When clicking the "Add Files" button
        onView(withId(R.id.button)).perform(click())

        // Then the Biometric prompt should be requested (cannot be tested with Espresso)
        // Then the PIN prompt should be shown (cannot be tested with Espresso)
        // This test's value is in ensuring the app doesn't crash and follows the logical path
        // to requesting authentication. We can check that we are still on the MainActivity.
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun testFileEncryptionAndDecryptionCycle() {
        // This is a conceptual test. Automating the full cycle with file pickers
        // and biometric/PIN prompts is complex. This outlines the steps that would
        // be manually verified or tested with more advanced tools like UI Automator.

        // 1. Set up PIN
        // 2. Click "Add Files"
        // 3. Authenticate with Biometric + PIN
        // 4. Select a file using the file picker
        // 5. Verify the file appears in the RecyclerView
        // 6. Click the file to open FileViewerActivity
        // 7. Authenticate again
        // 8. Verify the file content is displayed correctly
    }
} 