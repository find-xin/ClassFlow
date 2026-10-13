package org.classflow.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure credential storage utilizing Android KeyStore and EncryptedSharedPreferences.
 * Prevents root AK/SK leakage into plaintext XML or logs.
 */
class KeyStoreManager(context: Context) {
    companion object {
        private const val PREFS_FILE = "classflow_secure_credentials"
        private const val KEY_QINIU_AK = "qiniu_ak"
        private const val KEY_QINIU_SK = "qiniu_sk"
        private const val KEY_SYNC_ENDPOINT = "sync_endpoint"
        private const val KEY_SYNC_BUCKET = "sync_bucket"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveQiniuCredentials(accessKey: String, secretKey: String) {
        sharedPreferences.edit()
            .putString(KEY_QINIU_AK, accessKey)
            .putString(KEY_QINIU_SK, secretKey)
            .apply()
    }

    fun getQiniuAccessKey(): String? = sharedPreferences.getString(KEY_QINIU_AK, null)

    fun getQiniuSecretKey(): String? = sharedPreferences.getString(KEY_QINIU_SK, null)

    fun saveSyncTarget(endpoint: String, bucket: String) {
        sharedPreferences.edit()
            .putString(KEY_SYNC_ENDPOINT, endpoint)
            .putString(KEY_SYNC_BUCKET, bucket)
            .apply()
    }

    fun getSyncEndpoint(): String? = sharedPreferences.getString(KEY_SYNC_ENDPOINT, null)

    fun getSyncBucket(): String? = sharedPreferences.getString(KEY_SYNC_BUCKET, null)

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }
}
