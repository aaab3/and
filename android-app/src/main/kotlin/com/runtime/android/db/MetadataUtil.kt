package com.runtime.android.db

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

internal fun encodeMetadata(metadata: Map<String, String>): String =
    if (metadata.isEmpty()) "{}" else json.encodeToString(mapSerializer, metadata)

internal fun decodeMetadata(raw: String): AppResult<Map<String, String>> =
    try {
        AppResult.Success(json.decodeFromString(mapSerializer, raw))
    } catch (e: Exception) {
        AppResult.Failure(
            AppError(
                ErrorCodes.PARSE_ERROR,
                message = "Invalid message metadata JSON",
                cause = e.message
            )
        )
    }
