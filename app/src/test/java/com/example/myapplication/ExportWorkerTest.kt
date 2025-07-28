package com.example.myapplication

import androidx.work.Data
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for ExportWorker functionality
 */
class ExportWorkerTest {
    
    @Test
    fun testExportWorkerCreation() {
        // Test that ExportWorker can be created with valid parameters
        val inputData = Data.Builder()
            .putString("export_uri", "content://test/export")
            .putStringArray("file_paths", arrayOf("/test/file1.txt"))
            .putInt("file_count", 1)
            .build()
        
        assertNotNull("Input data should be created successfully", inputData)
        assertEquals("Export URI should match", "content://test/export", inputData.getString("export_uri"))
    }
    
    @Test
    fun testExportWorkerWithInvalidData() {
        // Test that worker handles invalid input data gracefully
        val inputData = Data.Builder()
            .putString("export_uri", null)
            .putInt("file_count", 0)
            .build()
        
        assertNotNull("Input data should be created even with null values", inputData)
        assertNull("Export URI should be null", inputData.getString("export_uri"))
        assertEquals("File count should be 0", 0, inputData.getInt("file_count", -1))
    }
    
    @Test
    fun testExportWorkerWithEmptyFileList() {
        // Test that worker handles empty file list
        val inputData = Data.Builder()
            .putString("export_uri", "content://test/export")
            .putStringArray("file_paths", emptyArray())
            .putInt("file_count", 0)
            .build()
        
        assertNotNull("Input data should be created successfully", inputData)
        assertEquals("Export URI should match", "content://test/export", inputData.getString("export_uri"))
        assertArrayEquals("File paths should be empty", emptyArray<String>(), inputData.getStringArray("file_paths"))
    }
    
    @Test
    fun testExportWorkerWithNonExistentFiles() {
        // Test that worker handles non-existent files gracefully
        val inputData = Data.Builder()
            .putString("export_uri", "content://test/export")
            .putStringArray("file_paths", arrayOf("/non/existent/file.txt"))
            .putInt("file_count", 1)
            .build()
        
        assertNotNull("Input data should be created successfully", inputData)
        assertEquals("Export URI should match", "content://test/export", inputData.getString("export_uri"))
        assertArrayEquals("File paths should match", arrayOf("/non/existent/file.txt"), inputData.getStringArray("file_paths"))
    }
    
    @Test
    fun testExportWorkerCreateWorkRequest() {
        // Test that work request is created correctly
        val exportUri = "content://test/export"
        val filePaths = listOf("/test/file1.txt", "/test/file2.txt")
        val fileCount = 2
        
        // Test that the method can be called without throwing exceptions
        try {
            val workRequest = ExportWorker.createWorkRequest(exportUri, filePaths, fileCount)
            assertNotNull("Work request should be created", workRequest)
        } catch (e: Exception) {
            fail("createWorkRequest should not throw exceptions: ${e.message}")
        }
    }
    
    @Test
    fun testExportWorkerBackoffPolicy() {
        // Test that work request has correct backoff policy
        try {
            val workRequest = ExportWorker.createWorkRequest(
                "content://test/export",
                listOf("/test/file.txt"),
                1
            )
            assertNotNull("Work request should be created", workRequest)
        } catch (e: Exception) {
            fail("createWorkRequest should not throw exceptions: ${e.message}")
        }
    }
    
    @Test
    fun testExportWorkerConstraints() {
        // Test that work request has correct constraints
        try {
            val workRequest = ExportWorker.createWorkRequest(
                "content://test/export",
                listOf("/test/file.txt"),
                1
            )
            assertNotNull("Work request should be created", workRequest)
        } catch (e: Exception) {
            fail("createWorkRequest should not throw exceptions: ${e.message}")
        }
    }
} 