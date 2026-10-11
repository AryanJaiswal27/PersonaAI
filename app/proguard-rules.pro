# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep MediaPipe & ML Kit
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mlkit.** { *; }
