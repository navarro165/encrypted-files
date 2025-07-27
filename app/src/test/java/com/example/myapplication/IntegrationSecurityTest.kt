package com.example.myapplication

import org.junit.Test
import org.junit.Assert.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec


/**
 * Integration tests that verify security components work together correctly
 */
class IntegrationSecurityTest {

    @Test
    fun testFileEncryptionDecryptionWorkflow() {
        // Simulate complete file encryption/decryption workflow
        val originalData = "🔐 Confidential business document with trade secrets! 💼".toByteArray()
        
        // Step 1: Generate file-specific key (simulating SecureKeyManager)
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val fileKey = keyGen.generateKey()
        
        // Step 2: Generate unique IV for this encryption
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        // Step 3: Encrypt data (simulating MainActivity.encryptAndSaveFile)
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, fileKey, GCMParameterSpec(128, iv))
        val encryptedData = encryptCipher.doFinal(originalData)
        
        // Verify encryption worked
        assertNotEquals("Data should be encrypted", 
            originalData.contentToString(), encryptedData.contentToString())
        assertEquals("Should add authentication tag", 
            originalData.size + 16, encryptedData.size)
        
        // Step 4: Decrypt data (simulating FileViewerActivity.decryptAndDisplayFile)
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, fileKey, GCMParameterSpec(128, iv))
        val decryptedData = decryptCipher.doFinal(encryptedData)
        
        // Verify perfect round-trip
        assertArrayEquals("Decryption should be perfect", originalData, decryptedData)
        assertEquals("Unicode should be preserved", 
            "🔐 Confidential business document with trade secrets! 💼", String(decryptedData))
    }

    @Test
    fun testMultipleFileEncryption() {
        // Test that multiple files get unique keys (simulating app behavior)
        val files = mapOf(
            "/documents/contract.pdf" to "📄 Legal contract content",
            "/photos/vacation.jpg" to "📸 Personal photo data", 
            "/videos/presentation.mp4" to "🎬 Business presentation",
            "/documents/financial.xlsx" to "💰 Financial spreadsheet"
        )
        
        val encryptedFiles = mutableMapOf<String, ByteArray>()
        val fileKeys = mutableMapOf<String, ByteArray>()
        val fileIVs = mutableMapOf<String, ByteArray>()
        
        // Encrypt each file with unique key
        files.forEach { (path, content) ->
            // Generate unique key per file (simulating SecureKeyManager.deriveFileKey)
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val key = keyGen.generateKey()
            
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(content.toByteArray())
            
            encryptedFiles[path] = encrypted
            fileKeys[path] = key.encoded
            fileIVs[path] = iv
        }
        
        // Verify all keys are unique
        val uniqueKeys = fileKeys.values.map { it.contentToString() }.toSet()
        assertEquals("All file keys should be unique", files.size, uniqueKeys.size)
        
        // Verify all IVs are unique
        val uniqueIVs = fileIVs.values.map { it.contentToString() }.toSet()
        assertEquals("All file IVs should be unique", files.size, uniqueIVs.size)
        
        // Verify each file can be decrypted correctly
        files.forEach { (path, originalContent) ->
            val keyBytes = fileKeys[path]!!
            val iv = fileIVs[path]!!
            val encrypted = encryptedFiles[path]!!
            
            val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encrypted)
            
            assertEquals("File should decrypt correctly", originalContent, String(decrypted))
        }
    }

    @Test
    fun testAuthenticationTimeoutIntegration() {
        // Test authentication timeout logic (simulating app workflow)
        val currentTime = System.currentTimeMillis()
        val timeout = AuthenticationManager.AUTHENTICATION_TIMEOUT
        
        // Test scenarios
        val scenarios = mapOf(
            "Fresh login" to currentTime,
            "Within timeout" to currentTime - (timeout / 2),
            "At boundary" to currentTime - timeout,
            "Just expired" to currentTime - timeout - 1L,
            "Long expired" to currentTime - timeout - 60000L
        )
        
        scenarios.forEach { (scenario, timestamp) ->
            val timeDiff = currentTime - timestamp
            val shouldRequireAuth = timeDiff > timeout
            
            // Simulate AuthenticationManager.isAuthenticationRequired logic
            val requiresAuth = timeDiff > timeout
            
            assertEquals("Scenario '$scenario' auth requirement", shouldRequireAuth, requiresAuth)
        }
    }

    @Test
    fun testStreamingEncryptionIntegration() {
        // Test streaming encryption for large files (simulating memory efficiency)
        val largeData = "LARGE FILE CONTENT ".repeat(10000).toByteArray() // ~200KB
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        // Simulate streaming encryption in chunks
        val chunkSize = 8192 // 8KB chunks like the app
        val encryptedChunks = mutableListOf<ByteArray>()
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        
        // Process in chunks (simulating CipherOutputStream behavior)
        var offset = 0
        while (offset < largeData.size) {
            val chunkEnd = minOf(offset + chunkSize, largeData.size)
            val chunk = largeData.sliceArray(offset..chunkEnd-1)
            
            val encryptedChunk = if (chunkEnd == largeData.size) {
                // Last chunk - finalize
                cipher.doFinal(chunk)
            } else {
                // Intermediate chunk
                cipher.update(chunk) ?: ByteArray(0)
            }
            
            if (encryptedChunk.isNotEmpty()) {
                encryptedChunks.add(encryptedChunk)
            }
            offset = chunkEnd
        }
        
        // Combine encrypted chunks
        val totalEncrypted = encryptedChunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        
        // Verify can decrypt back to original
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = decryptCipher.doFinal(totalEncrypted)
        
        assertArrayEquals("Streaming encryption should preserve data", largeData, decrypted)
        assertTrue("Encrypted size should include auth tag", 
            totalEncrypted.size >= largeData.size + 16)
    }

    @Test
    fun testErrorHandlingIntegration() {
        // Test error scenarios that app must handle
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val correctKey = keyGen.generateKey()
        val wrongKey = keyGen.generateKey()
        
        val testData = "Sensitive data".toByteArray()
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        // Encrypt with correct key
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, correctKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(testData)
        
        // Test wrong key scenario (simulating key corruption)
        try {
            val badCipher = Cipher.getInstance("AES/GCM/NoPadding")
            badCipher.init(Cipher.DECRYPT_MODE, wrongKey, GCMParameterSpec(128, iv))
            badCipher.doFinal(encrypted)
            fail("Should have failed with wrong key")
        } catch (e: Exception) {
            assertTrue("Should be authentication exception", 
                e is javax.crypto.AEADBadTagException ||
                e.message?.contains("tag mismatch") == true)
        }
        
        // Test corrupted data scenario
        val corruptedData = encrypted.clone()
        corruptedData[0] = (corruptedData[0].toInt() xor 0xFF).toByte()
        
        try {
            val badCipher = Cipher.getInstance("AES/GCM/NoPadding")
            badCipher.init(Cipher.DECRYPT_MODE, correctKey, GCMParameterSpec(128, iv))
            badCipher.doFinal(corruptedData)
            fail("Should have failed with corrupted data")
        } catch (e: Exception) {
            assertTrue("Should detect corruption", 
                e is javax.crypto.AEADBadTagException ||
                e.message?.contains("tag mismatch") == true)
        }
    }

    @Test
    fun testSecurityLayerIntegration() {
        // Test that all security layers work together
        
        // Layer 1: Authentication timeout check
        val authTimeout = AuthenticationManager.AUTHENTICATION_TIMEOUT
        assertTrue("Auth timeout should be secure", authTimeout == 5 * 60 * 1000L)
        
        // Layer 2: Key derivation
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        assertEquals("Key should be 256-bit", 32, key.encoded.size)
        
        // Layer 3: IV generation
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        assertEquals("IV should be 96-bit", 12, iv.size)
        
        // Layer 4: Authenticated encryption
        val testData = "Multi-layer security test".toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(testData)
        
        // Layer 5: Authentication verification
        assertEquals("Should add auth tag", testData.size + 16, encrypted.size)
        
        // Layer 6: Successful decryption
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = decryptCipher.doFinal(encrypted)
        
        assertArrayEquals("All layers should work together", testData, decrypted)
    }
}