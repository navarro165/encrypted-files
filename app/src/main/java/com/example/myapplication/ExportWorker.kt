package com.example.myapplication

import android.content.Context
import android.util.Log
import androidx.work.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for handling background file exports.
 * Ensures export operations complete even when app goes to background.
 */
class ExportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val KEY_EXPORT_URI = "export_uri"
        private const val KEY_FILE_PATHS = "file_paths"
        private const val KEY_FILE_COUNT = "file_count"
        
        fun createWorkRequest(
            exportUri: String,
            filePaths: List<String>,
            fileCount: Int
        ): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_EXPORT_URI, exportUri)
                .putStringArray(KEY_FILE_PATHS, filePaths.toTypedArray())
                .putInt(KEY_FILE_COUNT, fileCount)
                .build()
            
            return OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(inputData)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .setRequiresCharging(false)
                        .setRequiresDeviceIdle(false)
                        .setRequiresStorageNotLow(false)
                        .build()
                )
                .build()
        }
    }
    
    override suspend fun doWork(): Result {
        val exportUri = inputData.getString(KEY_EXPORT_URI)
        val filePaths = inputData.getStringArray(KEY_FILE_PATHS)
        val fileCount = inputData.getInt(KEY_FILE_COUNT, 0)
        
        if (exportUri == null || filePaths == null || fileCount == 0) {
            Log.e("ExportWorker", "Invalid input data")
            return Result.failure()
        }
        
        return try {
            var exportedCount = 0
            
            filePaths.take(fileCount).forEachIndexed { index, filePath ->
                try {
                    val file = File(filePath)
                    if (file.exists()) {
                        // Update progress
                        setForeground(createForegroundInfo("Exporting ${file.name}...", index + 1, fileCount))
                        
                        // Decrypt and export file
                        val decryptedBytes = decryptFile(file)
                        if (decryptedBytes != null) {
                            var secureBuffer: SecureMemoryBuffer? = null
                            try {
                                secureBuffer = SecureMemoryBuffer.create(decryptedBytes.size)
                                secureBuffer.write(decryptedBytes)
                                
                                secureBuffer.withDecryptedData { data ->
                                    applicationContext.contentResolver.openOutputStream(android.net.Uri.parse(exportUri))?.use { outputStream ->
                                        outputStream.write(data)
                                    }
                                }
                                exportedCount++
                            } finally {
                                secureBuffer?.destroy()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ExportWorker", "Error exporting file: ${e.message}")
                }
            }
            
            // Final progress update
            setForeground(createForegroundInfo("Export completed: $exportedCount file(s)", fileCount, fileCount))
            
            Log.i("ExportWorker", "Export completed successfully: $exportedCount files")
            Result.success()
            
        } catch (e: Exception) {
            Log.e("ExportWorker", "Export failed: ${e.message}")
            setForeground(createForegroundInfo("Export failed", 0, fileCount))
            Result.failure()
        }
    }
    
    private fun decryptFile(file: File): ByteArray? {
        return try {
            val secureKeyManager = SecureKeyManager(applicationContext)
            val fileSize = file.length()
            
            if (fileSize < 2048) {
                // Small file optimization
                java.io.FileInputStream(file).use { fis ->
                    val iv = ByteArray(12)
                    fis.read(iv)
                    
                    val cipher = secureKeyManager.getDecryptionCipher(iv)
                    val encryptedData = fis.readBytes()
                    
                    cipher.doFinal(encryptedData)
                }
            } else {
                // Large file with optimized I/O
                java.io.ByteArrayOutputStream().use { outputStream ->
                    java.io.FileInputStream(file).use { fis ->
                        val iv = ByteArray(12)
                        fis.read(iv)
                        
                        val cipher = secureKeyManager.getDecryptionCipher(iv)
                        
                        val bufferSize = when {
                            fileSize > 10 * 1024 * 1024 -> 262144 // 256KB
                            fileSize > 5 * 1024 * 1024 -> 131072  // 128KB
                            else -> 65536 // 64KB
                        }
                        
                        java.io.BufferedInputStream(fis, bufferSize).use { bufferedInput ->
                            javax.crypto.CipherInputStream(bufferedInput, cipher).use { cipherInput ->
                                val buffer = ByteArray(bufferSize)
                                var bytesRead: Int
                                
                                while (cipherInput.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                    }
                    outputStream.toByteArray()
                }
            }
        } catch (e: Exception) {
            Log.e("ExportWorker", "Failed to decrypt ${file.name}: ${e.message}")
            null
        }
    }
    
    private fun createForegroundInfo(message: String, current: Int, total: Int): ForegroundInfo {
        val progress = if (total > 0) (current * 100 / total) else 0
        
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "export_channel")
            .setContentTitle("File Export")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        
        return ForegroundInfo(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
} 