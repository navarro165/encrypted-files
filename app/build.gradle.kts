import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("jacoco")
    id("org.owasp.dependencycheck") version "12.1.0"
}

// Professional signing configuration loader
fun loadSigningConfig(): SigningConfig? {
    return try {
        // First try keystore.properties (for CI/CD)
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            val keystoreProperties = Properties()
            keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            
            SigningConfig(
                storeFile = file(keystoreProperties["storeFile"] as String),
                storePassword = keystoreProperties["storePassword"] as String,
                keyAlias = keystoreProperties["keyAlias"] as String,
                keyPassword = keystoreProperties["keyPassword"] as String
            )
        } else {
            // Try gradle.properties (for local development)
            val storeFile = project.findProperty("RELEASE_STORE_FILE") as String?
            val storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
            val keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
            val keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
            
            if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
                SigningConfig(
                    storeFile = file(storeFile),
                    storePassword = storePassword,
                    keyAlias = keyAlias,
                    keyPassword = keyPassword
                )
            } else {
                null
            }
        }
    } catch (e: Exception) {
        println("Warning: Could not load signing configuration: ${e.message}")
        null
    }
}

data class SigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

// Load signing configuration
val signingConfig = loadSigningConfig()

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    // Professional signing configuration
    signingConfigs {
        // Release signing config (only create if keystore is available)
        if (signingConfig != null) {
            create("release") {
                storeFile = signingConfig.storeFile
                storePassword = signingConfig.storePassword
                keyAlias = signingConfig.keyAlias
                keyPassword = signingConfig.keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Configure instrumentation test runner
        testInstrumentationRunnerArguments.putAll(mapOf(
            "clearPackageData" to "true",
            "androidx.benchmark.suppressErrors" to "EMULATOR"
        ))
        
        // Build config fields for different environments
        buildConfigField("String", "BUILD_TYPE", "\"${project.findProperty("BUILD_TYPE") ?: "debug"}\"")
        buildConfigField("String", "BUILD_DATE", "\"${System.currentTimeMillis()}\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("debug")
            enableUnitTestCoverage = true
            
            // Debug-specific configurations
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Only sign if we have a valid signing config
            if (signingConfig != null) {
                signingConfig = signingConfigs.findByName("release")
            }
        }
        
        // Create a staging build type for testing
        create("staging") {
            initWith(getByName("debug"))
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    

    
    testOptions {
        managedDevices {
            allDevices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel2api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        animationsDisabled = true
    }
    
    lint {
        // Allow lint to fail the build only for errors, not warnings
        abortOnError = true
        // Disable specific lint checks that are not critical for security
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.glide)
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // LocalBroadcastManager for reliable in-app communication
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // Argon2 for secure PIN hashing (Android-compatible)
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

    // Unit testing
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:3.12.4")
    testImplementation("org.robolectric:robolectric:4.9")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test:runner:1.6.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.work:work-testing:2.9.0")

    // Instrumentation testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("org.mockito:mockito-android:3.12.4")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")

    // For testing fragments and activities
    debugImplementation("androidx.fragment:fragment-testing:1.8.8")
}