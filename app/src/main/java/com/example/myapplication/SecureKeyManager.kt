package com.example.myapplication

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.annotation.SuppressLint
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.*

/**
 * Secure key management using a single, hardware-backed master key from the Android Keystore.
 * This class provides access to a biometric-protected key for direct encryption and decryption.
 */
class SecureKeyManager(context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val masterKeyAlias: String by lazy {
        getOrCreateMasterKeyAlias(context)
    }

    private fun getOrCreateMasterKeyAlias(context: Context): String {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            KEY_ALIAS_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        
        var alias = sharedPreferences.getString(KEY_ALIAS_NAME, null)
        if (alias == null) {
            alias = "EncryptedFilesMasterKey_" + UUID.randomUUID().toString()
            @SuppressLint("UseKtx")
            sharedPreferences.edit().putString(KEY_ALIAS_NAME, alias).apply()
        }
        return alias
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return (keyStore.getKey(masterKeyAlias, null) as? SecretKey) ?: generateBiometricProtectedKey()
    }

    /**
     * Generates a secure master key in Android Keystore that requires biometric authentication.
     * The key is configured to be invalidated if new biometrics are enrolled.
     */
    private fun generateBiometricProtectedKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            masterKeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        // Use modern authentication parameters for API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTH_TIMEOUT_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            // Fallback to deprecated method for older API levels
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_TIMEOUT_SECONDS)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Provides a Cipher for encryption. The user must have authenticated within the timeout period.
     * The IV is generated randomly and must be stored alongside the ciphertext.
     *
     * @return A Cipher instance ready for encryption.
     * @throws KeyPermanentlyInvalidatedException if the key is invalidated.
     */
    fun getEncryptionCipher(): Cipher {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        return cipher
    }

    /**
     * Provides a Cipher for decryption. The user must have authenticated within the timeout period.
     *
     * @param iv The initialization vector that was used for encryption.
     * @return A Cipher instance ready for decryption.
     * @throws KeyPermanentlyInvalidatedException if the key is invalidated.
     */
    fun getDecryptionCipher(iv: ByteArray): Cipher {
        val masterKey = getOrCreateMasterKey()
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        return cipher
    }

    /**
     * Deletes the master key from the Keystore. This is a destructive operation
     * and will render all encrypted data permanently unreadable.
     */
    fun deleteMasterKey() {
        if (keyStore.containsAlias(masterKeyAlias)) {
            keyStore.deleteEntry(masterKeyAlias)
        }
    }
    
    /**
     * Securely process encrypted file data with automatic memory cleanup
     */
    fun processFileDataSecurely(
        encryptedData: ByteArray, 
        cipher: Cipher,
        processor: (ByteArray) -> Unit
    ) {
        var plaintext: ByteArray? = null
        try {
            plaintext = cipher.doFinal(encryptedData)
            processor(plaintext)
        } finally {
            // Securely wipe the plaintext from memory
            plaintext?.let { secureWipeMemory(it) }
        }
    }
    
    /**
     * Securely clear sensitive byte arrays from memory using multiple passes
     */
    private fun secureWipeMemory(data: ByteArray) {
        val random = SecureRandom()
        
        // Pass 1: Random data
        random.nextBytes(data)
        
        // Pass 2: All 0xFF
        Arrays.fill(data, 0xFF.toByte())
        
        // Pass 3: All 0x00
        Arrays.fill(data, 0x00.toByte())
        
        // Pass 4: Random data again
        random.nextBytes(data)
        
        // Final pass: Zeros
        Arrays.fill(data, 0.toByte())
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // in bits
        private const val AUTH_TIMEOUT_SECONDS = 300 // 5 minutes
        private const val KEY_ALIAS_PREFS = "secure_key_alias_prefs"
        private const val KEY_ALIAS_NAME = "master_key_alias_v3"
    }
}