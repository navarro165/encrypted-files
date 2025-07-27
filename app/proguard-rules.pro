# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep biometric authentication classes
-keep class androidx.biometric.** { *; }

# Keep cryptography classes to prevent encryption issues  
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# Fix R8 missing classes for Tink cryptography
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-keep class javax.annotation.** { *; }
-keep class javax.annotation.concurrent.** { *; }

# Keep Google Tink classes
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.crypto.tink.proto.** { *; }

# Fix R8 missing classes for Google API client
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-keep class com.google.api.client.** { *; }
-keep class org.joda.time.** { *; }

# Exclude Tink's HTTP client functionality from release builds
# The following rule referenced a class removed from Tink 1.11+. Commented out to avoid
# "Unresolved reference" errors during R8/ProGuard shrinking.
#-assumenosideeffects class com.google.crypto.tink.util.KeysDownloader {
#    public static java.lang.String fetchAndCacheData();
#}

# Keep Glide classes for image loading
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}

# Keep DocumentFile classes for file operations
-keep class androidx.documentfile.provider.** { *; }

# Preserve all model classes and their fields
-keepclassmembers class com.example.myapplication.** {
    !private <fields>;
    !private <methods>;
}

# Remove logging in release builds for security
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Advanced obfuscation for security-critical classes
# SecurityManager should be fully obfuscated for maximum protection
-keepclassmembers class com.example.myapplication.SecurityManager {
    !private <methods>;
}

# Enhanced string obfuscation
-optimizations !code/simplification/string

# Control flow obfuscation
-overloadaggressively
-repackageclasses ''
-allowaccessmodification

# Hide security-related class names and method names
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt