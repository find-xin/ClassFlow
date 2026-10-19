# ProGuard rules for ClassFlow Android

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep data models used in JSON serialization
-keep class org.classflow.data.local.** { *; }
-keep class org.classflow.sync.** { *; }

# Keep Jetpack Glance AppWidget components
-keep class org.classflow.widgets.** { *; }
-keep class androidx.glance.** { *; }

# Keep Kotlin reflection metadata
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
