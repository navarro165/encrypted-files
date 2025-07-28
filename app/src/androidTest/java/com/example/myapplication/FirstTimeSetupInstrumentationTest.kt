package com.example.myapplication

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for first-time setup flow
 * Note: These tests require a device/emulator with biometric capabilities
 */
@RunWith(AndroidJUnit4::class)
class FirstTimeSetupInstrumentationTest {

    private lateinit var authenticationManager: AuthenticationManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        authenticationManager = AuthenticationManager.getInstance(context)
        
        // Clear any existing authentication state
        authenticationManager.logout()
        authenticationManager.removePin()
    }

    @Test
    fun testMainActivity_LoadsWithoutCrash() {
        // Given: Fresh app installation
        
        // When: Launching the app
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Then: Activity should load without crashing
                // This is a basic smoke test
            }
        }
    }

    @Test
    fun testAuthenticationManager_InitialState() {
        // Given: Fresh app installation
        
        // When: Checking initial state
        val isPinSet = authenticationManager.isPinSet()
        val authStatus = authenticationManager.getAuthenticationStatus()
        val isFirstTime = authenticationManager.isFirstTimeSetup()
        
        // Then: Should be in setup required state
        assert(!isPinSet)
        assert(authStatus == AuthenticationManager.AuthStatus.SETUP_REQUIRED)
        assert(isFirstTime)
    }

    @Test
    fun testPinSetup_ProgrammaticSetup() {
        // Given: Fresh app installation
        assert(!authenticationManager.isPinSet())
        
        // When: Setting up PIN programmatically
        val pin = "5678"
        val result = authenticationManager.setupPin(pin)
        
        // Then: PIN setup should succeed
        assert(result == AuthenticationManager.PinSetupResult.SUCCESS)
        assert(authenticationManager.isPinSet())
        assert(!authenticationManager.isFirstTimeSetup())
    }

    @Test
    fun testPinVerification_ProgrammaticVerification() {
        // Given: PIN is set up
        val pin = "5678"
        authenticationManager.setupPin(pin)
        assert(authenticationManager.isPinSet())
        
        // When: Verifying PIN
        val isValid = authenticationManager.verifyPin(pin)
        
        // Then: Should succeed
        assert(isValid)
    }

    @Test
    fun testTwoFactorAuth_ProgrammaticVerification() {
        // Given: PIN is set up
        val pin = "5678"
        authenticationManager.setupPin(pin)
        assert(authenticationManager.isPinSet())
        
        // When: Verifying second factor
        val isValid = authenticationManager.verifySecondFactor(pin)
        
        // Then: Should succeed
        assert(isValid)
        assert(authenticationManager.getAuthenticationStatus() == AuthenticationManager.AuthStatus.AUTHENTICATED)
    }

    @Test
    fun testWeakPin_Rejected() {
        // Given: Fresh app installation
        assert(!authenticationManager.isPinSet())
        
        // When: Trying to set up a weak PIN
        val weakPin = "0000"
        val result = authenticationManager.setupPin(weakPin)
        
        // Then: Should be rejected
        assert(result == AuthenticationManager.PinSetupResult.WEAK_PIN)
        assert(!authenticationManager.isPinSet())
    }

    @Test
    fun testInvalidPin_Rejected() {
        // Given: Fresh app installation
        assert(!authenticationManager.isPinSet())
        
        // When: Trying to set up an invalid PIN
        val invalidPin = "123"
        val result = authenticationManager.setupPin(invalidPin)
        
        // Then: Should be rejected
        assert(result == AuthenticationManager.PinSetupResult.INVALID_FORMAT)
        assert(!authenticationManager.isPinSet())
    }

    @Test
    fun testPinLockout_TooManyFailedAttempts() {
        // Given: PIN is set up
        val correctPin = "5678"
        val wrongPin = "1234"
        authenticationManager.setupPin(correctPin)
        
        // When: Making multiple failed attempts
        repeat(AuthenticationManager.MAX_PIN_ATTEMPTS) {
            authenticationManager.verifyPin(wrongPin)
        }
        
        // Then: PIN should be locked
        assert(authenticationManager.isPinLocked())
        assert(authenticationManager.getAuthenticationStatus() == AuthenticationManager.AuthStatus.LOCKED)
    }

    @Test
    fun testAuthenticationTimeout_AfterSuccessfulAuth() {
        // Given: PIN is set up and verified
        val pin = "5678"
        authenticationManager.setupPin(pin)
        authenticationManager.verifyPin(pin)
        assert(authenticationManager.getAuthenticationStatus() == AuthenticationManager.AuthStatus.AUTHENTICATED)
        
        // When: Simulating timeout (this would normally happen after 5 minutes)
        // For testing, we'll manually set the last auth timestamp to be old
        authenticationManager.setLastAuthenticationTimestampForTesting(
            System.currentTimeMillis() - AuthenticationManager.AUTHENTICATION_TIMEOUT - 1000
        )
        
        // Then: Should require re-authentication
        assert(authenticationManager.getAuthenticationStatus() == AuthenticationManager.AuthStatus.AUTH_REQUIRED)
    }

    @Test
    fun testFirstTimeSetupFlow_CompleteFlow() {
        // Given: Fresh app installation (no PIN set)
        assert(!authenticationManager.isPinSet())
        assert(authenticationManager.getAuthenticationStatus() == AuthenticationManager.AuthStatus.SETUP_REQUIRED)
        assert(authenticationManager.isFirstTimeSetup())
        
        // When: Setting up PIN
        val pin = "5678"
        val setupResult = authenticationManager.setupPin(pin)
        
        // Then: PIN setup should succeed
        assert(setupResult == AuthenticationManager.PinSetupResult.SUCCESS)
        assert(authenticationManager.isPinSet())
        assert(!authenticationManager.isFirstTimeSetup())
        
        // When: Verifying PIN (simulating second factor after biometric)
        val verifyResult = authenticationManager.verifySecondFactor(pin)
        
        // Then: Verification should succeed
        assert(verifyResult)
        assert(authenticationManager.getAuthenticationStatus() == AuthenticationManager.AuthStatus.AUTHENTICATED)
    }

    @Test
    fun testBiometricAvailability_Constants() {
        // Test that biometric constants are correctly defined
        assert(androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS == 0)
        assert(androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE == 12)
        assert(androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE == 1)
        assert(androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED == 11)
    }
}
 