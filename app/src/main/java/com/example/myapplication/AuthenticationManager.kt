package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import de.mkammerer.argon2.Argon2Factory
import android.annotation.SuppressLint

/**
 * Manages user authentication state with persistent storage.
 * Uses encrypted shared preferences to securely store authentication timestamps.
 */
class AuthenticationManager private constructor(context: Context) {
    
    enum class PinSetupResult {
        SUCCESS,
        WEAK_PIN,
        INVALID_FORMAT,
        ERROR
    }

    companion object {
        const val AUTHENTICATION_TIMEOUT = 5 * 60 * 1000L // 5 minutes
        private const val PREFS_NAME = "auth_prefs_v2"
        private const val AUTH_MASTER_KEY_ALIAS = "AuthMasterKey_v2"
        private const val KEY_LAST_AUTH = "last_authentication_timestamp"
        private const val KEY_FAILED_ATTEMPTS = "failed_authentication_attempts"
        private const val KEY_LOCKOUT_TIME = "authentication_lockout_time"
        private const val KEY_PIN_HASH = "pin_hash_v2"
        private const val KEY_PIN_SALT = "pin_salt_v2"
        private const val KEY_PIN_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKOUT = "pin_lockout_time"
        private const val KEY_PIN_SET = "pin_is_set"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION = 30 * 60 * 1000L // 30 minutes
        const val MAX_PIN_ATTEMPTS = 3
        const val PIN_LOCKOUT_DURATION = 60 * 60 * 1000L // 1 hour
        private val WEAK_PINS = setOf("1234", "0000", "1111", "9876", "2580", "1122", "1379")
        
        @Volatile
        private var INSTANCE: AuthenticationManager? = null
        
        fun getInstance(context: Context): AuthenticationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthenticationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        val spec = KeyGenParameterSpec.Builder(
            AUTH_MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUnlockedDeviceRequired(true)
                    setIsStrongBoxBacked(true)
                }
            }
            .build()
            
        val masterKey = MasterKey.Builder(context, AUTH_MASTER_KEY_ALIAS)
            .setKeyGenParameterSpec(spec)
            .build()
            
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isAuthenticationRequired(): Boolean {
        val lastAuth = encryptedPrefs.getLong(KEY_LAST_AUTH, 0L)
        return System.currentTimeMillis() - lastAuth > AUTHENTICATION_TIMEOUT
    }

    @SuppressLint("UseKtx")
    fun setLastAuthenticationTimestamp() {
        encryptedPrefs.edit()
            .putLong(KEY_LAST_AUTH, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Clears all authentication and session state but preserves PIN setup.
     */
    @SuppressLint("UseKtx")
    fun logout() {
        encryptedPrefs.edit()
            .remove(KEY_LAST_AUTH)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_TIME)
            .putInt(KEY_PIN_ATTEMPTS, 0)
            .remove(KEY_PIN_LOCKOUT)
            .apply()
    }
    
    /**
     * For testing purposes only - not exposed publicly
     */
    @SuppressLint("UseKtx")
    internal fun setLastAuthenticationTimestampForTesting(timestamp: Long) {
        encryptedPrefs.edit()
            .putLong(KEY_LAST_AUTH, timestamp)
            .apply()
    }
    
    /**
     * Check if authentication is locked due to too many failed attempts
     */
    fun isAuthenticationLocked(): Boolean {
        val lockoutTime = encryptedPrefs.getLong(KEY_LOCKOUT_TIME, 0L)
        return System.currentTimeMillis() < lockoutTime
    }
    
    /**
     * Get remaining lockout time in milliseconds
     */
    fun getRemainingLockoutTime(): Long {
        val lockoutTime = encryptedPrefs.getLong(KEY_LOCKOUT_TIME, 0L)
        val remaining = lockoutTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }
    
    /**
     * Record a failed authentication attempt
     */
    @SuppressLint("UseKtx")
    fun recordFailedAttempt() {
        val failedAttempts = encryptedPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = encryptedPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, failedAttempts)

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            // Lock authentication for LOCKOUT_DURATION
            val lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION
            editor.putLong(KEY_LOCKOUT_TIME, lockoutUntil)
        }
        
