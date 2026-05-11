package com.runtime.workflow.executor

internal object TemplateRenderer {
    private val placeholder = Regex("\\{\\{\\s*([^}]+?)\\s*\\}\\}")

    fun render(template: String, context: Map<String, String>): String =
        placeholder.replace(template) { m ->
            val key = m.groupValues[1].trim()
            context[key] ?: ""
        }
}
