# Add project specific ProGuard rules here.
# By default, the flags in this file are difficult to break.

# Keep Room entities
-keep class com.clinicalflow.data.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}