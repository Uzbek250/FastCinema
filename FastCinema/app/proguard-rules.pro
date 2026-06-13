# FastCinema ProGuard qoidalari

# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Cast
-keep class com.google.android.gms.cast.** { *; }
-dontwarn com.google.android.gms.**

# Application classes
-keep class com.fastcinema.** { *; }

# JavaScript interfaces — muhim!
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
