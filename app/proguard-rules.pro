# Keep model classes used by kotlinx.serialization (they are referenced reflectively)
-keep class com.quransanctuary.app.data.model.** { *; }
-keep class com.quransanctuary.app.ui.strings.** { *; }
# Type-safe Navigation resolves route serializers reflectively at startup.
-keep class com.quransanctuary.app.ui.navigation.** { *; }
# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
# Coil
-dontwarn coil3.**
-dontwarn io.coil.**
# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**
