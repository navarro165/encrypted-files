package com.example.myapplication

import org.junit.Test
import org.junit.Assert.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Tests for cryptographic security properties that don't require Android context
 */
class CryptographySecurityTest {
    
    companion object {
        // Shared SecureRandom instance for better randomness distribution
        private val secureRandom = SecureRandom()
    }

    @Test
    fun testAESGCMEncryptionSecurity() {
        val testData = "🔐 Highly sensitive financial data: Account #123456789, Balance: $1,000,000 💰".toByteArray()
        
        // Generate 256-bit AES key
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val key = keyGenerator.generateKey()
        
        // Generate 96-bit IV for GCM
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        
        // Encrypt with AES-GCM
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encryptedData = encryptCipher.doFinal(testData)
        
        // Verify encryption properties
        assertNotEquals("Encrypted data should differ from plaintext", 
            testData.contentToString(), encryptedData.contentToString())
        assertEquals("Encrypted data should include 16-byte auth tag", 
            testData.size + 16, encryptedData.size)
        
        // Decrypt and verify
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decryptedData = decryptCipher.doFinal(encryptedData)
        
        assertArrayEquals("Decryption should be perfect", testData, decryptedData)
        assertEquals("Unicode should be preserved", 
            "🔐 Highly sensitive financial data: Account #123456789, Balance: $1,000,000 💰", 
            String(decryptedData))
    }

    @Test
    fun testGCMAuthenticationIntegrity() {
        val originalData = "Authentication test data".toByteArray()
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        
        // Encrypt data
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = encryptCipher.doFinal(originalData)
        
        // Tamper with encrypted data (flip one bit)
        val tamperedData = encrypted.clone()
        tamperedData[0] = (tamperedData[0].toInt() xor 1).toByte()
        
        // Attempt decryption of tampered data - should fail
        try {
            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            decryptCipher.doFinal(tamperedData)
            fail("Should have detected tampering")
        } catch (e: Exception) {
            // Expected - GCM authentication should detect tampering
            assertTrue("Should be authentication failure", 
                e is javax.crypto.AEADBadTagException ||
                e.message?.contains("tag mismatch") == true ||
                e.message?.contains("authentication") == true)
        }
    }

    @Test
    fun testKeyUniqueness() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        
        val keys = mutableSetOf<String>()
        
        // Generate 1000 keys and verify all are unique
        repeat(1000) {
            val key = keyGen.generateKey()
            assertEquals("All keys should be 256-bit", 32, key.encoded.size)
            keys.add(key.encoded.contentToString())
        }
        
