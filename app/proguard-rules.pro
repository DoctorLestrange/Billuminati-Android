# Billuminati Android R8 / ProGuard Rules
# Ensures Moshi JSON backup serialization, Room persistence, and readable crash stack traces
# work reliably when isMinifyEnabled = true in production release builds.

# Preserve source file names and line numbers for clear crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Keep Billuminati domain models & enums serialized with Moshi (JSON backups & Room converters)
-keep class com.example.domain.model.** { *; }
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Moshi KSP-generated JsonAdapter classes and their constructors
-keepclassmembers class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}
-keep class com.example.domain.model.*JsonAdapter { *; }

# Keep Room database & entity classes
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.local.** { *; }
