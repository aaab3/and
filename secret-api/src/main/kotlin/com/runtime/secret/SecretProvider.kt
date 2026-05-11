package com.runtime.secret

import com.runtime.core.AppResult

interface SecretProvider {
    suspend fun putSecret(
        alias: String,
        value: String
    ): AppResult<Unit>

    suspend fun getSecret(
        alias: String
    ): AppResult<String>

    suspend fun deleteSecret(
        alias: String
    ): AppResult<Unit>
}
