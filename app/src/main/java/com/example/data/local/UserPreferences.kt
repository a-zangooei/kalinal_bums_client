package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.logger.AppLogger
import com.example.data.model.AppThemeMode

class UserPreferences @VisibleForTesting internal constructor(
    context: Context,
    securePrefsFactory: SecurePrefsFactory
) {

    constructor(context: Context) : this(context, DefaultSecurePrefsFactory)

    fun interface SecurePrefsFactory {
        fun create(context: Context): SharedPreferences
    }

    private object DefaultSecurePrefsFactory : SecurePrefsFactory {
        override fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private val uiPrefs: SharedPreferences =
        context.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences?
    val isEncryptionAvailable: Boolean
    val encryptionFailureReason: String?

    init {
        var createdPrefs: SharedPreferences? = null
        var failureReason: String? = null
        try {
            createdPrefs = securePrefsFactory.create(context)
        } catch (e: Exception) {
            failureReason = e.message ?: e.javaClass.simpleName
            AppLogger.logWarn(
                component = "UserPreferences",
                operation = "EncryptedPrefsUnavailable",
                message = "EncryptedSharedPreferences initialization failed (${e.javaClass.simpleName}); credential storage disabled",
                throwable = e
            )
        }

        securePrefs = createdPrefs
        isEncryptionAvailable = createdPrefs != null
        encryptionFailureReason = failureReason

        migrateLegacyAndUiPreferences(context)
    }

    private fun migrateLegacyAndUiPreferences(context: Context) {
        try {
            // 1. Preserve non-sensitive UI preferences previously kept in encrypted prefs
            securePrefs?.let { enc ->
                if (!uiPrefs.contains(KEY_THEME_MODE) && enc.contains(KEY_THEME_MODE)) {
                    val existingTheme = enc.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
                    uiPrefs.edit().putString(KEY_THEME_MODE, existingTheme).apply()
                }
                if (!uiPrefs.contains(KEY_DEV_MODE) && enc.contains(KEY_DEV_MODE)) {
                    val existingDev = enc.getBoolean(KEY_DEV_MODE, false)
                    uiPrefs.edit().putBoolean(KEY_DEV_MODE, existingDev).apply()
                }
            }

            // 2. Migrate and purge legacy unencrypted preferences if present
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacyPrefs.all.isNotEmpty()) {
                val legacyTheme = legacyPrefs.getString(KEY_THEME_MODE, null)
                if (legacyTheme != null && !uiPrefs.contains(KEY_THEME_MODE)) {
                    uiPrefs.edit().putString(KEY_THEME_MODE, legacyTheme).apply()
                }
                if (legacyPrefs.contains(KEY_DEV_MODE) && !uiPrefs.contains(KEY_DEV_MODE)) {
                    uiPrefs.edit().putBoolean(KEY_DEV_MODE, legacyPrefs.getBoolean(KEY_DEV_MODE, false)).apply()
                }

                val legacyUser = legacyPrefs.getString(KEY_USERNAME, "") ?: ""
                val legacyPass = legacyPrefs.getString(KEY_PASSWORD, "") ?: ""
                val legacyRem = legacyPrefs.getBoolean(KEY_REMEMBER_ME, false)

                if (securePrefs != null && legacyRem && legacyUser.isNotEmpty() && !securePrefs.contains(KEY_USERNAME)) {
                    securePrefs.edit()
                        .putString(KEY_USERNAME, legacyUser)
                        .putString(KEY_PASSWORD, legacyPass)
                        .putBoolean(KEY_REMEMBER_ME, true)
                        .putInt(KEY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION)
                        .apply()
                    AppLogger.logInfo("UserPreferences", "LegacyMigration", "Migrated legacy saved credentials to MasterKey AES256_GCM store")
                }

                // Always purge legacy plaintext store so credentials cannot remain unencrypted
                legacyPrefs.edit().clear().apply()
            }

            // 3. Enforce invariant: if rememberMe is false, ensure no credentials linger in securePrefs
            securePrefs?.let { enc ->
                val rem = enc.getBoolean(KEY_REMEMBER_ME, false)
                if (!rem && (enc.contains(KEY_USERNAME) || enc.contains(KEY_PASSWORD))) {
                    enc.edit()
                        .remove(KEY_USERNAME)
                        .remove(KEY_PASSWORD)
                        .apply()
                    AppLogger.logInfo("UserPreferences", "CredentialInvariantPurge", "Purged residual credentials while rememberMe=false")
                }
                if (enc.getInt(KEY_MIGRATION_VERSION, 0) < CURRENT_MIGRATION_VERSION) {
                    enc.edit().putInt(KEY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION).apply()
                }
            }
        } catch (e: Exception) {
            AppLogger.logWarn("UserPreferences", "LegacyMigration", "Preferences migration check failed: ${e.message}", e)
        }
    }

    var savedUsername: String
        get() {
            val enc = securePrefs ?: return ""
            if (!enc.getBoolean(KEY_REMEMBER_ME, false)) return ""
            return enc.getString(KEY_USERNAME, "") ?: ""
        }
        set(value) {
            val enc = securePrefs ?: return
            if (!enc.getBoolean(KEY_REMEMBER_ME, false)) return
            enc.edit().putString(KEY_USERNAME, value).apply()
        }

    var savedPassword: String
        get() {
            val enc = securePrefs ?: return ""
            if (!enc.getBoolean(KEY_REMEMBER_ME, false)) return ""
            return enc.getString(KEY_PASSWORD, "") ?: ""
        }
        set(value) {
            val enc = securePrefs ?: return
            if (!enc.getBoolean(KEY_REMEMBER_ME, false)) return
            enc.edit().putString(KEY_PASSWORD, value).apply()
        }

    var rememberMe: Boolean
        get() = securePrefs?.getBoolean(KEY_REMEMBER_ME, false) ?: false
        set(value) {
            val enc = securePrefs
            if (enc == null) {
                AppLogger.logWarn(
                    component = "UserPreferences",
                    operation = "RememberMeBlocked",
                    message = "Ignored remember-me change because encrypted storage is unavailable"
                )
                return
            }
            if (!value) {
                clearCredentials()
            } else {
                enc.edit().putBoolean(KEY_REMEMBER_ME, true).apply()
                AppLogger.logDebug("UserPreferences", "RememberMeChanged", "Remember-me preference set to true")
            }
        }

    var isBiometricLockEnabled: Boolean
        get() = securePrefs?.getBoolean(KEY_BIOMETRIC_LOCK, false) ?: false
        set(value) {
            val enc = securePrefs ?: return
            enc.edit().putBoolean(KEY_BIOMETRIC_LOCK, value).apply()
            AppLogger.logInfo("UserPreferences", "BiometricLockChanged", "Biometric lock set to $value")
        }

    var themeMode: AppThemeMode
        get() {
            val raw = uiPrefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
            return try {
                AppThemeMode.valueOf(raw ?: AppThemeMode.SYSTEM.name)
            } catch (e: Exception) {
                AppLogger.logWarn("UserPreferences", "InvalidThemeMode", "Invalid stored theme mode '$raw'; defaulting to SYSTEM", e)
                AppThemeMode.SYSTEM
            }
        }
        set(value) = uiPrefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var isDeveloperModeUnlocked: Boolean
        get() = uiPrefs.getBoolean(KEY_DEV_MODE, false)
        set(value) = uiPrefs.edit().putBoolean(KEY_DEV_MODE, value).apply()

    fun hasSavedCredentials(): Boolean {
        val enc = securePrefs ?: return false
        val rem = enc.getBoolean(KEY_REMEMBER_ME, false)
        if (!rem) return false
        val user = enc.getString(KEY_USERNAME, "") ?: ""
        val pass = enc.getString(KEY_PASSWORD, "") ?: ""
        return user.isNotEmpty() && pass.isNotEmpty()
    }

    fun saveCredentials(username: String, password: String) {
        val enc = securePrefs ?: return
        if (!enc.getBoolean(KEY_REMEMBER_ME, false)) return
        enc.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clearCredentials() {
        securePrefs?.edit()
            ?.remove(KEY_USERNAME)
            ?.remove(KEY_PASSWORD)
            ?.putBoolean(KEY_REMEMBER_ME, false)
            ?.apply()
        AppLogger.logInfo("UserPreferences", "ClearCredentials", "Cleared saved credentials from local storage")
    }

    companion object {
        internal const val ENCRYPTED_PREFS_NAME = "cullinan_encrypted_prefs"
        internal const val UI_PREFS_NAME = "cullinan_ui_prefs"
        internal const val LEGACY_PREFS_NAME = "cullinan_user_prefs"

        internal const val KEY_USERNAME = "saved_username"
        internal const val KEY_PASSWORD = "saved_password"
        internal const val KEY_REMEMBER_ME = "remember_me"
        internal const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        internal const val KEY_THEME_MODE = "theme_mode"
        internal const val KEY_DEV_MODE = "dev_mode_unlocked"
        internal const val KEY_MIGRATION_VERSION = "crypto_migration_version"
        internal const val CURRENT_MIGRATION_VERSION = 2
    }
}
