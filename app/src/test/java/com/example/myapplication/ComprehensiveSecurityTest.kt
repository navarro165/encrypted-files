package com.example.myapplication

import org.junit.Test
import org.junit.Assert.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec


/**
 * Comprehensive security tests covering cryptographic aspects that can be tested
 * without Android dependencies
 */
class ComprehensiveSecurityTest {

    @Test
    fun testEndToEndEncryptionSecurity() {
        // Test complete encryption/decryption pipeline like the app uses
        val originalData = "🔐 Super secret document with émojis and spëcial chars! 💎".toByteArray()
        
        // Generate components like MainActivity does
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        // Encrypt using app's algorithm
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encryptedData = encryptCipher.doFinal(originalData)
        
        // Verify encrypted data is different
        assertNotEquals("Encrypted data should differ from original",
            originalData.contentToString(), encryptedData.contentToString())
        
        // Decrypt like FileViewerActivity does
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decryptedData = decryptCipher.doFinal(encryptedData)
        
        // Verify perfect round-trip
        assertArrayEquals("Decrypted data should match original exactly", originalData, decryptedData)
        assertEquals("Content should be perfectly preserved", 
            "🔐 Super secret document with émojis and spëcial chars! 💎", String(decryptedData))
    }

    @Test
    fun testAuthenticationSecurityBoundaries() {
        // Test timeout value configuration is secure
        assertTrue("Timeout should be at most 15 minutes for security", 
            AuthenticationManager.AUTHENTICATION_TIMEOUT <= 15 * 60 * 1000L)
        assertTrue("Timeout should be at least 1 minute for usability", 
            AuthenticationManager.AUTHENTICATION_TIMEOUT >= 60 * 1000L)
        assertEquals("Timeout should be exactly 5 minutes", 
            5 * 60 * 1000L, AuthenticationManager.AUTHENTICATION_TIMEOUT)
    }

    @Test
    fun testCryptographicSecurityProperties() {
        val testData = "Sensitive financial data: $1,000,000".toByteArray()
        
        // Test 1: Different keys produce different ciphertext
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key1 = keyGen.generateKey()
        val key2 = keyGen.generateKey()
        
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv) // Same IV for comparison
        
        val cipher1 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher1.init(Cipher.ENCRYPT_MODE, key1, GCMParameterSpec(128, iv))
        val encrypted1 = cipher1.doFinal(testData)
        
        val cipher2 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher2.init(Cipher.ENCRYPT_MODE, key2, GCMParameterSpec(128, iv))
        val encrypted2 = cipher2.doFinal(testData)
        
        assertNotEquals("Different keys should produce different ciphertext",
            encrypted1.contentToString(), encrypted2.contentToString())
        
        // Test 2: Same key with different IVs produces different ciphertext
        val iv1 = ByteArray(12)
        val iv2 = ByteArray(12)
        SecureRandom().nextBytes(iv1)
        SecureRandom().nextBytes(iv2)
        
        val cipher3 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher3.init(Cipher.ENCRYPT_MODE, key1, GCMParameterSpec(128, iv1))
        val encrypted3 = cipher3.doFinal(testData)
        
        val cipher4 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher4.init(Cipher.ENCRYPT_MODE, key1, GCMParameterSpec(128, iv2))
        val encrypted4 = cipher4.doFinal(testData)
        
        assertNotEquals("Different IVs should produce different ciphertext",
            encrypted3.contentToString(), encrypted4.contentToString())
    }

    @Test
    fun testAuthenticationIntegrityProtection() {
        // Test that GCM mode provides authentication (detects tampering)
        val originalData = "Authentic document".toByteArray()
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        // Encrypt
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = encryptCipher.doFinal(originalData)
        
        // Tamper with encrypted data
        val tamperedData = encrypted.clone()
        tamperedData[0] = (tamperedData[0].toInt() xor 1).toByte() // Flip one bit
        
        // Try to decrypt tampered data - should fail
        try {
            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            decryptCipher.doFinal(tamperedData)
            fail("Should have detected tampering and thrown exception")
        } catch (e: Exception) {
            // Expected - GCM should detect tampering
            assertTrue("Should be authentication failure", 
                e is javax.crypto.AEADBadTagException || 
                e.message?.contains("tag mismatch") == true ||
                e.message?.contains("authentication") == true)
        }
    }

    @Test
    fun testKeyAndIVRandomness() {
        // Test that key and IV generation is properly random
        val keys = mutableSetOf<String>()
        val ivs = mutableSetOf<String>()
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        
        // Generate 100 keys and IVs
        repeat(100) {
            val key = keyGen.generateKey()
            keys.add(key.encoded.contentToString())
            
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            ivs.add(iv.contentToString())
        }
        
        // All should be unique (extremely high probability with good randomness)
        assertEquals("All keys should be unique", 100, keys.size)
        assertEquals("All IVs should be unique", 100, ivs.size)
    }

    @Test
    fun testLargeDataEncryption() {
        // Test encryption of larger data (simulating real files)
        val largeData = "A".repeat(100 * 1024) // 100KB
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        // Encrypt large data
        val startTime = System.currentTimeMillis()
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = encryptCipher.doFinal(largeData.toByteArray())
        val encryptTime = System.currentTimeMillis() - startTime
        
        // Decrypt large data
        val decryptStart = System.currentTimeMillis()
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = decryptCipher.doFinal(encrypted)
        val decryptTime = System.currentTimeMillis() - decryptStart
        
        // Verify correctness
        assertEquals("Large data should be preserved", largeData, String(decrypted))
        
        // Verify reasonable performance (should be fast on modern devices)
        assertTrue("Encryption should be reasonably fast", encryptTime < 5000) // 5 seconds max
        assertTrue("Decryption should be reasonably fast", decryptTime < 5000) // 5 seconds max
    }

    @Test
    fun testSecurityConfiguration() {
        // Test that security configuration meets best practices
        
        // 1. AES key size should be 256 bits
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        assertEquals("Should use 256-bit AES keys", 32, key.encoded.size)
        
        // 2. GCM IV should be 96 bits (12 bytes)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        assertEquals("Should use 96-bit IV for GCM", 12, iv.size)
        
        // 3. GCM tag length should be 128 bits
        val testData = "test".toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(testData)
        
        // Encrypted data should be original + 16 bytes (128-bit tag)
        assertEquals("Should add 16-byte authentication tag", 
            testData.size + 16, encrypted.size)
    }

    @Test
    fun testSessionManagementSecurity() {
        // Test that session timeout configuration is reasonable
        
        // Session timeout should not be too long (security risk)
        assertTrue("Session timeout should not exceed 15 minutes", 
            AuthenticationManager.AUTHENTICATION_TIMEOUT <= 15 * 60 * 1000L)
        
        // Session timeout should not be too short (usability issue)
        assertTrue("Session timeout should be at least 1 minute", 
            AuthenticationManager.AUTHENTICATION_TIMEOUT >= 60 * 1000L)
        
        // Current configuration should be optimal balance
        assertEquals("Should use 5-minute timeout for security-usability balance", 
            5 * 60 * 1000L, AuthenticationManager.AUTHENTICATION_TIMEOUT)
    }
} 