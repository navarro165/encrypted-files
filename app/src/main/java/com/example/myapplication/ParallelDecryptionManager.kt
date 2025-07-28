package com.example.myapplication

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import javax.crypto.CipherInputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Manages parallel decryption operations for multiple files while maintaining security.
 * Uses thread pools for I/O operations but keeps cryptographic operations isolated.
 */
class ParallelDecryptionManager(private val context: android.content.Context) {
    
    private val executor = Executors.newFixedThreadPool(2) // Limited pool for security
    
    /**
     * Decrypts multiple files in parallel using separate threads for I/O operations.
     * Each file gets its own isolated cryptographic context.
     */
    fun decryptFilesInParallel(
        files: List<File>,
        onProgress: (Int, Int, String) -> Unit, // current, total, filename
        onComplete: (List<DecryptionResult>) -> Unit
    ) {
        val results = mutableListOf<DecryptionResult>()
        val futures = mutableListOf<Future<DecryptionResult>>()
        
        files.forEachIndexed { index, file ->
            val future = executor.submit<DecryptionResult> {
                try {
                    onProgress(index + 1, files.size, file.name)
                    val result = decryptSingleFile(file)
                    result
                } catch (e: Exception) {
                    Log.w("ParallelDecryption", "Failed to decrypt ${file.name}: ${e.message}")
                    DecryptionResult(file, null, e)
                }
            }
            futures.add(future)
        }
        
        // Collect results
        futures.forEach { future ->
            try {
                val result = future.get(30, TimeUnit.SECONDS) // Timeout for security
                results.add(result)
            } catch (e: Exception) {
                Log.e("ParallelDecryption", "Error getting decryption result: ${e.message}")
                results.add(DecryptionResult(File(""), null, e))
            }
        }
        
        onComplete(results)
    }
    
    /**
     * Decrypts a single file with optimized I/O operations.
     * Each file gets its own fresh cryptographic context for security.
     */
    private fun decryptSingleFile(file: File): DecryptionResult {
        val secureKeyManager = SecureKeyManager(context)
        
        return try {
            val fileSize = file.length()
            
            // Check file size limit
            if (fileSize > 10 * 1024 * 1024) {
                return DecryptionResult(file, null, Exception("File too large"))
            }
            
            val decryptedBytes = if (fileSize < 2048) {
                // Small file optimization
                FileInputStream(file).use { fis ->
                    val iv = ByteArray(12)
                    fis.read(iv)
                    
                    val cipher = secureKeyManager.getDecryptionCipher(iv)
                    val encryptedData = fis.readBytes()
                    
                    cipher.doFinal(encryptedData)
                }
            } else {
                // Large file with optimized I/O
                ByteArrayOutputStream().use { outputStream ->
                    FileInputStream(file).use { fis ->
                        val iv = ByteArray(12)
                        fis.read(iv)
                        
                        val cipher = secureKeyManager.getDecryptionCipher(iv)
                        
                        // Adaptive buffer size based on file size
                        val bufferSize = when {
                            fileSize > 10 * 1024 * 1024 -> 262144 // 256KB for very large files
                            fileSize > 5 * 1024 * 1024 -> 131072  // 128KB for large files
                            else -> 65536 // 64KB for normal files
                        }
                        
                        BufferedInputStream(fis, bufferSize).use { bufferedInput ->
                            CipherInputStream(bufferedInput, cipher).use { cipherInput ->
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
            
            DecryptionResult(file, decryptedBytes, null)
        } catch (e: Exception) {
            DecryptionResult(file, null, e)
        }
    }
    
    /**
     * Shuts down the thread pool safely.
     */
    fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
    
    /**
     * Result of a decryption operation.
     */
    data class DecryptionResult(
        val file: File,
        val decryptedData: ByteArray?,
        val error: Exception?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as DecryptionResult
            
            if (file != other.file) return false
            if (decryptedData != null) {
                if (other.decryptedData == null) return false
                if (!decryptedData.contentEquals(other.decryptedData)) return false
            } else if (other.decryptedData != null) return false
            if (error != other.error) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = file.hashCode()
            result = 31 * result + (decryptedData?.contentHashCode() ?: 0)
            result = 31 * result + (error?.hashCode() ?: 0)
            return result
        }
    }
    
    companion object {
        @Volatile
        private var instance: ParallelDecryptionManager? = null
        
        fun getInstance(context: android.content.Context): ParallelDecryptionManager {
            return instance ?: synchronized(this) {
                instance ?: ParallelDecryptionManager(context).also { instance = it }
            }
        }
    }
} 