# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified in the
# Android SDK tools/proguard/proguard-android-optimize.txt

# Keep Room entity classes
-keep class com.example.videolibrarymanager.data.** { *; }

# Keep FFmpeg classes
-keep class com.arthenica.ffmpegkit.** { *; }

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
