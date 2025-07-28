package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.myapplication.databinding.ActivityFileViewerBinding
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import javax.crypto.AEADBadTagException
import javax.crypto.CipherInputStream
import android.security.keystore.KeyPermanentlyInvalidatedException

class FileViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileViewerBinding
    private lateinit var secureKeyManager: SecureKeyManager
    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var securityManager: SecurityManager
    private lateinit var raspMonitor: RASPMonitor
    private var secureBuffer: SecureMemoryBuffer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Anti-screenshot protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        binding = ActivityFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize security components first
        securityManager = SecurityManager.getInstance(this)
        
        // Perform security check
        val securityResult = securityManager.performSecurityCheck()
        if (!securityResult.isSecure) {
            handleSecurityViolation(securityResult.violations)
            return
        }
        
        secureKeyManager = SecureKeyManager(this)
        authenticationManager = AuthenticationManager.getInstance(this)
        raspMonitor = RASPMonitor.getInstance(this)
        
        // Start RASP monitoring for file viewing
        raspMonitor.startMonitoring(object : RASPMonitor.ThreatListener {
            override fun onThreatsDetected(threats: List<RASPMonitor.Threat>) {
                // Silent monitoring during file viewing to avoid interruption
            }
            
            override fun onEmergencyTriggered(allThreats: List<RASPMonitor.Threat>) {
                runOnUiThread {
                    finish() // Immediately close file viewer
                }
            }
        })

        val filePath = intent.getStringExtra("file_path")
        if (filePath != null) {
            val file = File(filePath)
            when (authenticationManager.getAuthenticationStatus()) {
                AuthenticationManager.AuthStatus.SETUP_REQUIRED -> {
                    Toast.makeText(this, "PIN setup required. Please return to main screen.", Toast.LENGTH_LONG).show()
                    finish()
                }
                AuthenticationManager.AuthStatus.LOCKED -> {
                    Toast.makeText(this, "Authentication locked. Please try again later.", Toast.LENGTH_LONG).show()
                    finish()
                }
                AuthenticationManager.AuthStatus.AUTH_REQUIRED -> {
                    showTwoFactorAuthenticationForViewing(file)
                }
                AuthenticationManager.AuthStatus.AUTHENTICATED -> {
                    decryptAndDisplayFile(file)
                }
            }
        }
    }
    
    private fun isAuthenticationRequired(file: File): Boolean {
        return try {
            val iv = ByteArray(12)
            FileInputStream(file).use { it.read(iv) }
            secureKeyManager.getDecryptionCipher(iv)
            false // Cipher acquired, authentication is still valid
        } catch (e: Exception) {
            true // Authentication needed
        }
    }

    private fun showTwoFactorAuthenticationForViewing(file: File) {
        // First factor: Biometric authentication
        showBiometricPromptForDecryption(file)
    }
    
    private fun showBiometricPromptForDecryption(file: File) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Two-Factor Authentication")
            .setSubtitle("Step 1: Verify your biometric credential")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()

        val biometricPrompt = BiometricPrompt(this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // First factor succeeded, now show PIN entry
                    showPinEntryForViewing(file)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && 
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(applicationContext, "Biometric authentication error", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Biometric authentication failed", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }
    
    private fun showPinEntryForViewing(file: File) {
        val dialog = PinEntryDialog.newInstance(PinEntryDialog.MODE_VERIFY, "Enter Security PIN")
        dialog.setPinEntryListener(object : PinEntryDialog.PinEntryListener {
            override fun onPinEntered(pin: String, isSetup: Boolean) {
                if (!isSetup) {
                    // Verify PIN (second factor)
                    if (authenticationManager.verifySecondFactor(pin)) {
                        // Both factors succeeded!
                        decryptAndDisplayFile(file)
                    } else {
                        // PIN verification failed
                        val attemptsRemaining = AuthenticationManager.MAX_PIN_ATTEMPTS - authenticationManager.getPinFailedAttempts()
                        if (attemptsRemaining > 0) {
                            dialog.showVerificationError("$attemptsRemaining attempts remaining")
                        } else {
                            Toast.makeText(this@FileViewerActivity, "PIN locked. Closing file viewer.", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                            finish()
                        }
                    }
                }
            }
            
            override fun onPinCancelled() {
                Toast.makeText(this@FileViewerActivity, "Authentication cancelled", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        dialog.show(supportFragmentManager, "pin_verify")
    }

    private fun decryptAndDisplayFile(file: File) {
        // Check file size limit for viewing (10MB for secure buffer)
        val fileSize = file.length()
        if (fileSize > 10 * 1024 * 1024) {
            Toast.makeText(this, "File too large to display securely", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // For very small files (< 1KB), we can optimize further
        val isSmallFile = fileSize < 1024

        // Show progress indicator with better feedback
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
            binding.imageView.visibility = View.GONE
            binding.textView.visibility = View.GONE
            
            // Add a text indicator for better UX
            if (binding.textView.visibility == View.GONE) {
                binding.textView.visibility = View.VISIBLE
                binding.textView.text = "Decrypting file...\n\nFile: ${file.name}\nSize: ${formatFileSize(fileSize)}"
            }
        }

        // Move decryption to background thread to prevent ANR
        Thread {
            try {
                // Check if file exists and has content
                if (!file.exists()) {
                    throw Exception("File does not exist")
                }
                
                val fileSize = file.length()
                if (fileSize <= 12) { // Only IV or empty file
                    throw Exception("File is too small or corrupted (size: $fileSize bytes)")
                }
                
                android.util.Log.d("FileViewerActivity", "Starting decryption of ${file.name} (${fileSize} bytes)")
                            // Optimized streaming decryption with progress updates
            val decryptedBytes = if (isSmallFile) {
                // For small files, read everything at once for maximum speed
                FileInputStream(file).use { fis ->
                    val iv = ByteArray(12)
                    val ivBytesRead = fis.read(iv)
                    if (ivBytesRead != 12) {
                        throw Exception("Failed to read IV: expected 12 bytes, got $ivBytesRead")
                    }
                    
                    android.util.Log.d("FileViewerActivity", "Read IV: ${iv.size} bytes")
                    
                    val cipher = secureKeyManager.getDecryptionCipher(iv)
                    val encryptedData = fis.readBytes()
                    
                    android.util.Log.d("FileViewerActivity", "Read encrypted data: ${encryptedData.size} bytes")
                    
                    // Decrypt in one operation for small files
                    cipher.doFinal(encryptedData)
                }
            } else {
                // For larger files, use streaming with progress updates
                ByteArrayOutputStream().use { outputStream ->
                    FileInputStream(file).use { fis ->
                        val iv = ByteArray(12)
                        val ivBytesRead = fis.read(iv)
                        if (ivBytesRead != 12) {
                            throw Exception("Failed to read IV: expected 12 bytes, got $ivBytesRead")
                        }
                        
                        android.util.Log.d("FileViewerActivity", "Read IV: ${iv.size} bytes")
                        
                        val cipher = secureKeyManager.getDecryptionCipher(iv)
                        
                        // Use optimized buffer size for better performance (64KB - good balance)
                        // For larger files, we can use parallel processing for I/O operations
                        val bufferSize = if (fileSize > 5 * 1024 * 1024) {
                            131072 // 128KB for very large files
                        } else {
                            65536  // 64KB for normal files
                        }
                        
                        BufferedInputStream(fis, bufferSize).use { bufferedInput ->
                            CipherInputStream(bufferedInput, cipher).use { cipherInput ->
                                val buffer = ByteArray(bufferSize)
                                var bytesRead: Int
                                var totalBytesRead = 0L
                                
                                while (cipherInput.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                    
                                    // Update progress every 500KB for better performance
                                    if (totalBytesRead % (500 * 1024) == 0L) {
                                        val progress = ((totalBytesRead.toFloat() / fileSize) * 100).toInt()
                                        runOnUiThread {
                                            binding.textView.text = "Decrypting file... ${progress}%\n\nFile: ${file.name}\nSize: ${formatFileSize(fileSize)}\nProgress: ${formatFileSize(totalBytesRead)} / ${formatFileSize(fileSize)}"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    outputStream.toByteArray()
                }
            }

                // Move back to UI thread for display
                runOnUiThread {
                    try {
                        // Hide progress indicator
                        binding.progressBar.visibility = View.GONE

                        // Immediately move the decrypted data into a secure buffer
                        secureBuffer = SecureMemoryBuffer.create(decryptedBytes.size)
                        secureBuffer?.write(decryptedBytes)

                        // Securely wipe the temporary byte array
                        java.util.Arrays.fill(decryptedBytes, 0.toByte())

                        // Use the data from the secure buffer and wipe it afterwards
                        secureBuffer?.withDecryptedData { data ->
                            val fileName = file.name.lowercase()
                            if (fileName.endsWith(".txt") || fileName.endsWith(".log") || fileName.endsWith(".md")) {
                                // Text files
                                binding.imageView.visibility = View.GONE
                                binding.textView.visibility = View.VISIBLE
                                binding.textView.text = String(data)
                            } else if (isImageFile(fileName)) {
                                // Image files
                                binding.imageView.visibility = View.VISIBLE
                                binding.textView.visibility = View.GONE
                                
                                // Use BitmapFactory for better control over image loading
                                try {
                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                                    if (bitmap != null) {
                                        binding.imageView.setImageBitmap(bitmap)
                                    } else {
                                        throw Exception("Failed to decode image")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("FileViewerActivity", "Failed to load image: ${e.message}")
                                    binding.imageView.setImageResource(R.drawable.ic_launcher_foreground)
                                    Toast.makeText(this@FileViewerActivity, "Failed to display image", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Unknown file type - show as text if possible
                                binding.imageView.visibility = View.GONE
                                binding.textView.visibility = View.VISIBLE
                                binding.textView.text = "Binary file (${data.size} bytes)\n\nPreview not available for this file type."
                            }
                        }
                    } catch (e: Exception) {
                        binding.progressBar.visibility = View.GONE
                        android.util.Log.e("FileViewerActivity", "Error displaying file: ${e.javaClass.simpleName}", e)
                        Toast.makeText(this@FileViewerActivity, "Error displaying file: ${e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

            } catch (e: KeyPermanentlyInvalidatedException) {
                runOnUiThread {
                    // Check if this is a real key invalidation or just needs authentication
                    if (e.message?.contains("Key requires authentication") == true) {
                        // This is not a real invalidation, just needs biometric auth
                        Toast.makeText(this@FileViewerActivity, "Authentication required to view file", Toast.LENGTH_SHORT).show()
                        showTwoFactorAuthenticationForViewing(file)
                    } else {
                        // This is a real key invalidation (biometric credentials changed)
                        handleKeyInvalidated()
                    }
                }
            } catch (e: AEADBadTagException) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@FileViewerActivity, "Decryption failed: file may be corrupt or tampered with.", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    // Security: Don't log sensitive information
                    android.util.Log.e("FileViewerActivity", "Decryption error: ${e.javaClass.simpleName} - ${e.message}", e)
                    Toast.makeText(this@FileViewerActivity, "Error decrypting file: ${e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()
    }
    
    private fun handleKeyInvalidated() {
        AlertDialog.Builder(this)
            .setTitle("Security Alert")
            .setMessage("Your biometric credentials have changed. For your security, all encrypted files have been permanently deleted and are unrecoverable.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                // Nuke all files and keys
                secureKeyManager.deleteMasterKey()
                val rootDir = File(filesDir, "encrypted_files")
                rootDir.deleteRecursively()
                finishAffinity() // Close the app
            }
            .show()
    }

    /**
     * Handle security violations detected by the SecurityManager
     */
    private fun handleSecurityViolation(violations: List<SecurityManager.SecurityViolation>) {
        // Emergency data wipe if critical violations detected
        if (violations.contains(SecurityManager.SecurityViolation.ROOT_DETECTED) ||
            violations.contains(SecurityManager.SecurityViolation.RUNTIME_MANIPULATION)) {
            securityManager.emergencyDataWipe()
        }
        
        // Show security alert and exit
        AlertDialog.Builder(this)
            .setTitle("Security Alert")
            .setMessage("A security violation has been detected. The app will now exit for your protection.")
            .setCancelable(false)
            .setPositiveButton("Exit") { _, _ ->
                finishAffinity()
            }
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Securely destroy the memory buffer
        secureBuffer?.destroy()
        // Stop RASP monitoring if initialized
        if (::raspMonitor.isInitialized) {
            raspMonitor.stopMonitoring()
        }
    }

    /**
     * Check if the file is an image based on its extension
     */
    private fun isImageFile(fileName: String): Boolean {
        val imageExtensions = setOf(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff", ".tif"
        )
        return imageExtensions.any { fileName.endsWith(it) }
    }

    /**
     * Format file size in human-readable format
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