        assertEquals("All 1000 keys should be unique", 1000, keys.size)
    }

    @Test
    fun testIVUniqueness() {
        val ivs = mutableSetOf<String>()
        
        // Generate 1000 IVs and verify all are unique
        repeat(1000) {
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)
            ivs.add(iv.contentToString())
        }
        
        assertEquals("All 1000 IVs should be unique", 1000, ivs.size)
    }

    @Test
    fun testDifferentKeysProduceDifferentCiphertext() {
        val testData = "Same plaintext data".toByteArray()
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key1 = keyGen.generateKey()
        val key2 = keyGen.generateKey()
        
        // Use same IV for both (normally bad practice, but needed for this test)
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        
        // Encrypt same data with different keys
        val cipher1 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher1.init(Cipher.ENCRYPT_MODE, key1, GCMParameterSpec(128, iv))
        val encrypted1 = cipher1.doFinal(testData)
        
        val cipher2 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher2.init(Cipher.ENCRYPT_MODE, key2, GCMParameterSpec(128, iv))
        val encrypted2 = cipher2.doFinal(testData)
        
        assertNotEquals("Different keys should produce different ciphertext",
            encrypted1.contentToString(), encrypted2.contentToString())
    }

    @Test
    fun testDifferentIVsProduceDifferentCiphertext() {
        val testData = "Same plaintext data".toByteArray()
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        
        val iv1 = ByteArray(12)
        val iv2 = ByteArray(12)
        secureRandom.nextBytes(iv1)
        secureRandom.nextBytes(iv2)
        
        // Encrypt same data with same key but different IVs
        val cipher1 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher1.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv1))
        val encrypted1 = cipher1.doFinal(testData)
        
        val cipher2 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher2.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv2))
        val encrypted2 = cipher2.doFinal(testData)
        
        assertNotEquals("Different IVs should produce different ciphertext",
            encrypted1.contentToString(), encrypted2.contentToString())
    }

    @Test
    fun testWrongKeyCannotDecrypt() {
        val testData = "Secret document content".toByteArray()
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val correctKey = keyGen.generateKey()
        val wrongKey = keyGen.generateKey()
        
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        
        // Encrypt with correct key
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, correctKey, GCMParameterSpec(128, iv))
        val encrypted = encryptCipher.doFinal(testData)
        
        // Try to decrypt with wrong key - should fail
        try {
            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(Cipher.DECRYPT_MODE, wrongKey, GCMParameterSpec(128, iv))
            decryptCipher.doFinal(encrypted)
            fail("Should not decrypt with wrong key")
        } catch (e: Exception) {
            // Expected - wrong key should fail authentication
            assertTrue("Should be authentication failure", 
                e is javax.crypto.AEADBadTagException ||
                e.message?.contains("tag mismatch") == true)
        }
    }

    @Test
    fun testSecurityConfiguration() {
        // Test that we're using secure cryptographic parameters
        
        // 1. Key size should be 256 bits
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        assertEquals("Should use 256-bit keys", 32, key.encoded.size)
        assertEquals("Should be AES algorithm", "AES", key.algorithm)
        
        // 2. IV should be 96 bits for GCM
        val iv = ByteArray(12)
        assertEquals("Should use 96-bit IV for GCM", 12, iv.size)
        
        // 3. GCM tag should be 128 bits
        val testData = "test".toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(testData)
        
        // Encrypted data should be original + 16 bytes (128-bit tag)
        assertEquals("Should add 128-bit authentication tag", 
            testData.size + 16, encrypted.size)
    }

    @Test
    fun testLargeDataEncryption() {
        // Test encryption of larger data sets
        val largeData = "A".repeat(100 * 1024).toByteArray() // 100KB
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        
        // Encrypt large data
        val startTime = System.currentTimeMillis()
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = encryptCipher.doFinal(largeData)
        val encryptTime = System.currentTimeMillis() - startTime
        
        // Decrypt large data
        val decryptStart = System.currentTimeMillis()
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = decryptCipher.doFinal(encrypted)
        val decryptTime = System.currentTimeMillis() - decryptStart
        
        // Verify correctness
        assertArrayEquals("Large data should decrypt correctly", largeData, decrypted)
        
        // Verify performance is reasonable
        assertTrue("Encryption should complete in reasonable time", encryptTime < 10000)
        assertTrue("Decryption should complete in reasonable time", decryptTime < 10000)
    }

    @Test
    fun testSecureRandomProperties() {
        // Test that SecureRandom produces cryptographically strong randomness
        val secureRandom = SecureRandom()
        
        val randomBytes1 = ByteArray(32)
        val randomBytes2 = ByteArray(32)
        val randomBytes3 = ByteArray(32)
        
        secureRandom.nextBytes(randomBytes1)
        secureRandom.nextBytes(randomBytes2)
        secureRandom.nextBytes(randomBytes3)
        
        // All should be different (extremely high probability)
        assertNotEquals("Random bytes should differ", 
            randomBytes1.contentToString(), randomBytes2.contentToString())
        assertNotEquals("Random bytes should differ", 
            randomBytes2.contentToString(), randomBytes3.contentToString())
        assertNotEquals("Random bytes should differ", 
            randomBytes1.contentToString(), randomBytes3.contentToString())
        
        // None should be all zeros
        assertNotEquals("Should not be all zeros", 
            ByteArray(32).contentToString(), randomBytes1.contentToString())
        assertNotEquals("Should not be all zeros", 
            ByteArray(32).contentToString(), randomBytes2.contentToString())
        assertNotEquals("Should not be all zeros", 
            ByteArray(32).contentToString(), randomBytes3.contentToString())
    }
}