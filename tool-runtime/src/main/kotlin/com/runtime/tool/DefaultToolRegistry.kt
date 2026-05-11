package com.runtime.tool

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Single entry boundary for tool lookup; handlers are registered explicitly in code (or by composition root).
 */
class DefaultToolRegistry : ToolRegistry {

    private val handlersByName = ConcurrentHashMap<String, ToolHandler>()

    override fun register(handler: ToolHandler): AppResult<Unit> {
        val name = handler.manifest.name.trim()
        if (name.isEmpty()) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "Tool manifest name must not be blank")
            )
        }
        val existing = handlersByName.putIfAbsent(name, handler)
        return if (existing != null) {
            AppResult.Failure(
                AppError(
                    ErrorCodes.INVALID_INPUT,
                    message = "Tool already registered: $name",
                    metadata = mapOf("toolName" to name)
                )
            )
        } else {
            AppResult.Success(Unit)
        }
    }

    override fun getTool(name: String): AppResult<ToolHandler> {
        val key = name.trim()
        if (key.isEmpty()) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "tool name must not be blank")
            )
        }
        val h = handlersByName[key]
            ?: return AppResult.Failure(
                AppError(
                    ErrorCodes.NOT_FOUND,
                    message = "Tool not registered",
                    metadata = mapOf("toolName" to key)
                )
            )
        return AppResult.Success(h)
    }

    override fun listTools(): List<ToolManifest> =
        handlersByName.values.map { it.manifest }.sortedBy { it.name }
}
