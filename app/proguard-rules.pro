# Add project specific ProGuard rules here.
# Keep stack trace line numbers for DeveloperLogsDialog crash diagnostics
-keepattributes SourceFile,LineNumberTable

# Preserve data models and structured log models
-keep class com.example.data.model.** { *; }
-keep class com.example.data.logger.** { *; }

# Keep Tink / EncryptedSharedPreferences cryptographic primitives used by UserPreferences
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Preserve Jsoup, OkHttp, and Coroutines metadata
-dontwarn org.jsoup.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

