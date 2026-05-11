package com.runtime.android.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.secret.SecretProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SecretProvider] backed by [EncryptedSharedPreferences] (AES256-GCM).
 */
class AndroidSecretProvider(
    context: Context
) : SecretProvider {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "runtime_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun prefKey(alias: String): String =
        "k_" + Base64.encodeToString(
            alias.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )

    override suspend fun putSecret(alias: String, value: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            prefs.edit().putString(prefKey(alias), value).apply()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to store secret", cause = e.message)
            )
        }
    }

    override suspend fun getSecret(alias: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            val v = prefs.getString(prefKey(alias), null)
                ?: return@withContext AppResult.Failure(
                    AppError(
                        ErrorCodes.NOT_FOUND,
                        message = "Secret not found",
                        metadata = mapOf("alias" to alias)
                    )
                )
            AppResult.Success(v)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to read secret", cause = e.message)
            )
        }
    }

    override suspend fun deleteSecret(alias: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val key = prefKey(alias)
            if (!prefs.contains(key)) {
                AppResult.Failure(
                    AppError(
                        ErrorCodes.NOT_FOUND,
                        message = "Secret not found for delete",
                        metadata = mapOf("alias" to alias)
                    )
                )
            } else {
                prefs.edit().remove(key).apply()
                AppResult.Success(Unit)
            }
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to delete secret", cause = e.message)
            )
        }
    }
}
