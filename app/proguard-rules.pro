# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Preserve data models used in state and serialization
-keep class com.example.data.model.** { *; }

# Preserve Jsoup and OkHttp metadata
-dontwarn org.jsoup.**
-dontwarn okhttp3.**
-dontwarn okio.**
