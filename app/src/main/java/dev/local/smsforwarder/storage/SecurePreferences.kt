package dev.local.smsforwarder.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/** Creates the encrypted SharedPreferences file used for secrets and retry data. */
object SecurePreferences {
    private const val FILE_NAME = "sms_forwarder_secure_prefs"

    @Suppress("DEPRECATION")
    fun create(context: Context): SharedPreferences {
        val app = context.applicationContext
        migrateFromDeviceProtectedStorage(app)

        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            app,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun clearAll(context: Context) {
        create(context.applicationContext).edit().clear().apply()
    }

    private fun migrateFromDeviceProtectedStorage(app: Context) {
        val deviceContext = app.createDeviceProtectedStorageContext()
        val appPrefs = sharedPreferencesFile(app)
        val devicePrefs = sharedPreferencesFile(deviceContext)
        if (!appPrefs.exists() && devicePrefs.exists()) {
            app.moveSharedPreferencesFrom(deviceContext, FILE_NAME)
        }
    }

    private fun sharedPreferencesFile(context: Context): File =
        File(context.dataDir, "shared_prefs/$FILE_NAME.xml")
}
