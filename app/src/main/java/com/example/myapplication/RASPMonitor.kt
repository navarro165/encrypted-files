package com.example.myapplication

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Runtime Application Self-Protection (RASP) Monitor
 * Provides continuous monitoring for real-time threats and attacks
 */
class RASPMonitor private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: RASPMonitor? = null
        
        fun getInstance(context: Context): RASPMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RASPMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val MONITORING_INTERVAL_MS = 10000L // 10 seconds
        private const val THREAT_THRESHOLD = 3 // Number of threats before emergency action
    }
    
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val threats = mutableListOf<Threat>()
    private val securityManager by lazy { SecurityManager.getInstance(context) }
    private val secureMemoryManager by lazy { SecureMemoryManager.getInstance() }
    private var isMonitoring = false
    private var threatListener: ThreatListener? = null
    
    /**
     * Start continuous RASP monitoring
     */
    fun startMonitoring(listener: ThreatListener? = null) {
        if (isMonitoring) return
        
        // Check if this is a debug build
        try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val isDebuggable = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            
            if (isDebuggable) {
                android.util.Log.d("RASPMonitor", "Debug build detected - RASP monitoring disabled for development")
                // In debug builds, don't start intensive monitoring that interferes with development
                return
            }
        } catch (e: Exception) {
            android.util.Log.w("RASPMonitor", "Could not determine if app is debuggable", e)
        }
        
        this.threatListener = listener
        isMonitoring = true
        
        // Schedule continuous monitoring
        executor.scheduleAtFixedRate({
            performRuntimeCheck()
        }, 0, MONITORING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        
        // Schedule memory protection monitoring
        executor.scheduleAtFixedRate({
            monitorMemoryThreats()
        }, 5000, 15000, TimeUnit.MILLISECONDS) // Every 15 seconds, offset by 5s
    }
    
    /**
     * Stop RASP monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
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
     * Perform real-time security checks
     */
    private fun performRuntimeCheck() {
        if (!isMonitoring) return
        
        try {
            val detectedThreats = mutableListOf<Threat>()
            
            // Check for memory dumping attempts
            checkMemoryDumping()?.let { detectedThreats.add(it) }
            
            // Check for code injection
            checkCodeInjection()?.let { detectedThreats.add(it) }
            
            // Check for API hooking
            checkAPIHooking()?.let { detectedThreats.add(it) }
            
            // Check for emulator detection
            checkEmulatorDetection()?.let { detectedThreats.add(it) }
            
            // Check for debugging attempts
            checkDebuggerAttachment()?.let { detectedThreats.add(it) }
            
            // Check for process injection
            checkProcessInjection()?.let { detectedThreats.add(it) }

            // Check for non-standard installer
            checkAppInstaller()?.let { detectedThreats.add(it) }
            
            // Check for unusual file system activity
            checkFileSystemThreats()?.let { detectedThreats.add(it) }
            
            // Process detected threats
            if (detectedThreats.isNotEmpty()) {
                handleThreats(detectedThreats)
            }
            
        } catch (e: Exception) {
            // RASP monitoring should never crash the app
            // Security: Don't log sensitive monitoring information
        }
    }
    
    /**
     * Check for memory dumping attempts
     */
    private fun checkMemoryDumping(): Threat? {
        return try {
            // Check for memory dumping tools
            val memoryDumpTools = arrayOf(
                "gdb", "lldb", "dump", "memdump", "volatility", "rekall"
            )
            
            val process = Runtime.getRuntime().exec("ps")
            val scanner = Scanner(process.inputStream)
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine().lowercase()
                for (tool in memoryDumpTools) {
                    if (line.contains(tool)) {
                        scanner.close()
                        return Threat(ThreatType.MEMORY_DUMP, "Memory dumping tool detected: $tool", ThreatLevel.CRITICAL)
                    }
                }
            }
            scanner.close()
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check for code injection attempts
     */
    private fun checkCodeInjection(): Threat? {
        return try {
            // Check for suspicious memory mappings
            val maps = File("/proc/self/maps")
            if (maps.exists()) {
                val content = maps.readText()
                
                // Look for suspicious executable mappings
                val lines = content.split("\n")
                for (line in lines) {
                    if (line.contains("rwx") && // Read-write-execute (dangerous)
                        (line.contains("tmp") || line.contains("cache") || line.contains("data"))) {
                        return Threat(ThreatType.CODE_INJECTION, "Suspicious RWX mapping detected", ThreatLevel.HIGH)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check for API hooking
     */
    private fun checkAPIHooking(): Threat? {
        return try {
            // Check for common hooking libraries
            val maps = File("/proc/self/maps")
            if (maps.exists()) {
                val content = maps.readText()
                val hookingLibs = arrayOf(
                    "substrate", "xposed", "frida", "cydia", "hook", "inline"
                )
                
                for (lib in hookingLibs) {
                    if (content.contains(lib, ignoreCase = true)) {
                        return Threat(ThreatType.API_HOOKING, "Hooking library detected: $lib", ThreatLevel.HIGH)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check for emulator detection
     */
    private fun checkEmulatorDetection(): Threat? {
        return try {
            val emulatorIndicators = mapOf(
                "ro.hardware" to arrayOf("goldfish", "ranchu", "vbox"),
                "ro.product.model" to arrayOf("sdk", "emulator", "android"),
                "ro.build.fingerprint" to arrayOf("generic", "unknown", "test"),
                "ro.kernel.qemu" to arrayOf("1")
            )
            
            for ((property, indicators) in emulatorIndicators) {
                val process = Runtime.getRuntime().exec("getprop $property")
                val scanner = Scanner(process.inputStream)
                val value = if (scanner.hasNext()) scanner.nextLine().lowercase() else ""
                scanner.close()
                
                for (indicator in indicators) {
                    if (value.contains(indicator)) {
                        return Threat(ThreatType.EMULATOR, "Emulator detected: $property=$value", ThreatLevel.MEDIUM)
                    }
                }
            }
            
            // Advanced checks
            if (isPiped() || !hasTelephony() || !hasKnownSensors()) {
                 return Threat(ThreatType.EMULATOR, "Advanced emulator indicators found", ThreatLevel.HIGH)
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if the process is running through a pipe (common in emulators)
     */
    private fun isPiped(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getprop ro.kernel.qemu.gles")
            val scanner = Scanner(process.inputStream)
            val result = if (scanner.hasNext()) scanner.nextLine() else ""
            scanner.close()
            result == "1"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if the device has telephony capabilities
     */
    private fun hasTelephony(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }
    
    /**
     * Check for presence of common hardware sensors
     */
    private fun hasKnownSensors(): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
        // Emulators often have a very limited set of virtual sensors
        return sensors.size > 5
    }
    
    /**
     * Check for debugger attachment
     */
    private fun checkDebuggerAttachment(): Threat? {
        return if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            Threat(ThreatType.DEBUGGER, "Debugger attachment detected", ThreatLevel.HIGH)
        } else {
            null
        }
    }
    
    /**
     * Check for process injection
     */
    private fun checkProcessInjection(): Threat? {
        return try {
            // Check for unusual ptrace activity
            val status = File("/proc/self/status")
            if (status.exists()) {
                val content = status.readText()
                if (content.contains("TracerPid:") && !content.contains("TracerPid:\t0")) {
                    return Threat(ThreatType.PROCESS_INJECTION, "Process tracing detected", ThreatLevel.HIGH)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check for non-standard app installer (sideloading)
     */
    private fun checkAppInstaller(): Threat? {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            val knownStores = setOf("com.android.vending", "com.google.android.feedback")
            
            if (installer == null || !knownStores.contains(installer)) {
                return Threat(ThreatType.SIDELOADING, "App installed from untrusted source: $installer", ThreatLevel.MEDIUM)
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check for file system threats
     */
    private fun checkFileSystemThreats(): Threat? {
        return try {
            val appDir = File(context.applicationInfo.dataDir)
            val files = appDir.listFiles() ?: return null
            
            // Check for suspicious files
            for (file in files) {
                if (file.name.contains("frida") || 
                    file.name.contains("xposed") ||
                    file.name.contains("substrate") ||
                    file.name.endsWith(".so.bak") ||
                    file.name.endsWith(".dex.tmp")) {
                    return Threat(ThreatType.FILE_SYSTEM, "Suspicious file detected: ${file.name}", ThreatLevel.MEDIUM)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Monitor memory-specific threats
     */
    private fun monitorMemoryThreats() {
        if (!isMonitoring) return
        
        try {
            // Check memory pressure
            val memStats = secureMemoryManager.getMemoryStats()
            if (memStats.totalMemoryUsed > 40 * 1024 * 1024) { // 40MB threshold
                val threat = Threat(ThreatType.MEMORY_PRESSURE, "High secure memory usage", ThreatLevel.LOW)
                handleThreats(listOf(threat))
            }
            
            // Check for memory scanning attempts
            checkMemoryScanning()?.let { threat ->
                handleThreats(listOf(threat))
            }
            
        } catch (e: Exception) {
            // Silent failure for memory monitoring
        }
    }
    
    /**
     * Check for memory scanning attempts
     */
    private fun checkMemoryScanning(): Threat? {
        return try {
            // Check for memory scanning patterns in /proc/self/maps access
            val maps = File("/proc/self/maps")
            val lastModified = maps.lastModified()
            
            // If maps file was accessed very recently and frequently, it might indicate scanning
            if (System.currentTimeMillis() - lastModified < 1000) {
                return Threat(ThreatType.MEMORY_SCAN, "Potential memory scanning detected", ThreatLevel.MEDIUM)
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Handle detected threats
     */
    private fun handleThreats(detectedThreats: List<Threat>) {
        synchronized(threats) {
            threats.addAll(detectedThreats)
            
            // Notify listener
            threatListener?.onThreatsDetected(detectedThreats)
            
            // Check for emergency threshold
            val criticalThreats = threats.count { it.level == ThreatLevel.CRITICAL }
            val highThreats = threats.count { it.level == ThreatLevel.HIGH }
            
            if (criticalThreats >= 1 || highThreats >= THREAT_THRESHOLD) {
                triggerEmergencyResponse()
            }
        }
    }
    
    /**
     * Trigger emergency response for severe threats
     */
    private fun triggerEmergencyResponse() {
        try {
            // Emergency data wipe
            securityManager.emergencyDataWipe()
            secureMemoryManager.emergencyDestroy()
            
            // Notify on main thread
            Handler(Looper.getMainLooper()).post {
                threatListener?.onEmergencyTriggered(threats.toList())
            }
            
        } catch (e: Exception) {
            // Best effort emergency response
        }
    }
    
    /**
     * Get current threat status
     */
    fun getThreatStatus(): ThreatStatus {
        synchronized(threats) {
            val critical = threats.count { it.level == ThreatLevel.CRITICAL }
            val high = threats.count { it.level == ThreatLevel.HIGH }
            val medium = threats.count { it.level == ThreatLevel.MEDIUM }
            val low = threats.count { it.level == ThreatLevel.LOW }
            
            return ThreatStatus(critical, high, medium, low, threats.toList())
        }
    }
    
    /**
     * Clear threat history (for testing or after manual review)
     */
    fun clearThreats() {
        synchronized(threats) {
            threats.clear()
        }
    }
    
    data class Threat(
        val type: ThreatType,
        val description: String,
        val level: ThreatLevel,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    enum class ThreatType {
        MEMORY_DUMP,
        CODE_INJECTION,
        API_HOOKING,
        EMULATOR,
        DEBUGGER,
        PROCESS_INJECTION,
        SIDELOADING,
        FILE_SYSTEM,
        MEMORY_PRESSURE,
        MEMORY_SCAN
    }
    
    enum class ThreatLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    data class ThreatStatus(
        val criticalCount: Int,
        val highCount: Int,
        val mediumCount: Int,
        val lowCount: Int,
        val allThreats: List<Threat>
    )
    
    interface ThreatListener {
        fun onThreatsDetected(threats: List<Threat>)
        fun onEmergencyTriggered(allThreats: List<Threat>)
    }
} 