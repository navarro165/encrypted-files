package com.example.myapplication

import org.junit.Test
import org.junit.Assert.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Tests for cryptographic operations used in the app.
 * These test the underlying crypto logic without Android dependencies.
 */
class CryptographyTest {

    @Test
    fun testAESKeyGeneration() {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        
        val key1 = keyGenerator.generateKey()
        val key2 = keyGenerator.generateKey()
        
        // Keys should be different each time
        assertNotEquals(key1.encoded.contentToString(), key2.encoded.contentToString())
        
        // Keys should be 256 bits (32 bytes)
        assertEquals(32, key1.encoded.size)
        assertEquals("AES", key1.algorithm)
    }

    @Test
    fun testSecureRandomIVGeneration() {
        val iv1 = ByteArray(12)
        val iv2 = ByteArray(12)
        
        SecureRandom().nextBytes(iv1)
        SecureRandom().nextBytes(iv2)
        
        // IVs should be different each time
        assertNotEquals(iv1.contentToString(), iv2.contentToString())
        
        // IVs should be 96 bits (12 bytes) for GCM
        assertEquals(12, iv1.size)
        assertEquals(12, iv2.size)
    }

    @Test
    fun testAESGCMEncryptionDecryption() {
        val originalData = "This is secret test data! 🔐🔒".toByteArray()
        
        // Generate key and IV like the app does
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val key = keyGenerator.generateKey()
        
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        // Encrypt
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encryptedData = encryptCipher.doFinal(originalData)
        
        // Decrypt
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decryptedData = decryptCipher.doFinal(encryptedData)
        
        // Should match original
        assertArrayEquals(originalData, decryptedData)
        assertEquals("This is secret test data! 🔐🔒", String(decryptedData))
    }

    @Test
    fun testDifferentKeysProduceDifferentCiphertext() {
        val originalData = "Test data for encryption".toByteArray()
        
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val key1 = keyGenerator.generateKey()
        val key2 = keyGenerator.generateKey()
        
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv) // Same IV for comparison
        
        // Encrypt with key1
        val cipher1 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher1.init(Cipher.ENCRYPT_MODE, key1, GCMParameterSpec(128, iv))
        val encrypted1 = cipher1.doFinal(originalData)
        
        // Encrypt with key2 (same IV)
        val cipher2 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher2.init(Cipher.ENCRYPT_MODE, key2, GCMParameterSpec(128, iv))
        val encrypted2 = cipher2.doFinal(originalData)
        
        // Should produce different encrypted results
        assertNotEquals(encrypted1.contentToString(), encrypted2.contentToString())
    }

    @Test
    fun testGCMAuthenticationFailsWithWrongKey() {
        val originalData = "Sensitive data".toByteArray()
        
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val correctKey = keyGenerator.generateKey()
        val wrongKey = keyGenerator.generateKey()
        
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        // Encrypt with correct key
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, correctKey, GCMParameterSpec(128, iv))
        val encryptedData = encryptCipher.doFinal(originalData)
        
        // Try to decrypt with wrong key - should fail
        try {
            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(Cipher.DECRYPT_MODE, wrongKey, GCMParameterSpec(128, iv))
            decryptCipher.doFinal(encryptedData)
            fail("Should have thrown exception for wrong key")
        } catch (e: Exception) {
            // Expected - GCM should detect tampering/wrong key
            assertTrue(e.message?.contains("tag mismatch") == true || 
                      e.message?.contains("authentication") == true ||
                      e is javax.crypto.AEADBadTagException)
        }
    }
} 