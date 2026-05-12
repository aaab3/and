package com.runtime.tool.builtin

/**
 * Simple JSON argument parser. Extracts string values from a flat JSON object.
 * Not a full JSON parser — handles the common case of {"key":"value"} from LLM tool calls.
 */
internal fun parseArgs(json: String): Map<String, String> {
    if (json.isBlank() || json.trim() == "{}") return emptyMap()
    val result = mutableMapOf<String, String>()
    // Simple regex-based extraction for flat string values
    val pattern = Regex(""""(\w+)"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
    for (match in pattern.findAll(json)) {
        val key = match.groupValues[1]
        val value = match.groupValues[2]
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        result[key] = value
    }
    // Also try to extract non-string values (numbers, booleans)
    val numPattern = Regex(""""(\w+)"\s*:\s*([0-9.]+|true|false|null)""")
    for (match in numPattern.findAll(json)) {
        val key = match.groupValues[1]
        if (key !in result) {
            result[key] = match.groupValues[2]
        }
    }
    return result
}
