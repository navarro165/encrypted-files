package com.example.myapplication

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure memory buffer that encrypts sensitive data in memory to prevent
 * memory dump attacks and cold boot attacks. Provides automatic memory
 * wiping and encrypted storage for ultra-sensitive operations.
 */
class SecureMemoryBuffer private constructor(
    private val capacity: Int,
    private val memoryKey: SecretKey
) {
    
    companion object {
        private const val MEMORY_KEY_ALIAS = "SecureMemoryKey"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        
        /**
         * Create a new secure memory buffer with specified capacity
         */
        fun create(capacity: Int): SecureMemoryBuffer {
            require(capacity > 0) { "Capacity must be positive" }
            require(capacity <= 10 * 1024 * 1024) { "Maximum capacity is 10MB" }
            
            val memoryKey = generateMemoryEncryptionKey()
            return SecureMemoryBuffer(capacity, memoryKey)
        }
        
        private fun generateMemoryEncryptionKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyAlias = "${MEMORY_KEY_ALIAS}_${System.currentTimeMillis()}"
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // Memory encryption shouldn't require user auth
                .build()
                
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }
    }
    
    private var encryptedBuffer: ByteArray? = null
    private var currentIV: ByteArray? = null
    private var isSealed = false
    
    /**
     * Store sensitive data in encrypted form in memory
     */
    fun write(data: ByteArray) {
        require(!isSealed) { "Buffer is sealed and cannot be modified" }
        require(data.size <= capacity) { "Data exceeds buffer capacity" }
        
        try {
            // Generate new IV for each write
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            
            // Encrypt the data
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, memoryKey, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(data)
            
            // Clear previous encrypted buffer if it exists
            encryptedBuffer?.let { secureWipeArray(it) }
            
            // Store encrypted data
            encryptedBuffer = encrypted
            currentIV = iv
            
            // Immediately wipe the input data for security
            secureWipeArray(data)
            
        } catch (e: Exception) {
            throw SecurityException("Failed to encrypt memory buffer", e)
        }
    }
    
    /**
     * Read and decrypt data from the secure buffer
     */
    fun read(): ByteArray? {
        require(!isSealed) { "Buffer is sealed and cannot be read" }
        
        val encrypted = encryptedBuffer ?: return null
        val iv = currentIV ?: return null
        
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, memoryKey, GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt memory buffer", e)
        }
    }
    
    /**
     * Execute a function with the decrypted data, automatically cleaning up afterwards
     */
    fun <T> withDecryptedData(operation: (ByteArray) -> T): T? {
        var decryptedData: ByteArray? = null
        try {
            decryptedData = read()
            return if (decryptedData != null) {
                operation(decryptedData)
            } else {
                null
            }
        } finally {
            // Always wipe decrypted data from memory
            decryptedData?.let { secureWipeArray(it) }
        }
    }
    
    /**
     * Seal the buffer to prevent further access (emergency security measure)
     */
    fun seal() {
        isSealed = true
        encryptedBuffer?.let { secureWipeArray(it) }
        currentIV?.let { secureWipeArray(it) }
        encryptedBuffer = null
        currentIV = null
    }
    
    /**
     * Clear all data and clean up resources
     */
    fun destroy() {
        encryptedBuffer?.let { secureWipeArray(it) }
        currentIV?.let { secureWipeArray(it) }
        encryptedBuffer = null
        currentIV = null
        isSealed = true
        
        // Clean up the memory encryption key
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            // Note: In a real implementation, you'd track the key alias to delete it
        } catch (e: Exception) {
            // Best effort cleanup
        }
    }
    
    /**
     * Check if buffer contains data
     */
    fun hasData(): Boolean = encryptedBuffer != null && !isSealed
    
    /**
     * Get the encrypted size (for monitoring purposes)
     */
    fun getEncryptedSize(): Int = encryptedBuffer?.size ?: 0
    
    /**
     * Secure memory wiping with multiple passes
     */
    private fun secureWipeArray(array: ByteArray) {
        val random = SecureRandom()
        
        // Pass 1: Random data
        random.nextBytes(array)
        
        // Pass 2: All 0xFF
        Arrays.fill(array, 0xFF.toByte())
        
        // Pass 3: All 0x00
        Arrays.fill(array, 0x00.toByte())
        
        // Pass 4: Random data again
        random.nextBytes(array)
        
        // Final pass: Zeros
        Arrays.fill(array, 0.toByte())
    }
    
    /**
     * Auto-cleanup when garbage collected
     */
    @Suppress("deprecation")
    protected fun finalize() {
        destroy()
    }
}

/**
 * Secure memory manager for handling multiple secure buffers
 */
class SecureMemoryManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: SecureMemoryManager? = null
        
        fun getInstance(): SecureMemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureMemoryManager().also { INSTANCE = it }
            }
        }
    }
    
    private val activeBuffers = mutableMapOf<String, SecureMemoryBuffer>()
    private val maxBuffers = 10
    private val maxTotalMemory = 50 * 1024 * 1024 // 50MB total limit
    
    /**
     * Create a new secure buffer with a unique identifier
     */
    fun createBuffer(id: String, capacity: Int): SecureMemoryBuffer {
        synchronized(activeBuffers) {
            require(activeBuffers.size < maxBuffers) { "Maximum number of secure buffers exceeded" }
            require(!activeBuffers.containsKey(id)) { "Buffer with ID '$id' already exists" }
            
            val totalMemory = activeBuffers.values.sumOf { it.getEncryptedSize() }
            require(totalMemory + capacity <= maxTotalMemory) { "Total secure memory limit exceeded" }
            
            val buffer = SecureMemoryBuffer.create(capacity)
            activeBuffers[id] = buffer
            return buffer
        }
    }
    
    /**
     * Get an existing secure buffer by ID
     */
    fun getBuffer(id: String): SecureMemoryBuffer? {
        synchronized(activeBuffers) {
            return activeBuffers[id]
        }
    }
    
    /**
     * Destroy a specific buffer
     */
    fun destroyBuffer(id: String) {
        synchronized(activeBuffers) {
            activeBuffers[id]?.destroy()
            activeBuffers.remove(id)
        }
    }
    
    /**
     * Emergency: destroy all buffers
     */
    fun emergencyDestroy() {
        synchronized(activeBuffers) {
            activeBuffers.values.forEach { it.destroy() }
            activeBuffers.clear()
        }
    }
    
    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        synchronized(activeBuffers) {
            val totalBuffers = activeBuffers.size
            val totalMemory = activeBuffers.values.sumOf { it.getEncryptedSize() }
            val activeBuffers = activeBuffers.values.count { it.hasData() }
            
            return MemoryStats(totalBuffers, activeBuffers, totalMemory)
        }
    }
    
    data class MemoryStats(
        val totalBuffers: Int,
        val activeBuffers: Int,
        val totalMemoryUsed: Int
    )
} 