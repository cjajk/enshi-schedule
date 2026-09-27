# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Keep model classes
-keep class com.hbesxy.schedule.model.** { *; }

# Keep data classes used in JSON serialization
-keepclassmembers,allowobfuscation class * {
  @com.hbesxy.schedule.model.* <fields>;
}

# Keep RsaUtil
-keep class com.hbesxy.schedule.net.RsaUtil { *; }
