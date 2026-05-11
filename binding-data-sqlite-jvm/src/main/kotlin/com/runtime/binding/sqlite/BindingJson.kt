package com.runtime.binding.sqlite

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal val bindingJson = Json { ignoreUnknownKeys = true }

private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
private val stringListSerializer = ListSerializer(String.serializer())

internal fun decodeStringMap(json: String): AppResult<Map<String, String>> =
    try {
        AppResult.Success(bindingJson.decodeFromString(stringMapSerializer, json))
    } catch (e: Exception) {
        AppResult.Failure(
            AppError(
                code = ErrorCodes.PARSE_ERROR,
                message = "Invalid JSON map",
                cause = e.message
            )
        )
    }

internal fun decodeStringSet(json: String): AppResult<Set<String>> =
    try {
        AppResult.Success(bindingJson.decodeFromString(stringListSerializer, json).toSet())
    } catch (e: Exception) {
        AppResult.Failure(
            AppError(
                code = ErrorCodes.PARSE_ERROR,
                message = "Invalid JSON string list",
                cause = e.message
            )
        )
    }
