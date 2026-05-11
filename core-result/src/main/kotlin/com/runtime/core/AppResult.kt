package com.runtime.core

/**
 * Structured success/failure carrier for cross-module boundaries.
 * Failures use [AppError]; callers do not rely on thrown exceptions as the primary contract.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val code: String,
    val message: String,
    val cause: String? = null,
    val retryable: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)