        editor.apply()
    }
    
    /**
     * Reset failed attempts counter on successful authentication
     */
    @SuppressLint("UseKtx")
    fun resetFailedAttempts() {
        encryptedPrefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .remove(KEY_LOCKOUT_TIME)
            .apply()
    }
    
    /**
     * Get current number of failed attempts
     */
    fun getFailedAttempts(): Int {
        return encryptedPrefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }
    
    /**
     * Set up a 4-digit PIN as backup authentication
     */
    fun setupPin(pin: String): PinSetupResult {
        if (!isValidPin(pin)) {
            return PinSetupResult.INVALID_FORMAT
        }
        if (isWeakPin(pin)) {
            return PinSetupResult.WEAK_PIN
        }
        
        try {
            // Generate a random salt
            val salt = ByteArray(32) // Increased salt size for Argon2
            SecureRandom().nextBytes(salt)
            
            // Hash the PIN with Argon2
            val pinHash = hashPin(pin, salt)
            
            // Store the hash and salt
            @SuppressLint("UseKtx")
            encryptedPrefs.edit()
                .putString(KEY_PIN_HASH, pinHash)
                .putString(KEY_PIN_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                .putBoolean(KEY_PIN_SET, true)
                .putInt(KEY_PIN_ATTEMPTS, 0)
                .remove(KEY_PIN_LOCKOUT)
                .apply()
            
            return PinSetupResult.SUCCESS
        } catch (e: Exception) {
            return PinSetupResult.ERROR
        }
    }
    
    /**
     * Verify PIN authentication
     */
    fun verifyPin(pin: String): Boolean {
        if (!isPinSet() || isPinLocked()) {
            return false
        }
        
        if (!isValidPin(pin)) {
            recordPinFailure()
            return false
        }
        
        try {
            val storedHashB64 = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return false
            val storedSaltB64 = encryptedPrefs.getString(KEY_PIN_SALT, null) ?: return false
            
            val storedHash = storedHashB64
            val storedSalt = android.util.Base64.decode(storedSaltB64, android.util.Base64.NO_WRAP)
            
            val inputHash = hashPin(pin, storedSalt)
            
            val isValid = storedHash == inputHash
            
            if (isValid) {
                // Reset PIN attempts on successful authentication
                @SuppressLint("UseKtx")
                encryptedPrefs.edit()
                    .putInt(KEY_PIN_ATTEMPTS, 0)
                    .remove(KEY_PIN_LOCKOUT)
                    .apply()
                
                // Also reset biometric attempts
                resetFailedAttempts()
                setLastAuthenticationTimestamp()
            } else {
                recordPinFailure()
            }
            
            return isValid
        } catch (e: Exception) {
            recordPinFailure()
            return false
        }
    }
    
    /**
     * Check if PIN is set up
     */
    fun isPinSet(): Boolean {
        return encryptedPrefs.getBoolean(KEY_PIN_SET, false)
    }
    
    /**
     * Check if PIN authentication is locked due to too many failed attempts
     */
    fun isPinLocked(): Boolean {
        val lockoutTime = encryptedPrefs.getLong(KEY_PIN_LOCKOUT, 0L)
        return System.currentTimeMillis() < lockoutTime
    }
    
    /**
     * Get remaining PIN lockout time in milliseconds
     */
    fun getRemainingPinLockoutTime(): Long {
        val lockoutTime = encryptedPrefs.getLong(KEY_PIN_LOCKOUT, 0L)
        val remaining = lockoutTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }
    
    /**
     * Get the absolute timestamp when the PIN lockout ends
     */
    fun getPinLockoutEndTime(): Long {
        return encryptedPrefs.getLong(KEY_PIN_LOCKOUT, 0L)
    }
    
    /**
     * Get current number of PIN failed attempts
     */
    fun getPinFailedAttempts(): Int {
        return encryptedPrefs.getInt(KEY_PIN_ATTEMPTS, 0)
    }
    
    /**
     * Record a failed PIN attempt
     */
    @SuppressLint("UseKtx")
    private fun recordPinFailure() {
        val attempts = encryptedPrefs.getInt(KEY_PIN_ATTEMPTS, 0) + 1
        val editor = encryptedPrefs.edit().putInt(KEY_PIN_ATTEMPTS, attempts)
        
        if (attempts >= MAX_PIN_ATTEMPTS) {
            // Lock PIN authentication
            val lockoutUntil = System.currentTimeMillis() + PIN_LOCKOUT_DURATION
            editor.putLong(KEY_PIN_LOCKOUT, lockoutUntil)
        }
        
        editor.apply()
    }
    
    /**
     * Remove PIN authentication (for security reset)
     */
    @SuppressLint("UseKtx")
    fun removePin() {
        encryptedPrefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_SET)
            .remove(KEY_PIN_ATTEMPTS)
            .remove(KEY_PIN_LOCKOUT)
            .apply()
    }
    
    /**
     * Validate PIN format (4 digits)
     */
    private fun isValidPin(pin: String): Boolean {
        return pin.length == 4 && pin.all { it.isDigit() }
    }
    
    /**
     * Check for weak or common PINs
     */
    private fun isWeakPin(pin: String): Boolean {
        return WEAK_PINS.contains(pin)
    }
    
    /**
     * Hash PIN with Argon2
     */
    private fun hashPin(pin: String, salt: ByteArray): String {
        val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
        // Stronger parameters for Argon2
        val hash = argon2.hash(
            4, // iterations
            131072, // 128 MB memory
            2, // parallelism
            pin.toCharArray(),
            java.nio.charset.StandardCharsets.UTF_8
        )
        return hash
    }
    
    /**
     * Two-factor authentication: Verify both biometric AND PIN
     * Call this after successful biometric authentication
     */
    fun verifySecondFactor(pin: String): Boolean {
        if (!isPinSet()) {
            // If no PIN is set up, this is a setup issue
            return false
        }
        
        return verifyPin(pin)
    }
    
    /**
     * Check if two-factor authentication is properly configured
     */
    fun isTwoFactorSetup(): Boolean {
        return isPinSet()
    }
    
    /**
     * Check if authentication is required (either initial or timeout)
     */
    fun isAnyAuthenticationRequired(): Boolean {
        return isAuthenticationRequired()
    }
    
    /**
     * Check if user can attempt authentication (not locked out)
     */
    fun canAttemptAuthentication(): Boolean {
        return !isAuthenticationLocked() && !isPinLocked()
    }
    
    /**
     * Get authentication status for UI
     */
    fun getAuthenticationStatus(): AuthStatus {
        return when {
            !isPinSet() -> AuthStatus.SETUP_REQUIRED
            isAuthenticationLocked() || isPinLocked() -> AuthStatus.LOCKED
            isAuthenticationRequired() -> AuthStatus.AUTH_REQUIRED
            else -> AuthStatus.AUTHENTICATED
        }
    }
    

    
    enum class AuthStatus {
        SETUP_REQUIRED,    // PIN not set up
        LOCKED,           // Too many failed attempts
        AUTH_REQUIRED,    // Need both biometric + PIN
        AUTHENTICATED     // Successfully authenticated
    }
}
