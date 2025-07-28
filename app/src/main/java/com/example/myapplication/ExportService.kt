package com.example.myapplication

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Foreground service for handling background file exports.
 * Ensures export operations complete even when app goes to background.
 */
class ExportService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "export_channel"
        private const val CHANNEL_NAME = "File Export"
        
        const val EXTRA_EXPORT_URI = "export_uri"
        const val EXTRA_FILES = "files"
        const val EXTRA_FILE_COUNT = "file_count"
    }
    
    private val executor = Executors.newSingleThreadExecutor()
    private var isExporting = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val exportUri = intent?.getStringExtra(EXTRA_EXPORT_URI)
        val fileCount = intent?.getIntExtra(EXTRA_FILE_COUNT, 0) ?: 0
        
        if (exportUri != null && fileCount > 0) {
            startForeground(NOTIFICATION_ID, createNotification("Starting export...", 0, fileCount))
            startExport(exportUri, fileCount)
        }
        
        return START_NOT_STICKY
    }
    
    private fun startExport(exportUri: String, fileCount: Int) {
        if (isExporting) return
        
        isExporting = true
        
        executor.execute {
            try {
                // Get the files from the app's internal storage
                val files = getFilesFromInternalStorage()
                var exportedCount = 0
                
                files.take(fileCount).forEachIndexed { index, file ->
                    try {
                        // Update notification
                        updateNotification("Exporting ${file.name}...", index + 1, fileCount)
                        
                        // Decrypt and export file
                        val decryptedBytes = decryptFile(file)
                        if (decryptedBytes != null) {
                            var secureBuffer: SecureMemoryBuffer? = null
                            try {
                                secureBuffer = SecureMemoryBuffer.create(decryptedBytes.size)
                                secureBuffer.write(decryptedBytes)
                                
                                secureBuffer.withDecryptedData { data ->
                                    contentResolver.openOutputStream(android.net.Uri.parse(exportUri))?.use { outputStream ->
                                        outputStream.write(data)
                                    }
                                }
                                exportedCount++
                            } finally {
                                secureBuffer?.destroy()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ExportService", "Error exporting ${file.name}: ${e.message}")
                    }
                }
                
                // Export completed
                updateNotification("Export completed: $exportedCount file(s)", fileCount, fileCount)
                
                // Stop service after a delay to show completion
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    stopForeground(true)
                    stopSelf()
                }, 3000) // Show completion for 3 seconds
                
            } catch (e: Exception) {
                Log.e("ExportService", "Export failed: ${e.message}")
                updateNotification("Export failed", 0, fileCount)
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    stopForeground(true)
                    stopSelf()
                }, 3000)
            } finally {
                isExporting = false
            }
        }
    }
    
    private fun getFilesFromInternalStorage(): List<File> {
        // This would need to be implemented based on how files are stored
        // For now, return empty list - this needs to be coordinated with MainActivity
        return emptyList()
    }
    
    private fun decryptFile(file: File): ByteArray? {
        return try {
            val secureKeyManager = SecureKeyManager(this)
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
            Log.e("ExportService", "Failed to decrypt ${file.name}: ${e.message}")
            null
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for file exports"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(message: String, current: Int, total: Int): Notification {
        val progress = if (total > 0) (current * 100 / total) else 0
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Export")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    private fun updateNotification(message: String, current: Int, total: Int) {
        val notification = createNotification(message, current, total)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
} 