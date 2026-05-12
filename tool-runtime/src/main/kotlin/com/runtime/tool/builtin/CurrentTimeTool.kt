package com.runtime.tool.builtin

import com.runtime.core.AppResult
import com.runtime.tool.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CurrentTimeTool : ToolHandler {

    override val manifest = ToolManifest(
        name = "current_time",
        description = "Get the current date and time in ISO format. Optionally specify a timezone.",
        inputSchemaJson = """{"type":"object","properties":{"timezone":{"type":"string","description":"IANA timezone (e.g. Asia/Shanghai). Defaults to UTC."}},"required":[]}""",
        outputSchemaJson = """{"type":"object","properties":{"time":{"type":"string"},"timezone":{"type":"string"}}}""",
        requiresUserConfirmation = false,
        timeoutMs = 5_000
    )

    override suspend fun execute(request: ToolExecutionRequest): AppResult<ToolExecutionResult> {
        val tz = try {
            val args = parseArgs(request.argumentsJson)
            val tzStr = args["timezone"]?.takeIf { it.isNotBlank() } ?: "UTC"
            ZoneId.of(tzStr)
        } catch (_: Exception) {
            ZoneId.of("UTC")
        }

        val now = Instant.now().atZone(tz)
        val formatted = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        return AppResult.Success(
            ToolExecutionResult(
                outputJson = """{"time":"$formatted","timezone":"${tz.id}"}"""
            )
        )
    }
}
