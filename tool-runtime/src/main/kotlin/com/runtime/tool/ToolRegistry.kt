package com.runtime.tool

import com.runtime.core.AppResult

interface ToolRegistry {
    fun register(handler: ToolHandler): AppResult<Unit>
    fun getTool(name: String): AppResult<ToolHandler>
    fun listTools(): List<ToolManifest>
}
