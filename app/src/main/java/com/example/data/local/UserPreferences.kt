package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            "cullinan_encrypted_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback for Robolectric or test environments where KeyStore is not simulated
        context.getSharedPreferences("cullinan_user_prefs", Context.MODE_PRIVATE)
    }

    init {
        // Migrate and purge legacy unencrypted preferences if present
        try {
            val legacyPrefs = context.getSharedPreferences("cullinan_user_prefs", Context.MODE_PRIVATE)
            if (legacyPrefs.contains(KEY_USERNAME) || legacyPrefs.contains(KEY_PASSWORD)) {
                val legacyUser = legacyPrefs.getString(KEY_USERNAME, "") ?: ""
                val legacyPass = legacyPrefs.getString(KEY_PASSWORD, "") ?: ""
                val legacyRem = legacyPrefs.getBoolean(KEY_REMEMBER_ME, false)

                if (legacyRem && legacyUser.isNotEmpty() && !prefs.contains(KEY_USERNAME)) {
                    prefs.edit()
                        .putString(KEY_USERNAME, legacyUser)
                        .putString(KEY_PASSWORD, legacyPass)
                        .putBoolean(KEY_REMEMBER_ME, true)
                        .apply()
                }
                legacyPrefs.edit().clear().apply()
            }
        } catch (_: Exception) {
        }
    }

    var savedUsername: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var savedPassword: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var rememberMe: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_ME, false)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_ME, value).apply()

    var themeMode: com.example.data.model.AppThemeMode
        get() {
            val raw = prefs.getString(KEY_THEME_MODE, com.example.data.model.AppThemeMode.SYSTEM.name)
            return try {
                com.example.data.model.AppThemeMode.valueOf(raw ?: com.example.data.model.AppThemeMode.SYSTEM.name)
            } catch (_: Exception) {
                com.example.data.model.AppThemeMode.SYSTEM
            }
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var isDeveloperModeUnlocked: Boolean
        get() = prefs.getBoolean(KEY_DEV_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_MODE, value).apply()

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .putBoolean(KEY_REMEMBER_ME, false)
            .apply()
    }

    companion object {
        private const val KEY_USERNAME = "saved_username"
        private const val KEY_PASSWORD = "saved_password"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEV_MODE = "dev_mode_unlocked"
    }
}
