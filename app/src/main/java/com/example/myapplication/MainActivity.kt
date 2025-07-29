package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityMainBinding
import java.io.File
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.activity.result.ActivityResultLauncher
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.security.SecureRandom
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.AEADBadTagException
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var rootDir: File
    private lateinit var currentDir: File
    private lateinit var fileAdapter: FileAdapter
    private lateinit var secureKeyManager: SecureKeyManager
    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var securityManager: SecurityManager
    private lateinit var raspMonitor: RASPMonitor
    private var actionMode: ActionMode? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var importCompletedReceiver: android.content.BroadcastReceiver
    private val pendingImports = mutableSetOf<String>() // Track pending imports by filename

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("MainActivity", "onCreate started")
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "super.onCreate completed")
        
        android.util.Log.d("MainActivity", "About to create notification channel")
        // Create notification channel for background exports
        createNotificationChannel()
        android.util.Log.d("MainActivity", "Notification channel created")
        
        android.util.Log.d("MainActivity", "About to initialize import receiver")
        // Initialize import completion receiver
        initializeImportReceiver()
        android.util.Log.d("MainActivity", "Import receiver initialized")
        
        // Anti-screenshot protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        android.util.Log.d("MainActivity", "About to initialize security manager")
        // Initialize security components first
        securityManager = SecurityManager.getInstance(this)
        android.util.Log.d("MainActivity", "Security manager initialized")
        
        android.util.Log.d("MainActivity", "About to perform security check")
        // Perform comprehensive security check
        val securityResult = securityManager.performSecurityCheck()
        android.util.Log.d("MainActivity", "Security check result: ${securityResult.isSecure}")
        if (!securityResult.isSecure) {
            android.util.Log.e("MainActivity", "Security violation detected, returning early")
            handleSecurityViolation(securityResult.violations)
            return
        }
        android.util.Log.d("MainActivity", "Security check passed, continuing")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        secureKeyManager = SecureKeyManager(this)
        authenticationManager = AuthenticationManager.getInstance(this)
        raspMonitor = RASPMonitor.getInstance(this)
        
        // Start RASP monitoring with threat listener
        raspMonitor.startMonitoring(object : RASPMonitor.ThreatListener {
            override fun onThreatsDetected(threats: List<RASPMonitor.Threat>) {
                // Security: Silent threat detection, logged internally by RASP
            }
            
            override fun onEmergencyTriggered(allThreats: List<RASPMonitor.Threat>) {
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Security Alert")
                        .setMessage("Critical security threats detected. Application will now exit.")
                        .setCancelable(false)
                        .setPositiveButton("Exit") { _, _ ->
                            finishAffinity()
                        }
                        .show()
                }
            }
        })
        
        rootDir = File(filesDir, "encrypted_files")
        if (!rootDir.exists()) {
            val created = rootDir.mkdir()
            if (!created) {
                android.util.Log.e("MainActivity", "Failed to create encrypted files directory")
                Toast.makeText(this, "Failed to initialize app storage", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        currentDir = rootDir

        fileAdapter = FileAdapter(
            mainActivity = this,
            onMultiSelect = { selectedFiles ->
                if (selectedFiles.isNotEmpty()) {
                    if (actionMode == null) {
                        actionMode = startActionMode(actionModeCallback)
                    }
                    actionMode?.title = "${selectedFiles.size} selected"
                } else {
                    actionMode?.finish()
                }
            },
            onOpenFile = { file ->
                val intent = Intent(this, FileViewerActivity::class.java)
                intent.putExtra("file_path", file.absolutePath)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to start FileViewerActivity", e)
                    Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenFolder = { folder ->
                currentDir = folder
                loadEncryptedFiles()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = fileAdapter
        
        // Initialize permission launcher for Android 13+
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                // Permissions granted, check authentication then proceed
                if (isAuthenticationRequired()) {
                    showBiometricPromptForEncryption()
                } else {
                    openFilePicker()
                }
            } else {
                Toast.makeText(this, "Media permissions required to select files", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up FAB click handler
        binding.fab.setOnClickListener {
            val options = arrayOf("Add Files", "Create Folder")
            AlertDialog.Builder(this)
                .setTitle("Select Action")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> checkAuthenticationAndOpenFilePicker()
                        1 -> showCreateFolderDialog()
                    }
                }
                .show()
        }

        // Check for first-time setup and biometric verification
        checkFirstTimeSetup()
        
        // Always check authentication status on app launch
        checkAuthenticationOnLaunch()
        
        loadEncryptedFiles()
        
        // Register import completion receiver
        android.util.Log.d("MainActivity", "About to register import receiver")
        registerImportReceiver()
        android.util.Log.d("MainActivity", "Import receiver registration completed")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop RASP monitoring if initialized
        try {
            if (::raspMonitor.isInitialized) {
                raspMonitor.stopMonitoring()
            }
        } catch (e: UninitializedPropertyAccessException) {
            // raspMonitor was not initialized, safe to ignore
        }
        
        // Shutdown parallel decryption manager
        ParallelDecryptionManager.getInstance(this).shutdown()
        
        // Unregister import receiver
        unregisterImportReceiver()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            
            // Export channel
            val exportChannel = android.app.NotificationChannel(
                "export_channel",
                "File Export",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for file exports"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(exportChannel)
            
            // Import channel
            val importChannel = android.app.NotificationChannel(
                "import_channel",
                "File Import",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for file imports"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(importChannel)
        }
    }
    
    private fun initializeImportReceiver() {
        importCompletedReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == "com.example.myapplication.IMPORT_COMPLETED_LOCAL") {
                    android.util.Log.d("MainActivity", "Import completion notification received, refreshing file list")
                    // Clear pending imports and refresh the file list when import completes
                    runOnUiThread {
                        android.util.Log.d("MainActivity", "Clearing pending imports, count before: ${pendingImports.size}")
                        pendingImports.clear()
                        android.util.Log.d("MainActivity", "Pending imports cleared, count after: ${pendingImports.size}")
                        refreshFileListWithPendingState()
                        fileAdapter.notifyDataSetChanged() // Force refresh
                        Toast.makeText(this@MainActivity, "File list refreshed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun registerImportReceiver() {
        // Register for local broadcasts only (more reliable for in-app communication)
        try {
            val localFilter = android.content.IntentFilter("com.example.myapplication.IMPORT_COMPLETED_LOCAL")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use modern broadcast registration for API 33+
                registerReceiver(importCompletedReceiver, localFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                // Use LocalBroadcastManager for older versions
                @Suppress("DEPRECATION")
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                    .registerReceiver(importCompletedReceiver, localFilter)
            }
            android.util.Log.d("MainActivity", "Import completion receiver registered")
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to register import completion receiver: ${e.message}")
        }
    }
    
    private fun unregisterImportReceiver() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use modern broadcast unregistration for API 33+
                unregisterReceiver(importCompletedReceiver)
            } else {
                // Use LocalBroadcastManager for older versions
                @Suppress("DEPRECATION")
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                    .unregisterReceiver(importCompletedReceiver)
            }
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    @Deprecated("Deprecated in API level 33")
    override fun onBackPressed() {
        if (currentDir != rootDir) {
            currentDir = currentDir.parentFile ?: rootDir
            loadEncryptedFiles()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                onBackPressedDispatcher.onBackPressed()
            } else {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    private fun isAuthenticationRequired(): Boolean {
        return try {
            secureKeyManager.getEncryptionCipher()
            false // Cipher acquired, authentication is still valid
        } catch (e: Exception) {
            true // Authentication needed
        }
    }
    
    private fun checkAuthenticationAndOpenFilePicker() {
        // Check permissions for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasMediaPermissions()) {
            requestMediaPermissions()
            return
        }
        
        // Check authentication status
        val authStatus = authenticationManager.getAuthenticationStatus()
        val isPinSet = authenticationManager.isPinSet()
        
        when (authStatus) {
            AuthenticationManager.AuthStatus.SETUP_REQUIRED -> {
                if (isPinSet) {
                    // PIN is set but status is SETUP_REQUIRED - this shouldn't happen
                    // Force authentication required and proceed
                    authenticationManager.forceAuthenticationRequired()
                    showTwoFactorAuthentication(true) // Open file picker after auth
                } else {
                    showPinSetupDialog()
                }
            }
            AuthenticationManager.AuthStatus.LOCKED -> {
                val remainingTime = maxOf(
                    authenticationManager.getRemainingLockoutTime(),
                    authenticationManager.getRemainingPinLockoutTime()
                ) / 60000
                Toast.makeText(this, "Authentication locked. Try again in $remainingTime minutes", Toast.LENGTH_LONG).show()
            }
            AuthenticationManager.AuthStatus.AUTH_REQUIRED -> {
                showTwoFactorAuthentication(true) // Open file picker after auth
            }
            AuthenticationManager.AuthStatus.AUTHENTICATED -> {
                openFilePicker()
            }
        }
    }
    
    private fun hasMediaPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permissions not needed for older versions
        }
    }
    
    private fun requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                    android.Manifest.permission.READ_MEDIA_AUDIO
                )
            )
        }
    }

    private fun showTwoFactorAuthentication(openFilePickerAfterAuth: Boolean = false) {
        // Show security overlay to hide content during authentication
        binding.securityOverlay.visibility = View.VISIBLE
        
        // First factor: Biometric authentication
        showBiometricPromptForEncryption(openFilePickerAfterAuth)
    }
    
    private fun showBiometricPromptForEncryption(openFilePickerAfterAuth: Boolean = false) {
        // Check if authentication is locked
        if (!authenticationManager.canAttemptAuthentication()) {
            val remainingTime = maxOf(
                authenticationManager.getRemainingLockoutTime(),
                authenticationManager.getRemainingPinLockoutTime()
            ) / 60000
            Toast.makeText(this, "Authentication locked. Try again in $remainingTime minutes", Toast.LENGTH_LONG).show()
            return
        }
        
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
                    // Security: Validate authentication result
                    if (validateAuthenticationResult(result)) {
                        // First factor succeeded, now show PIN entry (second factor)
                        showPinEntryDialog(openFilePickerAfterAuth)
                    } else {
                        Toast.makeText(this@MainActivity, "Biometric validation failed", Toast.LENGTH_SHORT).show()
                        authenticationManager.recordFailedAttempt()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && 
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        authenticationManager.recordFailedAttempt()
                        val attempts = authenticationManager.getFailedAttempts()
                        if (attempts < 5) {
                            Toast.makeText(applicationContext, "Biometric failed. ${5 - attempts} attempts remaining", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(applicationContext, "Too many failed attempts. Authentication locked for 30 minutes", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // User cancelled biometric authentication
                        binding.securityOverlay.visibility = View.GONE
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    authenticationManager.recordFailedAttempt()
                    val attempts = authenticationManager.getFailedAttempts()
                    if (attempts < 5) {
                        Toast.makeText(applicationContext, "Biometric failed. ${5 - attempts} attempts remaining", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, "Too many failed attempts. Authentication locked for 30 minutes", Toast.LENGTH_LONG).show()
                        // Hide security overlay when authentication is locked
                        binding.securityOverlay.visibility = View.GONE
                    }
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }
    
    private fun showPinSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Two-Factor Security Setup")
            .setMessage("For maximum security against biometric spoofing, you need to set up a 4-digit PIN that works alongside your biometric authentication.")
            .setPositiveButton("Set Up PIN") { _, _ ->
                val dialog = PinEntryDialog.newInstance(PinEntryDialog.MODE_SETUP, "Create Security PIN")
                dialog.setPinEntryListener(object : PinEntryDialog.PinEntryListener {
                    override fun onPinEntered(pin: String, isSetup: Boolean) {
                        if (isSetup) {
                            when (authenticationManager.setupPin(pin)) {
                                AuthenticationManager.PinSetupResult.SUCCESS -> {
                                    Toast.makeText(this@MainActivity, "PIN setup complete! You can now access encrypted files.", Toast.LENGTH_LONG).show()
                                    dialog.dismiss()
                                    
                                    // After PIN setup, proceed to normal authentication flow
                                    showTwoFactorAuthentication()
                                }
                                AuthenticationManager.PinSetupResult.WEAK_PIN -> {
                                    dialog.showVerificationError("This PIN is too common. Please choose a different one.")
                                }
                                else -> {
                                    dialog.showVerificationError("PIN setup failed. Please try again.")
                                }
                            }
                        }
                    }
                    
                    override fun onPinCancelled() {
                        Toast.makeText(this@MainActivity, "PIN setup is required for security", Toast.LENGTH_SHORT).show()
                    }
                })
                dialog.show(supportFragmentManager, "pin_setup")
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }
    
    private fun showPinEntryDialog(openFilePickerAfterAuth: Boolean = false) {
        val lockoutEndTime = if (authenticationManager.isPinLocked()) authenticationManager.getPinLockoutEndTime() else 0L
        val dialog = PinEntryDialog.newInstance(PinEntryDialog.MODE_VERIFY, "Enter Security PIN", lockoutEndTime)
        dialog.setPinEntryListener(object : PinEntryDialog.PinEntryListener {
            override fun onPinEntered(pin: String, isSetup: Boolean) {
                if (!isSetup) {
                    // Verify PIN (second factor)
                    if (authenticationManager.verifySecondFactor(pin)) {
                        // Both factors succeeded!
                        Toast.makeText(this@MainActivity, "Two-factor authentication successful", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        // Hide security overlay after successful authentication
                        binding.securityOverlay.visibility = View.GONE
                        
                        // Only open file picker if this authentication was for adding files
                        if (openFilePickerAfterAuth) {
                            openFilePicker()
                        }
                    } else {
                        // PIN verification failed
                        val attemptsRemaining = AuthenticationManager.MAX_PIN_ATTEMPTS - authenticationManager.getPinFailedAttempts()
                        val errorMessage = when {
                            attemptsRemaining <= 0 -> "PIN locked for 1 hour due to failed attempts"
                            else -> "Wrong PIN. $attemptsRemaining attempts remaining."
                        }
                        
                        if (attemptsRemaining <= 0) {
                             dialog.dismiss()
                             Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                        } else {
                            dialog.showVerificationError(errorMessage)
                        }
                    }
                }
            }
            
            override fun onPinCancelled() {
                Toast.makeText(this@MainActivity, "Two-factor authentication cancelled", Toast.LENGTH_SHORT).show()
                // Hide security overlay when authentication is cancelled
                binding.securityOverlay.visibility = View.GONE
            }
        })
        dialog.show(supportFragmentManager, "pin_verify")
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        resultLauncher.launch(intent)
    }

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                if (data.clipData != null) {
                    val count = data.clipData!!.itemCount
                    for (i in 0 until count) {
                        val uri = data.clipData!!.getItemAt(i).uri
                        encryptAndSaveFile(uri)
                    }
                } else if (data.data != null) {
                    val uri = data.data!!
                    encryptAndSaveFile(uri)
                }
            }
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderResultLauncher.launch(intent)
    }

    private val folderResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Toast.makeText(this, "Encrypting folder: $uri", Toast.LENGTH_SHORT).show()
                encryptFolder(uri)
            }
        }
    }

    private fun encryptFolder(uri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        documentFile?.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                encryptFolder(file.uri)
            } else if (file.isFile) {
                encryptAndSaveFile(file.uri)
            }
        }
    }

    internal fun encryptAndSaveFile(uri: Uri) {
        val originalFileName = getFileName(uri)
        if (originalFileName != "unknown_file") {
            startBackgroundImport(uri, originalFileName)
        } else {
            Toast.makeText(this, "Could not get filename", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startBackgroundImport(uri: Uri, filename: String) {
        // Add to pending imports and refresh UI immediately
        pendingImports.add(filename)
        refreshFileListWithPendingState()
        
        // Show background import notification
        Toast.makeText(this, "Import started in background", Toast.LENGTH_SHORT).show()
        
        // Start background import using WorkManager
        val workRequest = ImportWorker.createWorkRequest(
            sourceUri = uri.toString(),
            filename = filename
        )
        
        androidx.work.WorkManager.getInstance(applicationContext)
            .enqueue(workRequest)
    }

    internal fun loadEncryptedFiles() {
        val files = currentDir.listFiles()?.toList() ?: emptyList()
        android.util.Log.d("MainActivity", "loadEncryptedFiles: Found ${files.size} files in ${currentDir.absolutePath}")
        files.forEach { file ->
            android.util.Log.d("MainActivity", "loadEncryptedFiles: File: ${file.name} (${file.length()} bytes)")
        }
        fileAdapter.submitList(files)
    }
    
    private fun refreshFileListWithPendingState() {
        val actualFiles = currentDir.listFiles()?.toList() ?: emptyList()
        val allFiles = mutableListOf<File>()
        
        // Add actual files
        allFiles.addAll(actualFiles)
        
        // Add pending files that don't exist yet
        pendingImports.forEach { pendingFilename ->
            if (!actualFiles.any { it.name == pendingFilename }) {
                // Create a placeholder file for pending imports
                val pendingFile = File(currentDir, pendingFilename)
                allFiles.add(pendingFile)
            }
        }
        
        android.util.Log.d("MainActivity", "refreshFileListWithPendingState: ${actualFiles.size} actual files, ${pendingImports.size} pending files")
        fileAdapter.submitList(allFiles)
    }
    
    fun isFilePending(filename: String): Boolean {
        return pendingImports.contains(filename)
    }

    internal fun showDeleteConfirmationDialog(file: File) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Delete File")
        builder.setMessage("Are you sure you want to delete this file?")
        builder.setPositiveButton("Yes") { _, _ ->
            deleteFile(file)
        }
        builder.setNegativeButton("No", null)
        builder.show()
    }



    /**
     * Shows a small, unobtrusive reminder that the original file persists
     */
    private fun showOriginalFileReminder() {
        // Show a small toast with reminder about original file
        Toast.makeText(
            this, 
            "Note: Original file remains in its location. Delete manually if needed.", 
            Toast.LENGTH_LONG
        ).show()
    }

    internal fun deleteFile(file: File) {
        try {
            if (file.isDirectory) {
                // Handle directory deletion
                val deleted = file.deleteRecursively()
                if (deleted) {
                    loadEncryptedFiles()
                    Toast.makeText(this, "Folder deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to delete folder", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Use secure deletion for files
                val deleted = secureDeleteFile(file)
                if (deleted) {
                    loadEncryptedFiles()
                    Toast.makeText(this, "File securely deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Security: Don't log sensitive information
            Toast.makeText(this, "Error deleting ${if (file.isDirectory) "folder" else "file"}", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun decryptFile(file: File): ByteArray? {
        try {
            FileInputStream(file).use { fis ->
                // Read the IV from the beginning of the file
                val iv = ByteArray(12)
                fis.read(iv)

                val cipher = secureKeyManager.getDecryptionCipher(iv)

                // Use streaming decryption for all files
                BufferedInputStream(fis, 8192).use { bufferedInput ->
                    val outputStream = ByteArrayOutputStream()
                    CipherInputStream(bufferedInput, cipher).use { cipherInput ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (cipherInput.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    return outputStream.toByteArray()
                }
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Check if this is a real key invalidation or just needs authentication
            if (e.message?.contains("Key requires authentication") == true) {
                // This is not a real invalidation, just needs biometric auth
                Toast.makeText(this, "Authentication required to decrypt file", Toast.LENGTH_SHORT).show()
                showTwoFactorAuthentication()
            } else {
                // This is a real key invalidation (biometric credentials changed)
                handleKeyInvalidated()
            }
            return null
        } catch (e: AEADBadTagException) {
            Toast.makeText(this, "Decryption failed: file may be corrupt or tampered with.", Toast.LENGTH_LONG).show()
            return null
        } catch (e: Exception) {
            // Security: Don't log sensitive information
            android.util.Log.e("MainActivity", "Decryption error: ${e.javaClass.simpleName}", e)
            Toast.makeText(this, "Error decrypting file: ${e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
            return null
        }
    }
    
    private fun handleKeyInvalidated() {
        AlertDialog.Builder(this)
            .setTitle("Security Alert")
            .setMessage("Your biometric credentials have changed. For your security, all encrypted files have been permanently deleted and are unrecoverable.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                // Nuke all files and keys
                secureKeyManager.deleteMasterKey()
                rootDir.deleteRecursively()
                finishAffinity() // Close the app
            }
            .show()
    }
    
    /**
     * Securely delete a file by overwriting its contents multiple times before deletion
     */
    private fun secureDeleteFile(file: File): Boolean {
        return try {
            if (!file.exists() || !file.isFile) {
                return false
            }
            
            val fileSize = file.length()
            val random = SecureRandom()
            
            // Overwrite file with random data 3 times
            for (pass in 1..3) {
                RandomAccessFile(file, "rws").use { raf ->
                    raf.seek(0)
                    val buffer = ByteArray(8192)
                    var remaining = fileSize
                    
                    while (remaining > 0) {
                        val toWrite = minOf(buffer.size.toLong(), remaining).toInt()
                        random.nextBytes(buffer)
                        raf.write(buffer, 0, toWrite)
                        remaining -= toWrite
                    }
                    
                    raf.fd.sync() // Force write to disk
                }
            }
            
            // Final overwrite with zeros
            RandomAccessFile(file, "rws").use { raf ->
                raf.seek(0)
                val zeros = ByteArray(8192)
                var remaining = fileSize
                
                while (remaining > 0) {
                    val toWrite = minOf(zeros.size.toLong(), remaining).toInt()
                    raf.write(zeros, 0, toWrite)
                    remaining -= toWrite
                }
                
                raf.fd.sync()
            }
            
            // Finally delete the file
            file.delete()
        } catch (e: Exception) {
            // Security: Don't log sensitive information
            // Fall back to regular delete
            file.delete()
        }
    }

    internal fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    result = cursor.getString(columnIndex)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            // Use getLastPathSegment() instead of path manipulation
            // This prevents path traversal attacks by using Android's safe path extraction
            result = uri.lastPathSegment
            // If lastPathSegment is null, we cannot safely extract a filename
            // This is better than using unsafe path manipulation
        }
        return sanitizeFileName(result ?: "unknown_file")
    }

    /**
     * Sanitize filename to prevent path traversal and invalid characters
     */
    private fun sanitizeFileName(fileName: String): String {
        var sanitized = fileName.trim()
        
        // Remove path separators and dangerous sequences
        sanitized = sanitized.replace(Regex("[/\\\\]+"), "_")
        sanitized = sanitized.replace("..", "_")
        
        // Remove invalid characters for filesystem
        sanitized = sanitized.replace(Regex("[<>:\"|?*\\x00-\\x1f]"), "_")
        
        // Remove leading/trailing dots and spaces (Windows issues)
        sanitized = sanitized.trim('.', ' ')
        
        // Ensure it's not empty and not a reserved name
        if (sanitized.isEmpty() || sanitized.uppercase() in setOf(
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4",
            "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2",
            "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )) {
            sanitized = "file_${System.currentTimeMillis()}"
        }
        
        // Limit length
        if (sanitized.length > 255) {
            val extension = if (sanitized.contains('.')) {
                "." + sanitized.substringAfterLast('.')
            } else ""
            sanitized = sanitized.substring(0, 255 - extension.length) + extension
        }
        
        return sanitized
    }

    private val exportResultLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let { exportUri ->
            val selectedFiles = fileAdapter.getSelectedItems().toList()
            
            // Immediately return to main view - no delay
            actionMode?.finish()
            
            // Show background export notification
            Toast.makeText(this, "Export started in background", Toast.LENGTH_SHORT).show()
            
            // Start background export using WorkManager
            val filePaths = selectedFiles.map { it.absolutePath }
            val workRequest = ExportWorker.createWorkRequest(
                exportUri = exportUri.toString(),
                filePaths = filePaths,
                fileCount = selectedFiles.size
            )
            
            androidx.work.WorkManager.getInstance(applicationContext)
                .enqueue(workRequest)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_info -> {
                startActivity(Intent(this, InfoActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    internal fun showCreateFolderDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create New Folder")

        val input = EditText(this)
        builder.setView(input)

        builder.setPositiveButton("Create") { dialog, _ ->
            val folderName = input.text.toString().trim()
            if (folderName.isNotEmpty() && isValidFileName(folderName)) {
                val newFolder = File(currentDir, folderName)
                // Security: Ensure path stays within app directory
                if (isWithinAppDirectory(newFolder) && !newFolder.exists()) {
                    // Add to pending state immediately
                    pendingImports.add(folderName)
                    refreshFileListWithPendingState()
                    
                    // Create the folder
                    newFolder.mkdir()
                    
                    // Remove from pending and refresh
                    pendingImports.remove(folderName)
                    loadEncryptedFiles()
                    Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show()
                } else if (!isWithinAppDirectory(newFolder)) {
                    Toast.makeText(this, "Invalid folder location", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Folder already exists", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Invalid folder name", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    internal fun showRenameDialog(file: File) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Rename")

        val input = EditText(this)
        input.setText(file.name)
        builder.setView(input)

        builder.setPositiveButton("Rename") { dialog, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && isValidFileName(newName)) {
                val newFile = File(file.parent, newName)
                // Security: Ensure path stays within app directory
                if (isWithinAppDirectory(newFile) && !newFile.exists()) {
                    file.renameTo(newFile)
                    loadEncryptedFiles()
                    Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                } else if (!isWithinAppDirectory(newFile)) {
                    Toast.makeText(this, "Invalid file location", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Name already exists", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Invalid file name", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.contextual_action_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item?.itemId) {
                R.id.action_delete -> {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete Selected Files")
                        .setMessage("Are you sure you want to delete the selected files?")
                        .setPositiveButton("Yes") { _, _ ->
                            fileAdapter.getSelectedItems().forEach { file ->
                                deleteFile(file)
                            }
                            mode?.finish()
                        }
                        .setNegativeButton("No", null)
                        .show()
                    return true
                }
                R.id.action_export -> {
                    val selectedItems = fileAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        // Use the original filename for export
                        exportResultLauncher.launch(selectedItems[0].name)
                    } else {
                        // For multiple files, use a descriptive name
                        exportResultLauncher.launch("exported_files")
                    }
                    return true
                }
                R.id.action_rename -> {
                    val selectedItems = fileAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        showRenameDialog(selectedItems[0])
                    } else {
                        Toast.makeText(this@MainActivity, "Please select only one item to rename", Toast.LENGTH_SHORT).show()
                    }
                    mode?.finish()
                    return true
                }
                R.id.action_select_all -> {
                    fileAdapter.selectAll()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            fileAdapter.setMultiSelectMode(false)
            actionMode = null
        }
    }

    /**
     * Security validation functions to prevent path traversal attacks
     */
    private fun isValidFileName(name: String): Boolean {
        // Prevent path traversal and invalid characters
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return false
        }
        // Prevent reserved names and characters
        val invalidChars = setOf('<', '>', ':', '"', '|', '?', '*', '\u0000')
        if (name.any { it in invalidChars }) {
            return false
        }
        // Prevent reserved Windows names
        val reservedNames = setOf("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", 
                                 "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", 
                                 "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9")
        if (name.uppercase() in reservedNames) {
            return false
        }
        return name.length <= 255 // Reasonable file name length limit
    }

    private fun isWithinAppDirectory(file: File): Boolean {
        return try {
            val canonicalFile = file.canonicalPath
            val canonicalAppDir = filesDir.canonicalPath
            canonicalFile.startsWith(canonicalAppDir)
        } catch (e: Exception) {
            // If we can't resolve the path, deny access for security
            false
        }
    }

    private fun validateAuthenticationResult(result: BiometricPrompt.AuthenticationResult): Boolean {
        // Security: Additional validation of authentication result
        return try {
            // Verify authentication type is strong biometric
            result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC &&
            // Verify the result object is valid
            result.cryptoObject == null // We're not using crypto object, so it should be null
        } catch (e: Exception) {
            false
        }
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

    /**
     * Check authentication status on every app launch
     */
    private fun checkAuthenticationOnLaunch() {
        // Skip authentication check if this is first-time setup
        if (authenticationManager.isFirstTimeSetup()) {
            return
        }
        
        // Check authentication status
        val authStatus = authenticationManager.getAuthenticationStatus()
        
        when (authStatus) {
            AuthenticationManager.AuthStatus.SETUP_REQUIRED -> {
                // This shouldn't happen if PIN is already set, but handle it gracefully
                if (authenticationManager.isPinSet()) {
                    authenticationManager.forceAuthenticationRequired()
                    showTwoFactorAuthentication()
                } else {
                    showPinSetupDialog()
                }
            }
            AuthenticationManager.AuthStatus.LOCKED -> {
                val remainingTime = maxOf(
                    authenticationManager.getRemainingLockoutTime(),
                    authenticationManager.getRemainingPinLockoutTime()
                ) / 60000
                Toast.makeText(this, "Authentication locked. Try again in $remainingTime minutes", Toast.LENGTH_LONG).show()
            }
            AuthenticationManager.AuthStatus.AUTH_REQUIRED -> {
                // Authentication is required - show 2FA immediately
                showTwoFactorAuthentication()
            }
            AuthenticationManager.AuthStatus.AUTHENTICATED -> {
                // Force authentication on every app launch for security
                // Even if within timeout period, require fresh authentication
                authenticationManager.forceAuthenticationRequired()
                showTwoFactorAuthentication()
            }
        }
    }
    
    /**
     * Check if this is the first time the app is opened and handle setup
     */
    private fun checkFirstTimeSetup() {
        if (authenticationManager.isFirstTimeSetup()) {
            // Check if biometric authentication is available
            val biometricManager = BiometricManager.from(this)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            
            when (canAuthenticate) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    // Biometric is available, proceed with PIN setup
                    showPinSetupDialog()
                }
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                    showBiometricNotAvailableDialog("This device doesn't support biometric authentication.")
                }
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    showBiometricNotAvailableDialog("Biometric authentication is currently unavailable.")
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    showBiometricNotAvailableDialog("Please set up biometric authentication in your device settings.")
                }
                else -> {
                    showBiometricNotAvailableDialog("Biometric authentication is not available on this device.")
                }
            }
        }
    }
    
    /**
     * Show dialog when biometric authentication is not available
     */
    private fun showBiometricNotAvailableDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Biometric Authentication Required")
            .setMessage("$message\n\nThis app requires biometric authentication to ensure your files remain secure. Please set up biometric authentication in your device settings and restart the app.")
            .setCancelable(false)
            .setPositiveButton("Exit App") { _, _ ->
                finishAffinity()
            }
            .show()
    }
    
}


