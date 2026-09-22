# ProGuard / R8 configuration for AGRITECH HUB Production Release

# Keep data models used for Firestore / JSON serialization and Room
-keep class com.example.data.models.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Firebase & Play Services
-keep class com.google.firebase.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Security & Crypto
-keep class com.example.data.security.** { *; }
-keep class com.example.data.licensing.** { *; }

# Strip verbose debug logging from release build
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

