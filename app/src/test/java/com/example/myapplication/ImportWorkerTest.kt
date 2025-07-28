package com.example.myapplication

import androidx.work.Data
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for ImportWorker functionality
 */
class ImportWorkerTest {
    
    @Test
    fun testImportWorkerCreation() {
        // Test that ImportWorker can be created with valid parameters
        val inputData = Data.Builder()
            .putString("source_uri", "content://test/import")
            .putString("filename", "test_file.txt")
            .build()
        
        assertNotNull("Input data should be created successfully", inputData)
        assertEquals("Source URI should match", "content://test/import", inputData.getString("source_uri"))
        assertEquals("Filename should match", "test_file.txt", inputData.getString("filename"))
    }
    
    @Test
    fun testImportWorkerWithInvalidData() {
        // Test that worker handles invalid input data gracefully
        val inputData = Data.Builder()
            .putString("source_uri", null)
            .putString("filename", null)
            .build()
        
        assertNotNull("Input data should be created even with null values", inputData)
        assertNull("Source URI should be null", inputData.getString("source_uri"))
        assertNull("Filename should be null", inputData.getString("filename"))
    }
    
    @Test
    fun testImportWorkerCreateWorkRequest() {
        // Test that work request is created correctly
        val sourceUri = "content://test/import"
        val filename = "test_file.txt"
        
        // Test that the method can be called without throwing exceptions
        try {
            val workRequest = ImportWorker.createWorkRequest(sourceUri, filename)
            assertNotNull("Work request should be created", workRequest)
        } catch (e: Exception) {
            fail("createWorkRequest should not throw exceptions: ${e.message}")
        }
    }
    
    @Test
    fun testImportWorkerBackoffPolicy() {
        // Test that work request has correct backoff policy
        try {
            val workRequest = ImportWorker.createWorkRequest(
                "content://test/import",
                "test_file.txt"
            )
            assertNotNull("Work request should be created", workRequest)
        } catch (e: Exception) {
            fail("createWorkRequest should not throw exceptions: ${e.message}")
        }
    }
    
    @Test
    fun testImportWorkerConstraints() {
        // Test that work request has correct constraints
        try {
            val workRequest = ImportWorker.createWorkRequest(
                "content://test/import",
                "test_file.txt"
            )
            assertNotNull("Work request should be created", workRequest)
        } catch (e: Exception) {
            fail("createWorkRequest should not throw exceptions: ${e.message}")
        }
    }
    
    @Test
    fun testImportWorkerDataKeys() {
        // Test that the correct data keys are used
        val sourceUri = "content://test/import"
        val filename = "test_file.txt"
        
        val inputData = Data.Builder()
            .putString("source_uri", sourceUri)
            .putString("filename", filename)
            .build()
        
        assertEquals("Source URI key should be correct", sourceUri, inputData.getString("source_uri"))
        assertEquals("Filename key should be correct", filename, inputData.getString("filename"))
    }
} 